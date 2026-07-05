package tj.metro.dushanbe.admin.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tj.metro.dushanbe.common.error.ApiError;
import tj.metro.dushanbe.common.error.RequestIdFilter;

/**
 * Dev-фильтр авторизации админского контура: пропускает запросы к {@code /v1/admin/**}
 * (итоговый путь {@code /api/v1/admin/**}) только при корректном заголовке
 * {@code X-Admin-Key}, совпадающем со свойством {@code app.admin.dev-key}.
 * Отсутствие/несовпадение ключа → 401 в едином envelope {@link ApiError}
 * (код {@code admin.unauthorized}).
 *
 * <p><b>ВНИМАНИЕ — временная dev-заглушка, НЕ прод-безопасность.</b> Это простая
 * проверка общего секрета из конфигурации без пользователей, ролей и MFA. Продовый
 * контур (ТЗ §6.1.7, §9.2) обязан использовать OAuth 2.0 + JWT (Keycloak), MFA для
 * admin/operator, RBAC + ABAC, TLS и anti-abuse. Заголовок {@code X-Admin-Actor}
 * (актор аудита) в dev принимается «на доверии» и в проде должен извлекаться из JWT.
 *
 * <p>Порядок фильтра — сразу после {@link RequestIdFilter} (тот проставляет requestId),
 * чтобы 401-envelope содержал корректный requestId.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AdminKeyAuthFilter extends OncePerRequestFilter {

    /** Заголовок с dev-ключом администратора. */
    public static final String HEADER = "X-Admin-Key";

    /** Защищаемый префикс (после context-path /api). */
    private static final String ADMIN_PREFIX = "/v1/admin";

    private final AdminAuthProperties properties;
    private final ObjectMapper objectMapper;

    public AdminKeyAuthFilter(AdminAuthProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !path(request).startsWith(ADMIN_PREFIX);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String provided = request.getHeader(HEADER);
        String expected = properties.getDevKey();
        if (expected == null || expected.isBlank() || !expected.equals(provided)) {
            writeUnauthorized(request, response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    /** Путь запроса в пределах context-path (servletPath), напр. /v1/admin/lines. */
    private static String path(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        return servletPath != null ? servletPath : request.getRequestURI();
    }

    private void writeUnauthorized(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Object requestId = request.getAttribute(RequestIdFilter.ATTRIBUTE);
        ApiError body = ApiError.of(
                requestId != null ? requestId.toString() : "",
                "admin.unauthorized",
                "Требуется корректный заголовок X-Admin-Key (dev-авторизация админского контура)",
                null);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "X-Admin-Key");
        objectMapper.writeValue(response.getWriter(), body);
    }
}
