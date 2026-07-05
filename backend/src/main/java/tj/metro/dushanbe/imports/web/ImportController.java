package tj.metro.dushanbe.imports.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tj.metro.dushanbe.imports.domain.ImportJob;
import tj.metro.dushanbe.imports.service.ImportQueryService;
import tj.metro.dushanbe.imports.service.ImportService;
import tj.metro.dushanbe.imports.web.dto.ImportErrorDto;
import tj.metro.dushanbe.imports.web.dto.ImportJobDto;
import tj.metro.dushanbe.imports.web.dto.ImportPageDto;

/**
 * Admin-эндпоинты импорта данных сети (INT-04, §13). Итоговые пути с учётом
 * context-path: {@code /api/v1/admin/imports...}. Под префиксом {@code /v1/admin/**}
 * защищены dev-фильтром X-Admin-Key ({@code AdminKeyAuthFilter}; прод-авторизация —
 * Keycloak/OAuth2/RBAC, ТЗ §6.1.7). Актор аудита — заголовок {@code X-Admin-Actor}.
 *
 * <p>POST выполняет импорт синхронно (MVP; TODO PERF-05 — фоновые джобы) и возвращает
 * сводку джоба со счётчиками и статусом (success|partial|failed).
 */
@RestController
@RequestMapping("/v1/admin/imports")
@Tag(name = "Admin: Imports", description = "Импорт данных сети (GeoJSON; задел под GTFS/CSV)")
public class ImportController {

    private final ImportService importService;
    private final ImportQueryService importQueryService;

    public ImportController(ImportService importService, ImportQueryService importQueryService) {
        this.importService = importService;
        this.importQueryService = importQueryService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Импортировать сеть из GeoJSON",
            description = "Тело — GeoJSON FeatureCollection (линии/станции по образцу data/demo-network.geojson). "
                    + "Апсерт по стабильному code (идемпотентно). Возвращает сводку джоба: статус и счётчики "
                    + "created/updated/failed. Имя источника — заголовок X-Import-Source.")
    public ImportJobDto importNetwork(
            @RequestBody String body,
            @Parameter(description = "Имя источника/файла (IMP-01)")
            @RequestHeader(name = "X-Import-Source", required = false) String sourceName,
            @RequestHeader(name = "X-Admin-Actor", defaultValue = "dev-admin") String actor) {
        ImportJob job = importService.importNetworkGeoJson(body, sourceName, actor);
        return ImportQueryService.toDto(job);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Сводка задания импорта", description = "Статус и счётчики джоба. 404 import.not_found.")
    public ImportJobDto job(@PathVariable("id") UUID id) {
        return importQueryService.job(id);
    }

    @GetMapping("/{id}/errors")
    @Operation(summary = "Ошибки задания импорта", description = "Построчные ошибки джоба (IMP-03). 404 import.not_found.")
    public List<ImportErrorDto> errors(@PathVariable("id") UUID id) {
        return importQueryService.errors(id);
    }

    @GetMapping
    @Operation(summary = "Лента заданий импорта",
            description = "Постраничный список джобов, новые сверху. Размер страницы ограничен сверху (200).")
    public ImportPageDto list(
            @Parameter(description = "Номер страницы, начиная с 0")
            @RequestParam(name = "page", defaultValue = "0") int page,
            @Parameter(description = "Размер страницы (1..200)")
            @RequestParam(name = "size", defaultValue = "50") int size) {
        return importQueryService.page(page, size);
    }
}
