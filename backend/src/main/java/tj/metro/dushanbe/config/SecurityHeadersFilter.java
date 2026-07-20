package tj.metro.dushanbe.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Заголовки безопасности для всех ответов API.
 *
 * Порядок фильтров (все — plain {@code @Order}, без FilterRegistrationBean):
 * RequestIdFilter (HIGHEST) → RequestLoggingFilter (+5) → RateLimitingFilter /
 * AdminKeyAuthFilter (+10) → этот фильтр ({@code @Order(1)} ≫ HIGHEST, поэтому
 * идёт последним из «наших»). Так и задумано: заголовки навешиваются на уже
 * сформированный ответ.
 *
 * CSP здесь — политика для API, а не для SPA: браузер обычно рендерит только
 * страницы Swagger UI (в проде выключены). Для JSON-ответов строгая политика
 * {@code default-src 'none'} обезвреживает любые попытки заставить браузер
 * трактовать тело ответа как исполняемый документ, а {@code frame-ancestors
 * 'none'} закрывает clickjacking. Интерактивная документация Swagger UI (только
 * dev) исключается — иначе её собственные inline-скрипты/стили были бы
 * заблокированы; CSP статики портала и консоли живёт в их next-слоях.
 */
@Component
@Order(1)
public class SecurityHeadersFilter implements Filter {

    /**
     * Строгая CSP для API-ответов. {@code base-uri 'none'} и {@code form-action
     * 'none'} довершают запрет: у JSON-эндпоинтов нет ни форм, ни базовых URL.
     */
    private static final String API_CSP =
            "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        httpResponse.setHeader("X-Content-Type-Options", "nosniff");
        httpResponse.setHeader("X-Frame-Options", "DENY");
        httpResponse.setHeader("X-XSS-Protection", "0");
        httpResponse.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        httpResponse.setHeader("Permissions-Policy", "geolocation=(), camera=(), microphone=()");

        // Swagger UI (только dev) сам себе HTML-приложение с inline-кодом —
        // строгая CSP его сломала бы. В проде springdoc выключен, поэтому в
        // боевом контуре этой ветки не бывает.
        if (!isInteractiveDocs(httpRequest)) {
            httpResponse.setHeader("Content-Security-Policy", API_CSP);
        }

        if (isSecure(httpRequest)) {
            httpResponse.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        }

        chain.doFilter(request, response);
    }

    /** Пути Swagger UI и OpenAPI-описания (с учётом context-path /api). */
    private static boolean isInteractiveDocs(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri != null && (uri.contains("/swagger-ui") || uri.contains("/v3/api-docs"));
    }

    /**
     * Настоящий ли это TLS. Прямое TLS-соединение (в проде backend поднимает
     * keystore, server.ssl.enabled=true) даёт scheme=https. За TLS-терминирующим
     * прокси до приложения доходит http, поэтому дополнительно доверяем
     * X-Forwarded-Proto. На http-localhost обе проверки дают false — HSTS не
     * выставляется, и разработчик не блокирует себе домен в браузере.
     */
    private static boolean isSecure(HttpServletRequest request) {
        if ("https".equalsIgnoreCase(request.getScheme())) {
            return true;
        }
        String forwarded = request.getHeader("X-Forwarded-Proto");
        if (forwarded == null || forwarded.isBlank()) {
            return false;
        }
        return "https".equalsIgnoreCase(forwarded.split(",")[0].trim());
    }
}
