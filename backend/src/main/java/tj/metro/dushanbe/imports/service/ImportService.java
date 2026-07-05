package tj.metro.dushanbe.imports.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tj.metro.dushanbe.admin.service.AdminLineService;
import tj.metro.dushanbe.admin.service.AdminStationService;
import tj.metro.dushanbe.admin.web.dto.LineCreateRequest;
import tj.metro.dushanbe.admin.web.dto.LineUpdateRequest;
import tj.metro.dushanbe.admin.web.dto.StationCreateRequest;
import tj.metro.dushanbe.admin.web.dto.StationUpdateRequest;
import tj.metro.dushanbe.audit.service.AuditService;
import tj.metro.dushanbe.common.error.BadRequestException;
import tj.metro.dushanbe.imports.domain.ImportError;
import tj.metro.dushanbe.imports.domain.ImportJob;
import tj.metro.dushanbe.imports.repository.ImportErrorRepository;
import tj.metro.dushanbe.imports.repository.ImportJobRepository;
import tj.metro.dushanbe.imports.service.NetworkImportValidator.Kind;
import tj.metro.dushanbe.imports.service.NetworkImportValidator.ParsedFeature;
import tj.metro.dushanbe.network.domain.MetroLine;
import tj.metro.dushanbe.network.domain.MetroStation;
import tj.metro.dushanbe.network.domain.MetroStationLine;
import tj.metro.dushanbe.network.repository.MetroLineRepository;
import tj.metro.dushanbe.network.repository.MetroStationLineRepository;
import tj.metro.dushanbe.network.repository.MetroStationRepository;

/**
 * Сервис импорта сети из GeoJSON FeatureCollection (INT-04, §13). Принимает тело
 * импорта, валидирует каждую фичу ({@link NetworkImportValidator}) и апсертит линии/
 * станции, переиспользуя admin-сервисы ({@link AdminLineService}/{@link AdminStationService})
 * — та же валидация, персистентность и запись в аудит, без дублирования доменной логики.
 * Итог фиксируется в {@link ImportJob} (счётчики/статус) и построчных {@link ImportError}
 * (IMP-03); идемпотентность — по стабильному {@code code} (IMP-02).
 *
 * <h2>Синхронное применение (MVP)</h2>
 * Импорт выполняется синхронно в рамках HTTP-запроса. TODO(PERF-05): вынести в фоновую
 * очередь/джоб с preview-diff (IMP-04/IMP-05) — модель ImportJob уже поддерживает
 * жизненный цикл pending → running → success|partial|failed.
 *
 * <h2>Транзакции</h2>
 * Оркестрация НЕ обёрнута в общую транзакцию намеренно: каждый апсерт фичи выполняется
 * в собственной транзакции admin-сервиса, поэтому отклонение одной фичи (её транзакция
 * откатывается) не отравляет остальные и не роняет весь импорт. Строки ImportJob/
 * ImportError сохраняются отдельными транзакциями репозиториев — ошибки фиксируются
 * даже при последующих сбоях.
 */
@Service
public class ImportService {

