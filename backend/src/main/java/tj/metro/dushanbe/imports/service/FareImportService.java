package tj.metro.dushanbe.imports.service;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tj.metro.dushanbe.admin.service.AdminFareService;
import tj.metro.dushanbe.admin.web.dto.FareCreateRequest;
import tj.metro.dushanbe.admin.web.dto.FareUpdateRequest;
import tj.metro.dushanbe.audit.service.AuditService;
import tj.metro.dushanbe.common.error.BadRequestException;
import tj.metro.dushanbe.fare.domain.FareProduct;
import tj.metro.dushanbe.fare.repository.FareProductRepository;
import tj.metro.dushanbe.imports.domain.ImportError;
import tj.metro.dushanbe.imports.domain.ImportFormat;
import tj.metro.dushanbe.imports.domain.ImportJob;
import tj.metro.dushanbe.imports.repository.ImportErrorRepository;
import tj.metro.dushanbe.imports.repository.ImportJobRepository;
import tj.metro.dushanbe.imports.service.parser.FaresGtfsImportParser;
import tj.metro.dushanbe.imports.service.parser.FaresGtfsImportParser.FareImportOptions;
import tj.metro.dushanbe.imports.service.parser.FaresGtfsImportParser.FareParseResult;
import tj.metro.dushanbe.imports.service.parser.FaresGtfsImportParser.ParsedFareProduct;

/**
 * Импорт тарифного справочника из GTFS Fares v2 (INT-04, FAR-01/02): разбор фида
 * {@link FaresGtfsImportParser}, апсерт продуктов по стабильному коду (IMP-02), отчёт
 * построчно (IMP-03) и аудит. Джоб — вида {@link ImportJob#TYPE_FARE_GTFS} при том же
 * формате источника {@code gtfs}: контейнер общий, приёмник разный.
 *
 * <h2>Почему это не {@link ImportService}</h2>
 * {@link ImportService} — конвейер СЕТИ: он раскладывает {@code ParsedFeature} по
 * линиям/станциям, перепривязывает {@code metro_station_line} и гасит кэши сети. У тарифов
 * общего с этим нет ничего, кроме таблиц {@code import_job}/{@code import_error}. Второй
 * путь записи при этом не заводится: продукты пишутся ТОЛЬКО через
 * {@link AdminFareService}, откуда бесплатно приходят i18n-валидация, аудит
 * ({@code fare.create}/{@code fare.update}) и инвалидация кэша {@code fares} —
 * {@code @CacheEvict} на его методах срабатывает, потому что это отдельный бин (вызов идёт
 * через прокси).
 *
 * <h2>Чего импорт НЕ решает за оператора</h2>
 * <ul>
 *   <li><b>{@code validity_minutes}</b> — в GTFS такого поля нет (см. {@link FaresGtfsImportParser}).
 *       При создании продукт получает {@code null} (колонка nullable, V018), при
 *       обновлении сохраняется ПРЕЖНЕЕ значение: импорт не вправе стереть срок, который
 *       оператор проставил руками. Предупреждение об этом заводит парсер — на каждый продукт.</li>
 *   <li><b>{@code is_active}</b> — признака публикации в GTFS тоже нет. Без параметра
 *       {@code active} новый продукт создаётся неактивным (цена из чужого фида не должна
 *       попадать на публичный сайт, пока её не сверили), а у существующего флаг сохраняется.
 *       Явный {@code active=true|false} применяется к обоим случаям. Каждый неявный выбор —
 *       предупреждение в отчёте.</li>
 * </ul>
 *
 * <h2>Транзакции</h2>
 * Как и в {@link ImportService}: общей транзакции нет намеренно — каждый апсерт живёт в
 * своей (внутри {@link AdminFareService}), поэтому отклонение одного продукта не роняет
 * остальные, а строки отчёта сохраняются независимо.
 */
@Service
public class FareImportService {

    private static final Logger LOG = LoggerFactory.getLogger(FareImportService.class);

    private final ImportJobRepository jobRepository;
    private final ImportErrorRepository errorRepository;
    private final FaresGtfsImportParser parser;
    private final FareProductRepository fareRepository;
    private final AdminFareService adminFareService;
    private final AuditService auditService;
    private final Clock clock;

    public FareImportService(ImportJobRepository jobRepository,
                             ImportErrorRepository errorRepository,
                             FaresGtfsImportParser parser,
                             FareProductRepository fareRepository,
                             AdminFareService adminFareService,
                             AuditService auditService,
                             Clock clock) {
        this.jobRepository = jobRepository;
        this.errorRepository = errorRepository;
        this.parser = parser;
        this.fareRepository = fareRepository;
        this.adminFareService = adminFareService;
        this.auditService = auditService;
        this.clock = clock;
    }

