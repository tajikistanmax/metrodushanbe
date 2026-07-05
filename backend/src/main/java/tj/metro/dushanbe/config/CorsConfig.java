package tj.metro.dushanbe.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS для dev-окружения: публичному порталу (Next.js, http://localhost:3000) и
 * админ-панели (http://localhost:3001) разрешён доступ к API. Контракт —
 * docs/dev-conventions.md §3.
 *
 * <p>Origin админки (3001) добавлен для write-эндпоинтов {@code /api/v1/admin/**},
 * если админка обращается к ним из браузера; при серверных вызовах CORS не требуется.
 * Заголовок {@code X-Admin-Key} проходит через {@code allowedHeaders("*")}.
 */
@Configuration
public class CorsConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(@NonNull CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOrigins("http://localhost:3000", "http://localhost:3001")
                        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .exposedHeaders("X-Request-Id");
            }
        };
    }
}
