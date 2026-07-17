package tj.metro.dushanbe.admin.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tj.metro.dushanbe.admin.service.AdminUserService;
import tj.metro.dushanbe.admin.web.dto.AdminLoginRequest;
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

    public AdminAuthController(AdminUserService service) {
        this.service = service;
    }

    @PostMapping("/login")
    @Operation(summary = "Проверить логин и пароль оператора",
            description = "401 auth.invalid_credentials при неверных данных или отключённой учётной записи")
    public AdminLoginResultDto login(@Valid @RequestBody AdminLoginRequest request) {
        return new AdminLoginResultDto(service.authenticate(request));
    }

    @GetMapping("/session/{username}")
    @Operation(summary = "Проверить актуальность серверной сессии оператора")
    public AdminUserDto session(@PathVariable("username") String username) {
        return service.current(username);
    }
}
