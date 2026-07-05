package tj.metro.dushanbe.imports.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Ошибка импорта отдельной фичи (таблица {@code import_error}, IMP-03: ошибки
 * построчно/посущностно). Привязана к {@link ImportJob}; {@code featureRef} — code/id
 * проблемной фичи (или {@code $} для ошибок верхнего уровня — нечитаемый вход,
 * не FeatureCollection).
 *
 * <p>{@code severity}: {@code error} — фича отклонена и не применена;
 * {@code warning} — фича применена, но с оговоркой (напр. ссылка станции на
 * неизвестную линию — связь пропущена, сама станция сохранена).
 */
@Entity
@Table(name = "import_error")
public class ImportError {

    public static final String SEVERITY_ERROR = "error";
    public static final String SEVERITY_WARNING = "warning";

    /** Ссылка верхнего уровня для ошибок, не относящихся к конкретной фиче. */
    public static final String TOP_LEVEL_REF = "$";

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private ImportJob job;

    @Column(name = "feature_ref", nullable = false, length = 160)
    private String featureRef;

    @Column(name = "message", nullable = false)
    private String message;

    /** error|warning. */
    @Column(name = "severity", nullable = false, length = 16)
    private String severity;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    /** Конструктор для JPA. */
    protected ImportError() {
    }

    public ImportError(UUID id, ImportJob job, String featureRef, String message, String severity) {
        this.id = id;
        this.job = job;
        this.featureRef = featureRef;
        this.message = message;
        this.severity = severity;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public ImportJob getJob() {
        return job;
    }

    public String getFeatureRef() {
        return featureRef;
    }

    public String getMessage() {
        return message;
    }

    public String getSeverity() {
        return severity;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
