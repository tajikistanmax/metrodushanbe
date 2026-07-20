package tj.metro.dushanbe.admin.security;

import java.time.Duration;
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

    /** Подписанный токен актора аудита (аудит-пункт 5). */
    private ActorToken actorToken = new ActorToken();

    /** Блокировка учётной записи при подборе пароля (аудит-пункт 9). */
    private Lockout lockout = new Lockout();

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

    public ActorToken getActorToken() {
        return actorToken;
    }

    public void setActorToken(ActorToken actorToken) {
        this.actorToken = actorToken;
    }

    public Lockout getLockout() {
        return lockout;
    }

    public void setLockout(Lockout lockout) {
        this.lockout = lockout;
    }

    /**
     * Токен актора ({@code app.admin.actor-token.*}), см. {@link AdminActorTokenService}.
     *
     * <p>{@code required=false} — обратная совместимость dev-контура и интеграционных
     * тестов, которые ходят с голым {@code X-Admin-Actor}. В prod-профиле свойство
     * поднято в {@code true}, и там голый заголовок больше не принимается.
     */
    public static class ActorToken {

        /** Общий секрет HMAC; пустой = токены не принимаются вовсе (fail-closed). */
        private String secret;

        /** Обязателен ли токен. В prod — true. */
        private boolean required;

        /** Срок жизни токена от момента выпуска. */
        private Duration ttl = Duration.ofMinutes(2);

        /** Допустимое расхождение часов консоли и backend. */
        private Duration clockSkew = Duration.ofSeconds(30);

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public boolean isRequired() {
            return required;
        }

        public void setRequired(boolean required) {
            this.required = required;
        }

        public Duration getTtl() {
            return ttl;
        }

        public void setTtl(Duration ttl) {
            this.ttl = ttl;
        }

        public Duration getClockSkew() {
            return clockSkew;
        }

        public void setClockSkew(Duration clockSkew) {
            this.clockSkew = clockSkew;
        }
    }

    /**
     * Порог и окно блокировки входа ({@code app.admin.lockout.*}).
     *
     * <p>Два независимых счётчика: по учётной записи (медленный подбор пароля с
     * многих адресов) и по паре IP+логин (быстрый перебор с одного адреса). Общий
     * {@code app.rate-limit} их не заменяет: он считает ЛЮБЫЕ запросы по IP и
     * ничего не знает ни о логине, ни о том, была ли попытка успешной.
     */
    public static class Lockout {

        private boolean enabled = true;

        /** Неудач по учётной записи до блокировки. */
        private int accountMaxFailures = 5;

        /** Неудач с одного IP по одному логину до блокировки — строже и раньше. */
        private int ipUserMaxFailures = 10;

        /** Окно, в котором неудачи считаются подряд идущими. */
        private Duration window = Duration.ofMinutes(15);

        /** Длительность блокировки после достижения порога. */
        private Duration lockDuration = Duration.ofMinutes(15);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getAccountMaxFailures() {
            return accountMaxFailures;
        }

        public void setAccountMaxFailures(int accountMaxFailures) {
            this.accountMaxFailures = accountMaxFailures;
        }

        public int getIpUserMaxFailures() {
            return ipUserMaxFailures;
        }

        public void setIpUserMaxFailures(int ipUserMaxFailures) {
            this.ipUserMaxFailures = ipUserMaxFailures;
        }

        public Duration getWindow() {
            return window;
        }

        public void setWindow(Duration window) {
            this.window = window;
        }

        public Duration getLockDuration() {
            return lockDuration;
        }

        public void setLockDuration(Duration lockDuration) {
            this.lockDuration = lockDuration;
        }
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
