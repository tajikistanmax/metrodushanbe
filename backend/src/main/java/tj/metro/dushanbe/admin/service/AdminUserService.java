package tj.metro.dushanbe.admin.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tj.metro.dushanbe.admin.web.dto.AdminLoginRequest;
import tj.metro.dushanbe.admin.web.dto.AdminUserCreateRequest;
import tj.metro.dushanbe.admin.web.dto.AdminUserUpdateRequest;
import tj.metro.dushanbe.audit.service.AuditService;
import tj.metro.dushanbe.common.error.BadRequestException;
import tj.metro.dushanbe.common.error.ForbiddenException;
import tj.metro.dushanbe.common.error.NotFoundException;
import tj.metro.dushanbe.common.error.UnauthorizedException;
import tj.metro.dushanbe.identity.domain.AdminRole;
import tj.metro.dushanbe.identity.domain.AdminUser;
import tj.metro.dushanbe.identity.repository.AdminUserRepository;
import tj.metro.dushanbe.identity.web.dto.AdminUserDto;

/**
 * Управление операторами консоли и проверка их учётных данных (ADM-01).
 *
 * <p>Заменяет dev-схему с одним общим паролем: теперь у каждого audit-события
 * есть реальный актор. Все переходы состояния пишутся в аудит, включая неудачные
 * попытки входа.
 */
@Service
public class AdminUserService {

    private final AdminUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final AdminLoginGuard loginGuard;
    private final Clock clock;

    /**
     * Хеш несуществующего пароля для «холостой» проверки при неизвестном логине.
     * Без него ответ на неизвестный логин возвращался бы заметно быстрее, чем на
     * известный, и по времени ответа перебирались бы существующие учётные записи.
     * Генерируется один раз на старте из случайного значения.
     */
    private final String dummyHash;

    public AdminUserService(AdminUserRepository repository, PasswordEncoder passwordEncoder,
                            AuditService auditService, AdminLoginGuard loginGuard, Clock clock) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.loginGuard = loginGuard;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional(readOnly = true)
    public List<AdminUserDto> list() {
        return repository.findAllByOrderByRoleAscUsernameAsc().stream()
                .map(AdminUserService::toDto)
                .toList();
    }

    /** Возвращает актуальную активную учётную запись для проверки серверной сессии. */
    @Transactional(readOnly = true)
    public AdminUserDto current(String username) {
        AdminUser user = find(username);
        if (!user.isActive()) {
            throw new UnauthorizedException("auth.session_revoked", "Сессия оператора отозвана");
        }
        return toDto(user);
    }

    /** Backend-граница RBAC: UI не является доверенным местом проверки роли. */
    @Transactional(readOnly = true)
    public AdminUserDto requireRole(String username, AdminRole required) {
        AdminUserDto current = current(username);
        AdminRole actual = AdminRole.fromCode(current.role()).orElseThrow();
        if (!actual.includes(required)) {
            throw new ForbiddenException("admin.forbidden", "Недостаточно прав для операции");
        }
        return current;
    }

    /**
     * Проверяет логин и пароль. Любая причина отказа — один и тот же 401
     * {@code auth.invalid_credentials}; конкретика уходит только в аудит.
     *
     * <h2>Неразличимость учёток (аудит-пункт 9)</h2>
     * Ответ на «нет такого пользователя» и на «неверный пароль» одинаков и по коду,
     * и по времени: для несуществующего логина всё равно выполняется сверка с
     * фиктивным {@code dummyHash}, поэтому bcrypt тратит те же ~десятки миллисекунд,
     * что и на реальную учётную запись. Без этого по времени ответа перебирались бы
     * существующие логины. Блокировка {@code loginGuard} тоже ведётся по логину
     * независимо от его существования — иначе сам факт lockout выдавал бы учётку.
     *
     * <h2>Защита от подбора (аудит-пункт 9)</h2>
     * До сверки пароля проверяется {@code loginGuard.ensureNotLocked} (429 при
     * блокировке); каждая неудача увеличивает счётчики через {@code registerFailure},
     * успех — обнуляет их через {@code registerSuccess}.
     *
     * <p>Неудачные попытки пишутся через {@code recordIndependently}: обычный
     * {@code record} присоединился бы к этой транзакции и откатился вместе с ней
     * при выбросе 401, то есть журнал не сохранил бы ни одной неудачной попытки.
     *
     * @param clientIp адрес клиента для счётчика IP+логин (может быть null/blank)
     */
    @Transactional
    public AdminUserDto authenticate(AdminLoginRequest request, String clientIp) {
        String username = AdminUser.normalizeUsername(request.username());

        // Блокировка проверяется ДО сверки пароля — иначе подбор не тормозится.
        loginGuard.ensureNotLocked(username, clientIp);

        Optional<AdminUser> found = repository.findByUsername(username);

        // Холостая проверка выравнивает время ответа для неизвестного логина.
        if (found.isEmpty()) {
            passwordEncoder.matches(request.password(), dummyHash);
            registerFailure(username, clientIp, "unknown_user");
            throw invalidCredentials();
        }

        AdminUser user = found.get();
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            registerFailure(username, clientIp, "bad_password");
            throw invalidCredentials();
        }
        if (!user.isActive()) {
            registerFailure(username, clientIp, "inactive");
            throw invalidCredentials();
        }

