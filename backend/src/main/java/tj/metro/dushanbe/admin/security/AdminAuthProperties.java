package tj.metro.dushanbe.admin.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Свойства dev-авторизации админского контура (префикс {@code app.admin}).
 *
 * <p><b>ВНИМАНИЕ — временная dev-заглушка, не для прода.</b> Секрет
 * {@code app.admin.dev-key} задаётся в application.yml открытым текстом и защищает
 * write-эндпоинты {@code /api/v1/admin/**} лишь символически. Продовая безопасность
 * (ТЗ §6.1.7, §9.2) требует OAuth 2.0 + JWT (Keycloak), MFA для admin/operator,
 * RBAC + ABAC и полноценного аудита субъекта — этот класс к продакшену не пригоден.
 */
@Component
@ConfigurationProperties(prefix = "app.admin")
public class AdminAuthProperties {

    /** Dev-ключ, ожидаемый в заголовке {@code X-Admin-Key}. */
    private String devKey;

    /** Первичный суперадмин, создаваемый на старте (см. AdminUserBootstrap). */
    private Bootstrap bootstrap = new Bootstrap();

    public String getDevKey() {
        return devKey;
    }

    public void setDevKey(String devKey) {
        this.devKey = devKey;
    }

    public Bootstrap getBootstrap() {
        return bootstrap;
    }

    public void setBootstrap(Bootstrap bootstrap) {
        this.bootstrap = bootstrap;
    }

    /**
     * Учётные данные первичного суперадмина ({@code app.admin.bootstrap.*}).
     *
     * <p>Задаются только через env. В репозитории и в миграциях пароля нет: сид
     * BCrypt-хеша в SQL означал бы общеизвестный пароль в истории git.
     */
    public static class Bootstrap {

        /** Логин первичного суперадмина. */
        private String username;

        /** Пароль первичного суперадмина в открытом виде; хешируется на старте. */
        private String password;

        /** Отображаемое имя в консоли. */
        private String displayName = "Суперадминистратор";

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }
    }
}
