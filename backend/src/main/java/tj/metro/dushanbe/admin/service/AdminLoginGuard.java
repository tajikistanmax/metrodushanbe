package tj.metro.dushanbe.admin.service;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tj.metro.dushanbe.admin.security.AdminAuthProperties;
import tj.metro.dushanbe.audit.service.AuditService;
import tj.metro.dushanbe.common.error.TooManyRequestsException;
import tj.metro.dushanbe.identity.domain.LoginAttempt;
import tj.metro.dushanbe.identity.domain.LoginAttemptScope;
import tj.metro.dushanbe.identity.repository.LoginAttemptRepository;

/**
 * Защита входа от подбора пароля (аудит-пункт 9): два независимых счётчика неудач
 * и блокировка учётной записи, переживающая перезапуск процесса.
 *
 * <h2>Почему состояние в БД, а не в памяти</h2>
 * {@code ConcurrentHashMap} обнуляется рестартом: атакующему достаточно дождаться
 * деплоя. Общий {@code app.rate-limit} тоже in-memory, считает любые запросы по IP
 * и не знает о логине. Здесь состояние в {@code admin_login_attempt} — общее для
 * всех инстансов и устойчивое к перезапуску.
 *
 * <h2>Почему методы в собственных транзакциях ({@code REQUIRES_NEW})</h2>
 * {@code AdminUserService.authenticate} на неудаче бросает 401 и откатывает свою
 * транзакцию. Если бы инкремент счётчика шёл в ней же, он откатился бы вместе с 401
 * — и подбор не считался бы вовсе. Тот же приём, что у {@code AuditService.recordIndependently}.
 *
 * <h2>Неразличимость учёток</h2>
 * Счётчик ведётся по логину и по паре IP+логин ВНЕ зависимости от того, существует
 * ли учётная запись. Иначе сам факт блокировки выдавал бы существование логина.
 */
@Service
public class AdminLoginGuard {

    private final LoginAttemptRepository repository;
    private final AuditService auditService;
    private final AdminAuthProperties properties;
    private final Clock clock;

    public AdminLoginGuard(LoginAttemptRepository repository, AuditService auditService,
                           AdminAuthProperties properties, Clock clock) {
        this.repository = repository;
        this.auditService = auditService;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Отклоняет вход, если действует блокировка по учётной записи или по паре
     * IP+логин. Проверка выполняется ДО сверки пароля — иначе перебор не тормозится.
     *
     * @throws TooManyRequestsException если хотя бы один счётчик заблокирован
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void ensureNotLocked(String username, String ip) {
        if (!properties.getLockout().isEnabled()) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        long accountRetry = retryAfter(LoginAttemptScope.ACCOUNT, username, now);
        long ipUserRetry = retryAfter(LoginAttemptScope.IP_USER,
                LoginAttempt.ipUserSubject(ip, username), now);
        long retry = Math.max(accountRetry, ipUserRetry);
        if (retry > 0) {
            throw locked(retry);
        }
    }

    /**
     * Учитывает неудачную попытку в обоих счётчиках и, при достижении порога,
     * включает блокировку. Каждое включение блокировки пишется в аудит.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registerFailure(String username, String ip) {
        if (!properties.getLockout().isEnabled()) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        AdminAuthProperties.Lockout cfg = properties.getLockout();
        registerFailure(LoginAttemptScope.ACCOUNT, username, username, ip, now,
                cfg.getAccountMaxFailures(), cfg.getWindow(), cfg.getLockDuration());
        registerFailure(LoginAttemptScope.IP_USER, LoginAttempt.ipUserSubject(ip, username),
                username, ip, now, cfg.getIpUserMaxFailures(), cfg.getWindow(), cfg.getLockDuration());
    }

    /** Успешный вход обнуляет оба счётчика: серия подбора прервана. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registerSuccess(String username, String ip) {
        if (!properties.getLockout().isEnabled()) {
            return;
        }
        reset(LoginAttemptScope.ACCOUNT, username);
        reset(LoginAttemptScope.IP_USER, LoginAttempt.ipUserSubject(ip, username));
    }

    private long retryAfter(LoginAttemptScope scope, String subject, OffsetDateTime now) {
        return repository.findByScopeAndSubject(scope, subject)
                .map(attempt -> attempt.retryAfterSeconds(now))
                .orElse(0L);
    }

    private void registerFailure(LoginAttemptScope scope, String subject, String username, String ip,
                                 OffsetDateTime now, int threshold, Duration window,
                                 Duration lockDuration) {
        LoginAttempt attempt = getOrCreate(scope, subject);
        boolean nowLocked = attempt.registerFailure(now, threshold, window, lockDuration);
        repository.save(attempt);
        if (nowLocked) {
            // Событие блокировки — отдельная запись сверх auth.login_failed: смене
            // важно видеть не только поток неудач, но и момент, когда доступ закрылся.
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("scope", scope.code());
            details.put("ip", ip == null ? "unknown" : ip);
            details.put("lockedForSeconds", lockDuration.toSeconds());
            auditService.recordIndependently(username, "auth.account_locked", "admin_user",
                    username, null, details);
        }
    }

    /**
     * Возвращает строку счётчика под пессимистичной блокировкой, создавая её при
     * первом обращении. На гонке первичной вставки уникальный индекс отбрасывает
     * второго — тогда перечитываем уже существующую строку.
     */
    private LoginAttempt getOrCreate(LoginAttemptScope scope, String subject) {
        Optional<LoginAttempt> existing = repository.findForUpdate(scope, subject);
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            return repository.saveAndFlush(new LoginAttempt(UUID.randomUUID(), scope, subject));
        } catch (DataIntegrityViolationException race) {
            return repository.findForUpdate(scope, subject).orElseThrow(() -> race);
        }
    }

    private void reset(LoginAttemptScope scope, String subject) {
        repository.findForUpdate(scope, subject).ifPresent(attempt -> {
            attempt.reset();
            repository.save(attempt);
        });
    }

    private static TooManyRequestsException locked(long retryAfterSeconds) {
        return new TooManyRequestsException("auth.too_many_attempts",
                "Слишком много попыток входа. Повторите позже.", retryAfterSeconds);
    }
}
