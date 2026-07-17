package tj.metro.dushanbe.admin.service;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tj.metro.dushanbe.admin.security.AdminAuthProperties;
import tj.metro.dushanbe.identity.domain.AdminRole;
import tj.metro.dushanbe.identity.domain.AdminUser;
import tj.metro.dushanbe.identity.repository.AdminUserRepository;

/**
 * Создаёт первичного суперадмина на старте, если таблица пуста.
 *
 * <p>Почему не сид в миграции: BCrypt-хеш пришлось бы зафиксировать в SQL, то есть
 * положить в git общеизвестный пароль на все окружения. Здесь пароль приходит из
 * {@code app.admin.bootstrap.password} (env) и хешируется уже на месте.
 *
 * <p>Существующие учётные записи не трогаются: если суперадмин уже есть, метод
 * ничего не делает — иначе рестарт сбрасывал бы пароль, изменённый через консоль.
 */
@Component
public class AdminUserBootstrap {

    private static final Logger LOG = LoggerFactory.getLogger(AdminUserBootstrap.class);

    private final AdminUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final AdminAuthProperties properties;

    public AdminUserBootstrap(AdminUserRepository repository, PasswordEncoder passwordEncoder,
                              AdminAuthProperties properties) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void bootstrap() {
        AdminAuthProperties.Bootstrap config = properties.getBootstrap();
        String username = AdminUser.normalizeUsername(config.getUsername());
        String password = config.getPassword();

        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            LOG.warn("app.admin.bootstrap.username/password не заданы — первичный "
                    + "суперадмин не создан. Консоль будет недоступна до создания оператора.");
            return;
        }
        if (repository.countByRoleAndActiveIsTrue(AdminRole.SUPERADMIN) > 0) {
            LOG.info("Активный суперадмин уже существует — bootstrap пропущен.");
            return;
        }
        if (repository.existsByUsername(username)) {
            LOG.warn("Пользователь '{}' уже существует, но не является активным суперадмином — "
                    + "bootstrap пропущен, чтобы не перетереть учётную запись.", username);
            return;
        }

        repository.save(new AdminUser(UUID.randomUUID(), username, config.getDisplayName(),
                passwordEncoder.encode(password), AdminRole.SUPERADMIN, true));
        LOG.info("Создан первичный суперадмин '{}'. Смените пароль после первого входа.", username);
    }
}