    private final ImportJobRepository jobRepository;
    private final ImportErrorRepository errorRepository;
    private final NetworkImportValidator validator;
    private final AdminLineService adminLineService;
    private final AdminStationService adminStationService;
    private final MetroLineRepository lineRepository;
    private final MetroStationRepository stationRepository;
    private final MetroStationLineRepository stationLineRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ImportService(ImportJobRepository jobRepository,
                         ImportErrorRepository errorRepository,
                         NetworkImportValidator validator,
                         AdminLineService adminLineService,
                         AdminStationService adminStationService,
                         MetroLineRepository lineRepository,
                         MetroStationRepository stationRepository,
                         MetroStationLineRepository stationLineRepository,
                         AuditService auditService,
                         ObjectMapper objectMapper,
                         Clock clock) {
        this.jobRepository = jobRepository;
        this.errorRepository = errorRepository;
        this.validator = validator;
        this.adminLineService = adminLineService;
        this.adminStationService = adminStationService;
        this.lineRepository = lineRepository;
        this.stationRepository = stationRepository;
        this.stationLineRepository = stationLineRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * Выполнить импорт сети из GeoJSON-тела. Всегда создаёт запись {@link ImportJob}
     * (даже при нечитаемом входе — со статусом failed) и возвращает её id.
     *
     * @param body       сырое тело импорта (GeoJSON FeatureCollection)
     * @param sourceName имя источника/файла (IMP-01), может быть null
     * @param actor      субъект действия (аудит)
     * @return сохранённый (финализированный) {@link ImportJob}
     */
    public ImportJob importNetworkGeoJson(String body, String sourceName, String actor) {
        ImportJob job = jobRepository.save(new ImportJob(
                UUID.randomUUID(), ImportJob.TYPE_NETWORK_GEOJSON, sourceName, sha256(body)));
        job.markRunning(OffsetDateTime.now(clock));
        job = jobRepository.save(job);

        JsonNode root;
        try {
            root = objectMapper.readTree(body == null ? "" : body);
        } catch (Exception ex) {
            return fail(job, "тело импорта не является корректным JSON: " + ex.getMessage(), actor);
        }
        if (root == null || root.isMissingNode() || !"FeatureCollection".equals(root.path("type").asText(null))) {
            return fail(job, "ожидался GeoJSON FeatureCollection (поле type)", actor);
        }
        JsonNode features = root.path("features");
        if (!features.isArray()) {
            return fail(job, "поле features должно быть массивом фич", actor);
        }

        Counters counters = new Counters();
        // Порядок применения: сначала линии (на них ссылаются станции), затем станции.
        for (JsonNode feature : features) {
            ParsedFeature parsed = validator.parse(feature);
            if (parsed.kind() == Kind.LINE) {
                applyLine(job, parsed, counters, actor);
            }
        }
        Map<String, Integer> positions = new LinkedHashMap<>();
        for (JsonNode feature : features) {
            ParsedFeature parsed = validator.parse(feature);
            if (parsed.kind() == Kind.STATION) {
                applyStation(job, parsed, counters, positions, actor);
            } else if (parsed.kind() == Kind.UNKNOWN) {
                recordError(job, parsed.ref(), String.join("; ", parsed.errors()), ImportError.SEVERITY_ERROR);
                counters.failed++;
            }
        }

        job.finish(counters.total, counters.created, counters.updated, counters.failed,
                OffsetDateTime.now(clock));
        job = jobRepository.save(job);
        auditService.record(actor, "network.import", "import_job", job.getId().toString(), null, snapshot(job));
        return job;
    }

    private void applyLine(ImportJob job, ParsedFeature parsed, Counters counters, String actor) {
        counters.total++;
        if (!parsed.valid()) {
            recordError(job, parsed.ref(), String.join("; ", parsed.errors()), ImportError.SEVERITY_ERROR);
            counters.failed++;
            return;
        }
        try {
            boolean exists = lineRepository.existsByCode(parsed.code());
            if (exists) {
                adminLineService.update(parsed.code(), new LineUpdateRequest(
                        parsed.name(), parsed.colorHex(), parsed.status(), parsed.sortOrder(), parsed.path()), actor);
                counters.updated++;
            } else {
                adminLineService.create(new LineCreateRequest(
                        parsed.code(), parsed.name(), parsed.colorHex(), parsed.status(),
                        parsed.sortOrder(), parsed.path()), actor);
                counters.created++;
            }
        } catch (BadRequestException ex) {
            recordError(job, parsed.ref(), ex.getMessage(), ImportError.SEVERITY_ERROR);
            counters.failed++;
        }
    }

    private void applyStation(ImportJob job, ParsedFeature parsed, Counters counters,
                              Map<String, Integer> positions, String actor) {
        counters.total++;
        if (!parsed.valid()) {
            recordError(job, parsed.ref(), String.join("; ", parsed.errors()), ImportError.SEVERITY_ERROR);
            counters.failed++;
            return;
        }
        try {
            boolean exists = stationRepository.existsByCode(parsed.code());
            if (exists) {
                adminStationService.update(parsed.code(), new StationUpdateRequest(
                        parsed.name(), parsed.status(), parsed.coordinates(),
                        parsed.isTransfer(), parsed.accessibility(), parsed.description()), actor);
                counters.updated++;
            } else {
                adminStationService.create(new StationCreateRequest(
                        parsed.code(), parsed.name(), parsed.status(), parsed.coordinates(),
                        parsed.isTransfer(), parsed.accessibility(), parsed.description()), actor);
                counters.created++;
            }
            relinkStation(job, parsed, positions);
        } catch (BadRequestException ex) {
            recordError(job, parsed.ref(), ex.getMessage(), ImportError.SEVERITY_ERROR);
            counters.failed++;
        }
    }

    /**
     * Идемпотентно перепривязывает станцию к её линиям (metro_station_line): удаляет
     * прежние связи станции и создаёт заново из входных {@code lines[]}. Позиция вдоль
     * линии — по порядку появления станций во входе (счётчик на линию). Ссылка на
     * неизвестную линию — не ошибка импорта станции: связь пропускается с warning.
     */
    private void relinkStation(ImportJob job, ParsedFeature parsed, Map<String, Integer> positions) {
        MetroStation station = stationRepository.findByCode(parsed.code()).orElseThrow();
        stationLineRepository.deleteAll(stationLineRepository.findByStation_Code(parsed.code()));
        List<String> lineCodes = parsed.lineCodes() != null ? parsed.lineCodes() : List.of();
        for (String lineCode : lineCodes) {
            MetroLine line = lineRepository.findByCode(lineCode).orElse(null);
            if (line == null) {
                recordError(job, parsed.ref(),
                        "станция ссылается на неизвестную линию '" + lineCode + "' — связь пропущена",
                        ImportError.SEVERITY_WARNING);
                continue;
            }
            int position = positions.merge(lineCode, 1, Integer::sum);
            stationLineRepository.save(new MetroStationLine(station, line, position));
        }
    }

    private ImportJob fail(ImportJob job, String message, String actor) {
        recordError(job, ImportError.TOP_LEVEL_REF, message, ImportError.SEVERITY_ERROR);
        job.markFailed(OffsetDateTime.now(clock));
        ImportJob saved = jobRepository.save(job);
        auditService.record(actor, "network.import", "import_job", saved.getId().toString(), null, snapshot(saved));
        return saved;
    }

    private void recordError(ImportJob job, String featureRef, String message, String severity) {
        errorRepository.save(new ImportError(UUID.randomUUID(), job, featureRef, message, severity));
    }

    private static Map<String, Object> snapshot(ImportJob job) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", job.getId().toString());
        snapshot.put("type", job.getType());
        snapshot.put("status", job.getStatus());
        snapshot.put("sourceName", job.getSourceName());
        snapshot.put("sourceHash", job.getSourceHash());
        snapshot.put("featureCount", job.getFeatureCount());
        snapshot.put("created", job.getCreatedCount());
        snapshot.put("updated", job.getUpdatedCount());
        snapshot.put("failed", job.getFailedCount());
        return snapshot;
    }

    private static String sha256(String body) {
        if (body == null) {
            return null;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            return null;
        }
    }

    /** Изменяемый аккумулятор счётчиков за один прогон импорта. */
    private static final class Counters {
        private int total;
        private int created;
        private int updated;
        private int failed;
    }
}
