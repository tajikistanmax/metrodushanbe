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
import tj.metro.dushanbe.identity.domain.AdminRole;
import tj.metro.dushanbe.identity.domain.AdminUser;
import tj.metro.dushanbe.identity.repository.AdminUserRepository;

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
    public static final String ACTOR_HEADER = "X-Admin-Actor";

    /** Защищаемый префикс (после context-path /api). */
    private static final String ADMIN_PREFIX = "/v1/admin";

    private final AdminAuthProperties properties;
    private final ObjectMapper objectMapper;
    private final AdminUserRepository userRepository;

    public AdminKeyAuthFilter(AdminAuthProperties properties, ObjectMapper objectMapper,
                              AdminUserRepository userRepository) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
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
            writeError(request, response, HttpServletResponse.SC_UNAUTHORIZED,
                    "admin.unauthorized", "Требуется корректный X-Admin-Key");
            return;
        }

        String requestPath = path(request);
        if (!requestPath.startsWith(ADMIN_PREFIX + "/auth/")) {
            String actor = AdminUser.normalizeUsername(request.getHeader(ACTOR_HEADER));
            var user = actor == null ? null : userRepository.findByUsername(actor).orElse(null);
            if (user == null || !user.isActive()) {
                writeError(request, response, HttpServletResponse.SC_UNAUTHORIZED,
                        "auth.session_revoked", "Учётная запись оператора недействительна");
                return;
            }
            AdminRole required = requiredRole(requestPath, request.getMethod());
            if (!user.getRole().includes(required)) {
                writeError(request, response, HttpServletResponse.SC_FORBIDDEN,
                        "admin.forbidden", "Недостаточно прав для операции");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private static AdminRole requiredRole(String path, String method) {
        if ("GET".equalsIgnoreCase(method)) {
            return path.startsWith(ADMIN_PREFIX + "/users") ? AdminRole.SUPERADMIN : AdminRole.VIEWER;
        }
        if (path.startsWith(ADMIN_PREFIX + "/users")
                || path.startsWith(ADMIN_PREFIX + "/feature-flags")
                || path.startsWith(ADMIN_PREFIX + "/imports")) {
            return AdminRole.SUPERADMIN;
        }
        // Операционный контур: дежурная смена ведёт его сама, без прав редактора
        // на справочники. Должно совпадать с requireAdminRole в admin-actions.ts —
        // иначе консоль покажет действие, которое backend отклонит с 403.
        if (path.startsWith(ADMIN_PREFIX + "/alerts")
                || path.startsWith(ADMIN_PREFIX + "/requests")
                || path.startsWith(ADMIN_PREFIX + "/incidents")) {
            return AdminRole.OPERATOR;
        }
        return AdminRole.EDITOR;
    }

    /** Путь запроса в пределах context-path (servletPath), напр. /v1/admin/lines. */
    private static String path(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        return servletPath != null ? servletPath : request.getRequestURI();
    }

    private void writeError(HttpServletRequest request, HttpServletResponse response, int status,
                            String code, String message) throws IOException {
        Object requestId = request.getAttribute(RequestIdFilter.ATTRIBUTE);
        ApiError body = ApiError.of(
                requestId != null ? requestId.toString() : "",
                code,
                message,
                null);
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        if (status == HttpServletResponse.SC_UNAUTHORIZED) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "X-Admin-Key");
        }
        objectMapper.writeValue(response.getWriter(), body);
    }
}