        loginGuard.registerSuccess(username, clientIp);
        user.markLoggedIn(OffsetDateTime.now(clock));
        AdminUser saved = repository.save(user);
        auditService.record(username, "auth.login", "admin_user", username,
                null, Map.of("role", saved.getRole().code()));
        return toDto(saved);
    }

    /** Общая запись неудачи: сначала счётчик блокировки, затем событие аудита. */
    private void registerFailure(String username, String clientIp, String reason) {
        loginGuard.registerFailure(username, clientIp);
        auditService.recordIndependently(username, "auth.login_failed", "admin_user", username,
                null, Map.of("reason", reason));
    }

    @Transactional
    public AdminUserDto create(AdminUserCreateRequest request, String actor) {
        String username = AdminUser.normalizeUsername(request.username());
        AdminSupport.requireUnique(repository.existsByUsername(username),
                "admin_user.username_exists", "username", username);
        AdminUser user = new AdminUser(UUID.randomUUID(), username, request.displayName(),
                passwordEncoder.encode(request.password()), role(request.role()), request.active());
        AdminUser saved = repository.save(user);
        auditService.record(actor, "admin_user.create", "admin_user", username,
                null, snapshot(saved));
        return toDto(saved);
    }

    @Transactional
    public AdminUserDto update(String username, AdminUserUpdateRequest request, String actor) {
        AdminUser user = find(username);
        AdminRole newRole = role(request.role());
        Map<String, Object> before = snapshot(user);

        // Гейт: нельзя разжаловать или отключить последнего активного суперадмина —
        // иначе консоль остаётся без единственной учётки, способной чинить доступ.
        boolean losesSuperadmin = user.getRole() == AdminRole.SUPERADMIN
                && (newRole != AdminRole.SUPERADMIN || !request.active());
        if (losesSuperadmin) {
            requireAnotherSuperadmin(user);
        }

        user.update(request.displayName(), newRole, request.active());
        boolean passwordChanged = request.password() != null && !request.password().isBlank();
        if (passwordChanged) {
            user.changePasswordHash(passwordEncoder.encode(request.password()));
        }
        AdminUser saved = repository.save(user);

        Map<String, Object> after = snapshot(saved);
        after.put("passwordChanged", passwordChanged);
        auditService.record(actor, "admin_user.update", "admin_user", username, before, after);
        return toDto(saved);
    }

    @Transactional
    public void delete(String username, String actor) {
        AdminUser user = find(username);

        // Удалить себя нельзя: оператор мгновенно теряет доступ, а откатить
        // операцию будет уже некому.
        if (user.getUsername().equals(AdminUser.normalizeUsername(actor))) {
            throw new BadRequestException("admin_user.self_delete",
                    "Нельзя удалить собственную учётную запись",
                    Map.of("username", username));
        }
        if (user.getRole() == AdminRole.SUPERADMIN) {
            requireAnotherSuperadmin(user);
        }

        Map<String, Object> before = snapshot(user);
        repository.delete(user);
        auditService.record(actor, "admin_user.delete", "admin_user", username, before, null);
    }

    private void requireAnotherSuperadmin(AdminUser user) {
        long activeSuperadmins = repository.findActiveByRoleForUpdate(AdminRole.SUPERADMIN).size();
        boolean isLastOne = user.isActive() && activeSuperadmins <= 1;
        if (isLastOne) {
            throw new BadRequestException("admin_user.last_superadmin",
                    "Это последний активный суперадмин: сначала назначьте другого",
                    Map.of("username", user.getUsername()));
        }
    }

    private AdminUser find(String username) {
        return repository.findByUsername(AdminUser.normalizeUsername(username))
                .orElseThrow(() -> new NotFoundException("admin_user.not_found",
                        "Оператор не найден"));
    }

    private static AdminRole role(String code) {
        return AdminRole.fromCode(code).orElseThrow(() ->
                new BadRequestException("admin_user.role_invalid",
                        "Недопустимая роль: " + code,
                        Map.of("field", "role", "allowed", AdminRole.codes())));
    }

    private static UnauthorizedException invalidCredentials() {
        return new UnauthorizedException("auth.invalid_credentials",
                "Неверный логин или пароль");
    }

    private static Map<String, Object> snapshot(AdminUser user) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("username", user.getUsername());
        snapshot.put("displayName", user.getDisplayName());
        snapshot.put("role", user.getRole().code());
        snapshot.put("active", user.isActive());
        return snapshot;
    }

    /** Хеш пароля в DTO не переносится — он не покидает backend. */
    public static AdminUserDto toDto(AdminUser user) {
        return new AdminUserDto(user.getUsername(), user.getDisplayName(), user.getRole().code(),
                user.isActive(), user.getSessionVersion(), user.getLastLoginAt(), user.getUpdatedAt());
    }
}
