package tj.metro.dushanbe.admin.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tj.metro.dushanbe.admin.service.AdminLineService;
import tj.metro.dushanbe.admin.web.dto.LineCreateRequest;
import tj.metro.dushanbe.admin.web.dto.LineUpdateRequest;
import tj.metro.dushanbe.network.web.dto.LineDto;

/**
 * Admin-write эндпоинты линий (ADM-02). Итоговые пути: /api/v1/admin/lines...
 * Защищены dev-фильтром X-Admin-Key (AdminKeyAuthFilter). Актор аудита — заголовок
 * {@code X-Admin-Actor} (dev-доверие; в проде — субъект из JWT, ТЗ §6.1.7).
 */
@RestController
@RequestMapping("/v1/admin/lines")
@Tag(name = "Admin: Lines", description = "Управление линиями (create/update/soft-delete)")
public class AdminLineController {

    private final AdminLineService adminLineService;

    public AdminLineController(AdminLineService adminLineService) {
        this.adminLineService = adminLineService;
    }

    @PostMapping
    @Operation(summary = "Создать линию",
            description = "Поля: code, name{tg,ru,en}, colorHex(#RRGGBB), status, sortOrder?, path?[[lon,lat],...].")
    public ResponseEntity<LineDto> create(
            @Valid @RequestBody LineCreateRequest request,
            @RequestHeader(name = "X-Admin-Actor", defaultValue = "dev-admin") String actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminLineService.create(request, actor));
    }

    @PutMapping("/{code}")
    @Operation(summary = "Обновить линию",
            description = "Меняет цвет/статус/название/порядок/геометрию. Код неизменен (BR-NET-5). 404 line.not_found.")
    public LineDto update(
            @PathVariable("code") String code,
            @Valid @RequestBody LineUpdateRequest request,
            @RequestHeader(name = "X-Admin-Actor", defaultValue = "dev-admin") String actor) {
        return adminLineService.update(code, request, actor);
    }

    @DeleteMapping("/{code}")
    @Operation(summary = "Soft-delete линии",
            description = "Помечает линию удалённой (BR-NET-2), без физического удаления. 404 line.not_found.")
    public ResponseEntity<Void> delete(
            @PathVariable("code") String code,
            @RequestHeader(name = "X-Admin-Actor", defaultValue = "dev-admin") String actor) {
        adminLineService.softDelete(code, actor);
        return ResponseEntity.noContent().build();
    }
}
