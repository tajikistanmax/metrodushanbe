package tj.metro.dushanbe.admin.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
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
import tj.metro.dushanbe.admin.service.AdminStationService;
import tj.metro.dushanbe.admin.web.dto.AccessibilityFeatureRequest;
import tj.metro.dushanbe.admin.web.dto.StationCreateRequest;
import tj.metro.dushanbe.admin.web.dto.StationExitRequest;
import tj.metro.dushanbe.admin.web.dto.StationUpdateRequest;
import tj.metro.dushanbe.network.web.dto.AccessibilityFeatureDto;
import tj.metro.dushanbe.network.web.dto.StationDto;
import tj.metro.dushanbe.network.web.dto.StationExitDto;

/**
 * Admin-write эндпоинты станций и их деталей (ADM-02/NET-03). Пути: /api/v1/admin/stations...
 * Защищены dev-фильтром X-Admin-Key. Актор аудита — заголовок {@code X-Admin-Actor}.
 */
@RestController
@RequestMapping("/v1/admin/stations")
@Tag(name = "Admin: Stations", description = "Управление станциями, выходами и доступностью")
public class AdminStationController {

    private final AdminStationService adminStationService;

    public AdminStationController(AdminStationService adminStationService) {
        this.adminStationService = adminStationService;
    }

    @PostMapping
    @Operation(summary = "Создать станцию",
            description = "Поля: code, name{tg,ru,en}, status, coordinates[lon,lat], isTransfer?, accessibility?, description?.")
    public ResponseEntity<StationDto> create(
            @Valid @RequestBody StationCreateRequest request,
            @RequestHeader(name = "X-Admin-Actor", defaultValue = "dev-admin") String actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminStationService.create(request, actor));
    }

    @PutMapping("/{code}")
    @Operation(summary = "Обновить станцию", description = "Код неизменен (BR-NET-5). 404 station.not_found.")
    public StationDto update(
            @PathVariable("code") String code,
            @Valid @RequestBody StationUpdateRequest request,
            @RequestHeader(name = "X-Admin-Actor", defaultValue = "dev-admin") String actor) {
        return adminStationService.update(code, request, actor);
    }

    @DeleteMapping("/{code}")
    @Operation(summary = "Soft-delete станции", description = "Помечает станцию удалённой (BR-NET-2). 404 station.not_found.")
    public ResponseEntity<Void> delete(
            @PathVariable("code") String code,
            @RequestHeader(name = "X-Admin-Actor", defaultValue = "dev-admin") String actor) {
        adminStationService.softDelete(code, actor);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{code}/exits")
    @Operation(summary = "Добавить выход станции",
            description = "Поля: code, name{tg,ru,en}, coordinates[lon,lat], isAccessible?, sortOrder?.")
    public ResponseEntity<StationExitDto> createExit(
            @PathVariable("code") String stationCode,
            @Valid @RequestBody StationExitRequest request,
            @RequestHeader(name = "X-Admin-Actor", defaultValue = "dev-admin") String actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminStationService.createExit(stationCode, request, actor));
    }

    @DeleteMapping("/exits/{exitCode}")
    @Operation(summary = "Удалить выход станции", description = "404 station_exit.not_found.")
    public ResponseEntity<Void> deleteExit(
            @PathVariable("exitCode") String exitCode,
            @RequestHeader(name = "X-Admin-Actor", defaultValue = "dev-admin") String actor) {
        adminStationService.deleteExit(exitCode, actor);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{code}/accessibility-features")
    @Operation(summary = "Добавить объект доступности станции",
            description = "Поля: type, description{tg,ru,en}, status?(available|out_of_service|planned).")
    public ResponseEntity<AccessibilityFeatureDto> createFeature(
            @PathVariable("code") String stationCode,
            @Valid @RequestBody AccessibilityFeatureRequest request,
            @RequestHeader(name = "X-Admin-Actor", defaultValue = "dev-admin") String actor) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminStationService.createFeature(stationCode, request, actor));
    }

    @DeleteMapping("/accessibility-features/{featureId}")
    @Operation(summary = "Удалить объект доступности", description = "По UUID. 404 accessibility_feature.not_found.")
    public ResponseEntity<Void> deleteFeature(
            @PathVariable("featureId") UUID featureId,
            @RequestHeader(name = "X-Admin-Actor", defaultValue = "dev-admin") String actor) {
        adminStationService.deleteFeature(featureId, actor);
        return ResponseEntity.noContent().build();
    }
}
