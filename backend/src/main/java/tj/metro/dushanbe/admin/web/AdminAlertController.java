package tj.metro.dushanbe.admin.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tj.metro.dushanbe.admin.service.AdminAlertService;
import tj.metro.dushanbe.admin.web.dto.AlertCreateRequest;
import tj.metro.dushanbe.admin.web.dto.AlertUpdateRequest;
import tj.metro.dushanbe.alert.web.dto.AlertDto;

/**
 * Admin-write эндпоинты сервисных уведомлений (ADM-02, ТЗ §6.2.6). Пути:
 * /api/v1/admin/alerts... Защищены dev-фильтром X-Admin-Key. Создание — в статусе
 * draft; публикация — отдельным вызовом (жизненный цикл + гейт языков).
 */
@RestController
@RequestMapping("/v1/admin/alerts")
@Tag(name = "Admin: Alerts", description = "Управление уведомлениями (draft → published)")
public class AdminAlertController {

    private final AdminAlertService adminAlertService;

    public AdminAlertController(AdminAlertService adminAlertService) {
        this.adminAlertService = adminAlertService;
    }

    @PostMapping
    @Operation(summary = "Создать уведомление (draft)",
            description = "Поля: code, severity(info|warning|critical), title{tg,ru,en}, body{tg,ru,en}, "
                    + "startsAt, endsAt?, targets?[{type:line|station, code}].")
    public ResponseEntity<AlertDto> create(
            @Valid @RequestBody AlertCreateRequest request,
            @RequestHeader(name = "X-Admin-Actor", defaultValue = "dev-admin") String actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminAlertService.create(request, actor));
    }

    @PutMapping("/{code}")
    @Operation(summary = "Обновить уведомление", description = "Меняет содержание/окно/таргеты. 404 alert.not_found.")
    public AlertDto update(
            @PathVariable("code") String code,
            @Valid @RequestBody AlertUpdateRequest request,
            @RequestHeader(name = "X-Admin-Actor", defaultValue = "dev-admin") String actor) {
        return adminAlertService.update(code, request, actor);
    }

    @PostMapping("/{code}/publish")
    @Operation(summary = "Опубликовать уведомление",
            description = "Переход draft/review/approved → published с гейтом языков (tg/ru/en в title/body). "
                    + "400 alert.status_invalid — из недопустимого статуса; 404 alert.not_found.")
    public AlertDto publish(
            @PathVariable("code") String code,
            @RequestHeader(name = "X-Admin-Actor", defaultValue = "dev-admin") String actor) {
        return adminAlertService.publish(code, actor);
    }
}
