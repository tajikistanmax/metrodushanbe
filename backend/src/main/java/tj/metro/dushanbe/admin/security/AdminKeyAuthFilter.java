package tj.metro.dushanbe.admin.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
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
 * <p><b>ВНИМАНИЕ — это НЕ полноценная прод-безопасность.</b> Проверка общего секрета
 * из конфигурации не заменяет OAuth 2.0 + JWT (Keycloak), MFA и ABAC, которых требует
 * ТЗ §6.1.7, §9.2. Переход на внешнего эмитента остаётся открытым P0.
 *
 * <h2>Актор аудита (аудит-пункт 5)</h2>
 * Раньше заголовок {@code X-Admin-Actor} принимался «на доверии»: обладатель общего
 * {@code X-Admin-Key} мог назваться любым активным суперадмином, и журнал записал бы
 * чужое имя. Теперь актор подтверждается подписанным токеном
 * {@code X-Admin-Actor-Token} (см. {@link AdminActorTokenService}):
 * <ul>
 *   <li>подпись HMAC-SHA256 на секрете {@code app.admin.actor-token.secret}, отличном
 *       от {@code X-Admin-Key} — компрометации одного недостаточно;</li>
 *   <li>{@code sessionVersion} из токена сверяется с {@code admin_user.session_version},
 *       поэтому отозванная сессия (смена роли, пароля, деактивация) перестаёт работать
 *       немедленно, а не по истечении TTL;</li>
 *   <li>после успешной проверки запрос заворачивается так, что контроллеры видят в
 *       {@code X-Admin-Actor} ПОДТВЕРЖДЁННЫЙ логин — присланное значение заголовка
 *       игнорируется целиком и подделать его нельзя.</li>
 * </ul>
 *
 * <p><b>Режимы.</b> При {@code app.admin.actor-token.required=false} (dev, интеграционные
 * тесты) сохраняется старое поведение: если токена нет, актор берётся из
 * {@code X-Admin-Actor}. Но если токен ПРИСЛАН и невалиден — это уже попытка обмана,
 * и запрос отклоняется в любом режиме. В prod-профиле свойство равно {@code true}:
 * там голый заголовок не принимается, а отсутствие секрета делает контур
 * fail-closed (401 на всё).
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

    /** Подписанный токен актора; в strict-режиме — единственный источник имени. */
    public static final String ACTOR_TOKEN_HEADER = "X-Admin-Actor-Token";

    /** Защищаемый префикс (после context-path /api). */
    private static final String ADMIN_PREFIX = "/v1/admin";

    private final AdminAuthProperties properties;
    private final ObjectMapper objectMapper;
    private final AdminUserRepository userRepository;
    private final AdminActorTokenService actorTokens;

    public AdminKeyAuthFilter(AdminAuthProperties properties, ObjectMapper objectMapper,
                              AdminUserRepository userRepository) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        // Не инжектируем бином намеренно (см. javadoc AdminActorTokenService):
        // иначе @WebMvcTest-срезы без Clock-бина перестали бы подниматься. Свежесть
        // токена — wall-clock, поэтому системных часов достаточно.
        this.actorTokens = new AdminActorTokenService(properties, Clock.systemUTC());
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
        // /auth/** — вход и проверка сессии: актора там ещё нет по определению,
        // от подбора пароля этот участок защищает lockout (AdminLoginGuard).
        if (requestPath.startsWith(ADMIN_PREFIX + "/auth/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String rawToken = request.getHeader(ACTOR_TOKEN_HEADER);
        boolean tokenPresented = rawToken != null && !rawToken.isBlank();
        AdminActorClaims claims = null;
        if (tokenPresented) {
            claims = actorTokens.verify(rawToken).orElse(null);
            if (claims == null) {
                // Присланный, но неподтверждённый токен — это попытка обмана, а не
                // «старый клиент»; на фолбэк по заголовку она права не даёт.
                writeError(request, response, HttpServletResponse.SC_UNAUTHORIZED,
                        "auth.actor_token_invalid", "Токен актора недействителен");
                return;
            }
        } else if (actorTokens.isRequired()) {
            writeError(request, response, HttpServletResponse.SC_UNAUTHORIZED,
                    "auth.actor_token_required", "Требуется подписанный токен актора");
            return;
        }

        String actor = claims != null
                ? claims.username()
                : AdminUser.normalizeUsername(request.getHeader(ACTOR_HEADER));
        AdminUser user = actor == null ? null : userRepository.findByUsername(actor).orElse(null);
        if (user == null || !user.isActive()) {
            writeError(request, response, HttpServletResponse.SC_UNAUTHORIZED,
                    "auth.session_revoked", "Учётная запись оператора недействительна");
            return;
        }
        // Расхождение версий = сессия отозвана после выпуска токена (смена роли,
        // пароля, деактивация). Ждать истечения TTL здесь нельзя: отзыв обязан
        // действовать сразу, иначе разжалованный оператор доработает своё окно.
        if (claims != null && claims.sessionVersion() != user.getSessionVersion()) {
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

        // Контроллеры читают актора из X-Admin-Actor. Подменяем его подтверждённым
        // логином — так двадцать контроллеров не нужно править, а присланное
        // клиентом значение перестаёт влиять на журнал вообще.
        filterChain.doFilter(claims == null ? request : new VerifiedActorRequest(request, actor),
                response);
    }

    /** Отдаёт {@code X-Admin-Actor} с подтверждённым логином, игнорируя присланный. */
    private static final class VerifiedActorRequest extends HttpServletRequestWrapper {

        private final String actor;

        private VerifiedActorRequest(HttpServletRequest request, String actor) {
            super(request);
            this.actor = actor;
        }

        @Override
        public String getHeader(String name) {
            return ACTOR_HEADER.equalsIgnoreCase(name) ? actor : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            return ACTOR_HEADER.equalsIgnoreCase(name)
                    ? Collections.enumeration(List.of(actor))
                    : super.getHeaders(name);
        }
    }

    private static AdminRole requiredRole(String path, String method) {
        if ("GET".equalsIgnoreCase(method)) {
            return path.startsWith(ADMIN_PREFIX + "/users") ? AdminRole.SUPERADMIN : AdminRole.VIEWER;
        }
        // Разбор упавшей доставки вебхука (U-OPS-04) — рутина дежурной смены, и
        // требовать ради повтора суперадмина значит парализовать смену ночью.
        // Проверяется ДО общего /webhooks ниже: тот префикс перехватил бы этот
        // путь и поднял требование до SUPERADMIN. Порядок здесь — не стилистика.
        if (path.startsWith(ADMIN_PREFIX + "/webhooks/deliveries")) {
            return AdminRole.OPERATOR;
        }
        if (path.startsWith(ADMIN_PREFIX + "/users")
                || path.startsWith(ADMIN_PREFIX + "/feature-flags")
                || path.startsWith(ADMIN_PREFIX + "/imports")
                // Подписчик вебхука — это внешний адрес, куда уходят данные сети,
                // плюс секрет подписи и лимиты. Заводить их вправе только суперадмин.
                || path.startsWith(ADMIN_PREFIX + "/webhooks")) {
            return AdminRole.SUPERADMIN;
        }
        // Операционный контур: дежурная смена ведёт его сама, без прав редактора
        // на справочники. Должно совпадать с requireAdminRole в admin-actions.ts —
        // иначе консоль покажет действие, которое backend отклонит с 403.
        //
        // /notifications — рассылки ведёт смена; /notification-templates сюда НЕ
        // попадает (префиксы расходятся на '-' против 's') и остаётся редакционным
        // справочником на EDITOR — это осознанно.
        if (path.startsWith(ADMIN_PREFIX + "/alerts")
                || path.startsWith(ADMIN_PREFIX + "/requests")
                || path.startsWith(ADMIN_PREFIX + "/incidents")
                || path.startsWith(ADMIN_PREFIX + "/notifications")
                // Билеты, платежи и чёрный список: возврат пассажиру и блокировка
                // скомпрометированного билета — работа кассы и дежурного, а не
                // редактора справочников.
                || path.startsWith(ADMIN_PREFIX + "/tickets")
                || path.startsWith(ADMIN_PREFIX + "/payments")
                || path.startsWith(ADMIN_PREFIX + "/blocklist")) {
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
