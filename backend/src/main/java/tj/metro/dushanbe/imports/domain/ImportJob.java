package tj.metro.dushanbe.imports.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Задание импорта данных сети (таблица {@code import_job}, ТЗ §6.2.8 INT-04, §13.2, §15.3).
 * Одна строка на запуск конвейера импорта: вид, статус жизненного цикла, источник и его
 * hash (IMP-01), счётчики применённых/отклонённых фич (created/updated/failed) и окно
 * выполнения (started_at/finished_at).
 *
 * <p>Жизненный цикл: {@code pending → running → success|partial|failed}. Статус
 * {@code partial} — часть фич применена, часть отклонена (см. {@link #finish}); в MVP
 * применение синхронно, но модель готова под фоновые джобы (PERF-05, TODO).
 */
@Entity
@Table(name = "import_job")
public class ImportJob {

    /** Вид импорта сети из GeoJSON FeatureCollection (задел под gtfs/csv). */
    public static final String TYPE_NETWORK_GEOJSON = "network_geojson";

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_RUNNING = "running";
    public static final String STATUS_SUCCESS = "success";
    public static final String STATUS_PARTIAL = "partial";
    public static final String STATUS_FAILED = "failed";

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "type", nullable = false, length = 32)
    private String type;

    /** pending|running|success|partial|failed. */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /** Имя источника/файла (IMP-01); в dev — заголовок {@code X-Import-Source}. */
    @Column(name = "source_name", length = 256)
    private String sourceName;

    /** SHA-256 тела импорта в hex (IMP-01/IMP-02); NULL, если тело нечитаемо. */
    @Column(name = "source_hash", length = 64)
    private String sourceHash;

    @Column(name = "feature_count", nullable = false)
    private int featureCount;

    @Column(name = "created_count", nullable = false)
    private int createdCount;

    @Column(name = "updated_count", nullable = false)
    private int updatedCount;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** Конструктор для JPA. */
    protected ImportJob() {
    }

    public ImportJob(UUID id, String type, String sourceName, String sourceHash) {
        this.id = id;
        this.type = type;
        this.status = STATUS_PENDING;
        this.sourceName = sourceName;
        this.sourceHash = sourceHash;
    }

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    /** Перевод джоба в состояние выполнения (фиксирует started_at). */
    public void markRunning(OffsetDateTime when) {
        this.status = STATUS_RUNNING;
        this.startedAt = when;
    }

    /**
     * Финализация джоба по итогам применения: проставляет счётчики, finished_at и статус.
     * Статус вычисляется из итогов: нет фич или все отклонены → {@code failed};
     * часть отклонена при наличии успешных → {@code partial}; иначе {@code success}.
     */
    public void finish(int featureCount, int createdCount, int updatedCount, int failedCount,
                       OffsetDateTime when) {
        this.featureCount = featureCount;
        this.createdCount = createdCount;
        this.updatedCount = updatedCount;
        this.failedCount = failedCount;
        this.finishedAt = when;
        int applied = createdCount + updatedCount;
        if (applied == 0) {
            this.status = STATUS_FAILED;
        } else if (failedCount > 0) {
            this.status = STATUS_PARTIAL;
        } else {
            this.status = STATUS_SUCCESS;
        }
    }

    /** Финализация как проваленного джоба (нечитаемый вход/не FeatureCollection). */
    public void markFailed(OffsetDateTime when) {
        this.status = STATUS_FAILED;
        this.finishedAt = when;
    }

    public UUID getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getStatus() {
        return status;
    }

    public String getSourceName() {
        return sourceName;
    }

    public String getSourceHash() {
        return sourceHash;
    }

    public int getFeatureCount() {
        return featureCount;
    }

    public int getCreatedCount() {
        return createdCount;
    }

    public int getUpdatedCount() {
        return updatedCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public OffsetDateTime getFinishedAt() {
        return finishedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
