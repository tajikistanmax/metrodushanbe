package tj.metro.dushanbe.admin.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tj.metro.dushanbe.admin.service.AdminUserService;
import tj.metro.dushanbe.admin.web.dto.AdminUserCreateRequest;
import tj.metro.dushanbe.admin.web.dto.AdminUserUpdateRequest;
import tj.metro.dushanbe.identity.web.dto.AdminUserDto;
import tj.metro.dushanbe.identity.domain.AdminRole;

/**
 * Управление операторами консоли (ADM-01).
 *
 * <p>Раздел доступен только роли {@code superadmin}; проверка роли выполняется в
 * Next-контуре консоли по claims подписанной сессии (admin/src/lib/server-auth.ts),
 * поскольку backend доверяет вызовам с корректным {@code X-Admin-Key}. Заголовок
 * {@code X-Admin-Actor} несёт логин актора для аудита.
 */
@RestController
@RequestMapping("/v1/admin/users")
@Tag(name = "Admin: Users", description = "Операторы консоли и их роли")
public class AdminUserController {

    private final AdminUserService service;

    public AdminUserController(AdminUserService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Все операторы, включая отключённых")
    public List<AdminUserDto> list(
            @RequestHeader(name = "X-Admin-Actor") String actor) {
        service.requireRole(actor, AdminRole.SUPERADMIN);
        return service.list();
    }

    @PostMapping
    public ResponseEntity<AdminUserDto> create(
            @Valid @RequestBody AdminUserCreateRequest request,
            @RequestHeader(name = "X-Admin-Actor") String actor) {
        service.requireRole(actor, AdminRole.SUPERADMIN);
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request, actor));
    }

    @PutMapping("/{username}")
    public AdminUserDto update(
            @PathVariable("username") String username,
            @Valid @RequestBody AdminUserUpdateRequest request,
            @RequestHeader(name = "X-Admin-Actor") String actor) {
        service.requireRole(actor, AdminRole.SUPERADMIN);
        return service.update(username, request, actor);
    }

    @DeleteMapping("/{username}")
    public ResponseEntity<Void> delete(
            @PathVariable("username") String username,
            @RequestHeader(name = "X-Admin-Actor") String actor) {
        service.requireRole(actor, AdminRole.SUPERADMIN);
        service.delete(username, actor);
        return ResponseEntity.noContent().build();
    }
}
