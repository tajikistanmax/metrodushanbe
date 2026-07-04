package tj.metro.dushanbe.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация OpenAPI (springdoc).
 * UI доступен по адресу http://localhost:8080/api/swagger-ui.html.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI metroOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Metro Dushanbe API")
                .version("v1")
                .description("Публичное API национальной платформы «Метро Душанбе»: "
                        + "сетевой каталог (линии, станции) и гео-слой сети (GeoJSON, RFC 7946). "
                        + "Контракт — docs/dev-conventions.md."));
    }
}
