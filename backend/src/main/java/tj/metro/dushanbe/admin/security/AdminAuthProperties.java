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

    public String getDevKey() {
        return devKey;
    }

    public void setDevKey(String devKey) {
        this.devKey = devKey;
    }
}