    /**
     * Импортирует тарифы из GTFS-фида. Обработка синхронная: тарифов в фиде единицы —
     * десятки (в отличие от stop_times сети), фоновый режим тут был бы усложнением без
     * выигрыша. Возвращается уже завершённый джоб со счётчиками.
     *
     * @param source     сырые байты фида (ZIP)
     * @param options    язык фида и соответствие категорий пассажиров (см. {@link FareImportOptions})
     * @param active     публиковать ли импортированные продукты; null ⇒ решает не импорт
     *                   (новый — неактивен, существующий — как был), см. описание класса
     * @param sourceName имя источника/файла (IMP-01), может быть null
     * @param actor      субъект действия (аудит)
     * @return завершённый {@link ImportJob} (success|partial|failed)
     */
    public ImportJob importFaresGtfs(byte[] source, FareImportOptions options, Boolean active,
                                     String sourceName, String actor) {
        ImportJob job = jobRepository.save(new ImportJob(UUID.randomUUID(), ImportJob.TYPE_FARE_GTFS,
                ImportFormat.GTFS, sourceName, sha256(source)));
        try {
            job.markRunning(OffsetDateTime.now(clock));
            job = jobRepository.save(job);
            LOG.info("Fare import job {} started processing", job.getId());

            FareParseResult parsed = parser.parse(source,
                    options == null ? FareImportOptions.defaults() : options);
            if (parsed.rejected()) {
                return fail(job, parsed.topLevelErrors(), actor);
            }

            Counters counters = new Counters();
            for (ParsedFareProduct product : parsed.products()) {
                apply(job, product, active, counters, actor);
            }

            job.finish(counters.total, counters.created, counters.updated, counters.failed,
                    OffsetDateTime.now(clock));
            job = jobRepository.save(job);
            auditService.record(actor, "fare.import", "import_job", job.getId().toString(),
                    null, snapshot(job));
            LOG.info("Fare import job {} completed: {} created, {} updated, {} failed",
                    job.getId(), counters.created, counters.updated, counters.failed);
            return job;
        } catch (RuntimeException ex) {
            LOG.error("Fare import job {} failed unexpectedly: {}", job.getId(), ex.getMessage(), ex);
            job.markFailed(OffsetDateTime.now(clock));
            ImportJob saved = jobRepository.save(job);
            auditService.record(actor, "fare.import", "import_job", saved.getId().toString(),
                    null, snapshot(saved));
            return saved;
        }
    }

    private void apply(ImportJob job, ParsedFareProduct parsed, Boolean active, Counters counters,
                       String actor) {
        counters.total++;
        if (!parsed.valid()) {
            recordError(job, parsed.ref(), String.join("; ", parsed.errors()), ImportError.SEVERITY_ERROR);
            counters.failed++;
            return;
        }
        try {
            FareProduct existing = fareRepository.findByCode(parsed.code()).orElse(null);
            List<String> warnings = new ArrayList<>(parsed.warnings());
            if (existing != null) {
                // Срок действия и признак публикации переносим из существующей записи:
                // GTFS их не несёт, а AdminFareService.update перезаписывает поля целиком.
                boolean enabled = active != null ? active : existing.isActive();
                adminFareService.update(parsed.code(), new FareUpdateRequest(parsed.name(),
                        parsed.description(), parsed.amount(), parsed.currency(),
                        parsed.riderCategory(), existing.getValidityMinutes(), enabled), actor);
                counters.updated++;
                if (active == null) {
                    warnings.add("признак публикации (is_active) в GTFS отсутствует — у продукта"
                            + " сохранён прежний: " + (existing.isActive() ? "активен" : "неактивен")
                            + ". Задайте параметр active, если импорт должен его менять");
                }
            } else {
                boolean enabled = Boolean.TRUE.equals(active);
                adminFareService.create(new FareCreateRequest(parsed.code(), parsed.name(),
                        parsed.description(), parsed.amount(), parsed.currency(),
                        parsed.riderCategory(), null, enabled), actor);
                counters.created++;
                if (active == null) {
                    warnings.add("признак публикации (is_active) в GTFS отсутствует — продукт создан"
                            + " НЕАКТИВНЫМ и на публичном сайте не появится; сверьте цену и"
                            + " активируйте его вручную");
                }
            }
            recordWarnings(job, parsed.ref(), warnings);
        } catch (BadRequestException ex) {
            recordError(job, parsed.ref(), ex.getMessage(), ImportError.SEVERITY_ERROR);
            counters.failed++;
        }
    }

    /** Оговорки применённого продукта (severity=warning): цена в БД, но есть что проверить. */
    private void recordWarnings(ImportJob job, String ref, List<String> warnings) {
        for (String warning : warnings) {
            recordError(job, ref, warning, ImportError.SEVERITY_WARNING);
        }
    }

    private ImportJob fail(ImportJob job, List<String> messages, String actor) {
        for (String message : messages) {
            recordError(job, ImportError.TOP_LEVEL_REF, message, ImportError.SEVERITY_ERROR);
        }
        job.markFailed(OffsetDateTime.now(clock));
        ImportJob saved = jobRepository.save(job);
        auditService.record(actor, "fare.import", "import_job", saved.getId().toString(),
                null, snapshot(saved));
        return saved;
    }

    private void recordError(ImportJob job, String featureRef, String message, String severity) {
        errorRepository.save(new ImportError(UUID.randomUUID(), job, featureRef, message, severity));
    }

    private static Map<String, Object> snapshot(ImportJob job) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", job.getId().toString());
        snapshot.put("type", job.getType());
        snapshot.put("format", job.getFormat());
        snapshot.put("status", job.getStatus());
        snapshot.put("sourceName", job.getSourceName());
        snapshot.put("sourceHash", job.getSourceHash());
        snapshot.put("featureCount", job.getFeatureCount());
        snapshot.put("created", job.getCreatedCount());
        snapshot.put("updated", job.getUpdatedCount());
        snapshot.put("failed", job.getFailedCount());
        return snapshot;
    }

    /** SHA-256 источника (IMP-01/IMP-02) — по сырым байтам, как и у импорта сети. */
    private static String sha256(byte[] source) {
        if (source == null) {
            return null;
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source));
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
