package tj.metro.dushanbe.admin.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tj.metro.dushanbe.admin.service.AdminUserService;
import tj.metro.dushanbe.admin.web.dto.AdminLoginRequest;
import tj.metro.dushanbe.config.RateLimitProperties;
import tj.metro.dushanbe.identity.web.dto.AdminLoginResultDto;
import tj.metro.dushanbe.identity.web.dto.AdminUserDto;

/**
 * Проверка учётных данных оператора при входе в консоль.
 *
 * <p>Эндпоинт живёт под {@code /v1/admin/**} и потому закрыт {@code X-Admin-Key}:
 * вызывать его может только серверный контур Next, но не браузер. Пароль таким
 * образом никогда не покидает доверенный периметр, а сессионную cookie выпускает
 * и подписывает уже Next.
 */
@RestController
@RequestMapping("/v1/admin/auth")
@Tag(name = "Admin: Auth", description = "Вход операторов консоли")
public class AdminAuthController {

    private final AdminUserService service;
    private final RateLimitProperties rateLimitProperties;

    public AdminAuthController(AdminUserService service, RateLimitProperties rateLimitProperties) {
        this.service = service;
        this.rateLimitProperties = rateLimitProperties;
    }

    @PostMapping("/login")
    @Operation(summary = "Проверить логин и пароль оператора",
            description = "401 auth.invalid_credentials при неверных данных или отключённой учётной "
                    + "записи; 429 auth.too_many_attempts при блокировке после серии неудач (аудит-пункт 9)")
    public AdminLoginResultDto login(@Valid @RequestBody AdminLoginRequest request,
                                     HttpServletRequest httpRequest) {
        return new AdminLoginResultDto(service.authenticate(request, clientIp(httpRequest)));
    }

    /**
     * Адрес клиента для счётчика IP+логин. XFF учитывается только если ему доверяет
     * {@code app.rate-limit.trust-forwarded-for} — тем же правилом, что и общий
     * rate-limit (аудит-пункт 7): иначе клиент подставил бы любой IP и уводил бы
     * блокировку с себя.
     */
    private String clientIp(HttpServletRequest request) {
        if (rateLimitProperties.isTrustForwardedFor()) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        String remoteAddr = request.getRemoteAddr();
        return remoteAddr != null ? remoteAddr : "unknown";
    }

    @GetMapping("/session/{username}")
    @Operation(summary = "Проверить актуальность серверной сессии оператора")
    public AdminUserDto session(@PathVariable("username") String username) {
        return service.current(username);
    }
}
