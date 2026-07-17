package tj.metro.dushanbe.imports.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tj.metro.dushanbe.imports.domain.ImportFormat;
import tj.metro.dushanbe.imports.domain.ImportJob;
import tj.metro.dushanbe.imports.service.FareImportService;
import tj.metro.dushanbe.imports.service.ImportQueryService;
import tj.metro.dushanbe.imports.service.ImportService;
import tj.metro.dushanbe.imports.service.parser.FaresGtfsImportParser.FareImportOptions;
import tj.metro.dushanbe.imports.service.parser.NetworkImportParser.ImportOptions;
import tj.metro.dushanbe.imports.web.dto.ImportErrorDto;
import tj.metro.dushanbe.imports.web.dto.ImportJobDto;
import tj.metro.dushanbe.imports.web.dto.ImportPageDto;

/**
 * Admin-эндпоинты импорта данных (INT-04, §13). Итоговые пути с учётом context-path:
 * {@code /api/v1/admin/imports...}. Под префиксом {@code /v1/admin/**} защищены
 * dev-фильтром X-Admin-Key ({@code AdminKeyAuthFilter}; прод-авторизация —
 * Keycloak/OAuth2/RBAC, ТЗ §6.1.7). Актор аудита — заголовок {@code X-Admin-Actor}.
 *
 * <p>Формат источника определяет путь, а не поле тела: GeoJSON — JSON, GTFS — бинарный
 * ZIP, CSV — текст, и один эндпоинт не может принимать всё это без выдумывания обёртки.
 * Формат применённого джоба виден в ответе ({@code format}: geojson|gtfs|csv).
 *
 * <p>{@code /gtfs} и {@code /gtfs-fares} — разные эндпоинты при одном формате: контейнер
 * общий, но первый импортирует топологию сети, второй — тарифный справочник, у них разные
 * параметры и разные приёмники. Параметром одного эндпоинта это было бы хуже: набор
 * query-параметров зависел бы от значения другого параметра, а в ленте джобов два разных
 * по последствиям импорта стали бы неразличимы. Что именно импортировали, видно в
 * {@code type}: {@code network_gtfs} против {@code fare_gtfs}.
 *
 * <p>Импорт сети по умолчанию синхронный (200 OK со сводкой), при включённом feature-флаге
 * {@code import.async} — 202 Accepted + Location. Импорт тарифов всегда синхронный.
 */
@RestController
@RequestMapping("/v1/admin/imports")
@Tag(name = "Admin: Imports", description = "Импорт данных сети (GeoJSON/GTFS/CSV) и тарифов (GTFS Fares v2)")
public class ImportController {

    private final ImportService importService;
    private final FareImportService fareImportService;
    private final ImportQueryService importQueryService;

    public ImportController(ImportService importService,
                            FareImportService fareImportService,
                            ImportQueryService importQueryService) {
        this.importService = importService;
        this.fareImportService = fareImportService;
        this.importQueryService = importQueryService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Импортировать сеть из GeoJSON",
            description = "Тело — GeoJSON FeatureCollection (линии/станции по образцу data/demo-network.geojson). "
                    + "Апсерт по стабильному code (идемпотентно). По умолчанию импорт синхронный и возвращает "
                    + "200 OK со сводкой джоба (счётчики created/updated/failed). При включённом feature-флаге "
                    + "import.async задание уходит в фон: 202 Accepted + Location, статус — по GET /{id}. "
                    + "Имя источника — заголовок X-Import-Source.")
    public ResponseEntity<ImportJobDto> importNetwork(
            @RequestBody String body,
            @Parameter(description = "Имя источника/файла (IMP-01)")
            @RequestHeader(name = "X-Import-Source", required = false) String sourceName,
            @RequestHeader(name = "X-Admin-Actor", defaultValue = "dev-admin") String actor) {
        return respond(importService.importNetworkGeoJson(body, sourceName, actor));
    }

    @PostMapping(value = "/gtfs", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @Operation(summary = "Импортировать сеть из GTFS-фида",
            description = "Тело — GTFS-фид (ZIP с agency/routes/stops/trips/stop_times/shapes/transfers). "
                    + "Импортируются маршруты метро (route_type=1) и их остановки; порядок станций выводится "
                    + "из наиболее полного рейса, геометрия — из shapes.txt. Апсерт по route_id/stop_id "
                    + "(идемпотентно, IMP-02). GTFS одноязычен: язык фида берётся из параметра lang, иначе из "
                    + "agency.txt:agency_lang; недостающие языки заполняются строкой языка фида и помечаются "
                    + "предупреждениями (severity=warning) в GET /{id}/errors — список того, что требует перевода. "
                    + "Статуса жизненного цикла в GTFS нет: он задаётся параметром status (по умолчанию active). "
                    + "Ошибки: 400 import.format_unsupported.")
    public ResponseEntity<ImportJobDto> importGtfs(
            @RequestBody byte[] body,
            @Parameter(description = "Язык фида: tg|ru|en. Если не указан — agency.txt:agency_lang")
            @RequestParam(name = "lang", required = false) String language,
            @Parameter(description = "Статус импортируемых линий/станций (по умолчанию active)")
            @RequestParam(name = "status", required = false) String status,
            @Parameter(description = "Имя источника/файла (IMP-01)")
            @RequestHeader(name = "X-Import-Source", required = false) String sourceName,
            @RequestHeader(name = "X-Admin-Actor", defaultValue = "dev-admin") String actor) {
        ImportJob job = importService.importNetwork(body, ImportFormat.GTFS,
                new ImportOptions(language, status), sourceName, actor);
        return respond(job);
    }

    @PostMapping(value = "/gtfs-fares", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @Operation(summary = "Импортировать тарифы из GTFS Fares v2",
            description = "Тело — GTFS-фид (ZIP) с fare_products.txt (+ rider_categories.txt, fare_media.txt, "
                    + "agency.txt). Импортируются тарифные продукты в справочник fare_product; сеть этот "
                    + "эндпоинт не трогает (для неё — POST /imports/gtfs). Апсерт по коду, полученному из "
                    + "fare_product_id (идемпотентно, IMP-02). Обработка синхронная: 200 OK со сводкой джоба "
                    + "(type=fare_gtfs, format=gtfs). "
                    + "Категория пассажира: rider_category_id ищется в параметре riderCategories, иначе должен "
                    + "буквально совпасть с одной из all|adult|child|student|senior, иначе — ошибка строки "
                    + "(тихого приведения нет: это льгота пассажира). "
                    + "validity_minutes в GTFS отсутствует как поле: импортом не задаётся (новый продукт — без "
                    + "срока, у существующего сохраняется прежний). Носитель (fare_media) моделью не "
                    + "поддерживается — попадает в предупреждения. Всё достроенное и взятое по умолчанию "
                    + "видно как severity=warning в GET /{id}/errors. "
                    + "Ошибки: 400 validation.i18n_incomplete, fare.code_exists (на отдельных строках отчёта).")
    public ResponseEntity<ImportJobDto> importGtfsFares(
            @RequestBody byte[] body,
            @Parameter(description = "Язык фида: tg|ru|en. Если не указан — agency.txt:agency_lang")
            @RequestParam(name = "lang", required = false) String language,
            @Parameter(description = "Соответствие категорий фида категориям справочника, "
                    + "например RC_ADULT:adult,RC_KID:child")
            @RequestParam(name = "riderCategories", required = false) String riderCategories,
            @Parameter(description = "Публиковать импортированные тарифы. Не указан — новый продукт "
                    + "создаётся неактивным, у существующего флаг сохраняется")
            @RequestParam(name = "active", required = false) Boolean active,
            @Parameter(description = "Имя источника/файла (IMP-01)")
            @RequestHeader(name = "X-Import-Source", required = false) String sourceName,
            @RequestHeader(name = "X-Admin-Actor", defaultValue = "dev-admin") String actor) {
        ImportJob job = fareImportService.importFaresGtfs(body,
                new FareImportOptions(language, riderCategories), active, sourceName, actor);
        return respond(job);
    }

    @PostMapping(value = "/csv", consumes = {"text/csv", MediaType.TEXT_PLAIN_VALUE})
    @Operation(summary = "Импортировать сеть из CSV-таблицы",
            description = "Тело — CSV в UTF-8: заголовок + строки. Обязательные колонки: entity(line|station), "
                    + "code, name_tg, name_ru, name_en, status; для линии — color_hex (+sort_order), для станции — "
                    + "lon, lat (+lines через '|', is_transfer, accessibility, description_tg/ru/en). Порядок "
                    + "станций на линии — по порядку строк. Апсерт по code (идемпотентно, IMP-02). Ошибки "
                    + "сообщаются построчно с номером строки файла (GET /{id}/errors). "
                    + "Ошибки: 400 import.format_unsupported.")
    public ResponseEntity<ImportJobDto> importCsv(
            @RequestBody String body,
            @Parameter(description = "Имя источника/файла (IMP-01)")
            @RequestHeader(name = "X-Import-Source", required = false) String sourceName,
            @RequestHeader(name = "X-Admin-Actor", defaultValue = "dev-admin") String actor) {
        ImportJob job = importService.importNetwork(body == null ? null : body.getBytes(StandardCharsets.UTF_8),
                ImportFormat.CSV, ImportOptions.defaults(), sourceName, actor);
        return respond(job);
    }

    /**
     * Незавершённый джоб (pending/running) ⇒ импорт запущен в фоне (async-режим): 202 +
     * Location на сводку. Завершённый (success|partial|failed) ⇒ синхронный импорт: 200 OK.
     *
     * <p>Location строится от context-path, а не от текущего запроса: у /gtfs и /csv
     * дописывание «/{id}» к текущему пути дало бы несуществующий ресурс
     * {@code /v1/admin/imports/gtfs/{id}}.
     */
    private static ResponseEntity<ImportJobDto> respond(ImportJob job) {
        ImportJobDto dto = ImportQueryService.toDto(job);
        if (ImportJob.STATUS_PENDING.equals(job.getStatus()) || ImportJob.STATUS_RUNNING.equals(job.getStatus())) {
            URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path("/v1/admin/imports/{id}")
                    .buildAndExpand(job.getId()).toUri();
            return ResponseEntity.accepted().location(location).body(dto);
        }
        return ResponseEntity.ok(dto);
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
