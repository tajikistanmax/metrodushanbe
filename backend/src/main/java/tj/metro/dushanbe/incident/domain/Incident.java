package tj.metro.dushanbe.incident.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Внутренняя запись об инциденте (INC-01).
 *
 * <p>Переходы состояния выполняются только через {@link #moveTo}: он же
 * проставляет отметки времени. Прямого сеттера статуса нет намеренно — иначе
 * запись легко оставить в состоянии «resolved» без {@code resolvedAt}, и
 * отчётность по времени устранения перестанет сходиться.
 */
@Entity
@Table(name = "incident")
public class Incident {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 64)
    private String code;

    @Convert(converter = IncidentCategory.Persistence.class)
    @Column(name = "category", nullable = false, length = 24)
    private IncidentCategory category;

    @Convert(converter = IncidentSeverity.Persistence.class)
    @Column(name = "severity", nullable = false, length = 16)
    private IncidentSeverity severity;

    @Convert(converter = IncidentStatus.Persistence.class)
    @Column(name = "status", nullable = false, length = 24)
    private IncidentStatus status;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "line_code", length = 64)
    private String lineCode;

    @Column(name = "station_code", length = 64)
    private String stationCode;

    @Column(name = "reported_by", nullable = false, length = 64)
    private String reportedBy;

    @Column(name = "assigned_to", length = 64)
    private String assignedTo;

    @Column(name = "resolution")
    private String resolution;

    @Column(name = "public_alert_code", length = 64)
    private String publicAlertCode;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @Column(name = "acknowledged_at")
    private OffsetDateTime acknowledgedAt;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Incident() {
    }

    public Incident(UUID id, String code, IncidentCategory category, IncidentSeverity severity,
                    String title, String description, String lineCode, String stationCode,
                    String reportedBy, String assignedTo, OffsetDateTime occurredAt) {
        this.id = id;
        this.code = code;
        this.category = category;
        this.severity = severity;
        this.status = IncidentStatus.OPEN;
        this.title = title;
        this.description = description;
        this.lineCode = lineCode;
        this.stationCode = stationCode;
        this.reportedBy = reportedBy;
        this.assignedTo = assignedTo;
        this.occurredAt = occurredAt;
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

    /** Редактирование карточки; статус и отметки времени сюда не входят. */
    public void updateDetails(IncidentCategory newCategory, IncidentSeverity newSeverity,
                              String newTitle, String newDescription, String newLineCode,
                              String newStationCode, String newAssignedTo,
                              OffsetDateTime newOccurredAt) {
        category = newCategory;
        severity = newSeverity;
        title = newTitle;
        description = newDescription;
        lineCode = newLineCode;
        stationCode = newStationCode;
        assignedTo = newAssignedTo;
        occurredAt = newOccurredAt;
    }

    /**
     * Переводит инцидент в новое состояние и проставляет соответствующую отметку
     * времени. Допустимость перехода проверяет вызывающий сервис — здесь только
     * последствия перехода, чтобы состояние и отметки не разъезжались.
     *
     * <p>Отметка ставится только при первом входе в состояние: при возврате
     * resolved → in_progress → resolved {@code resolvedAt} обновится на момент
     * повторного устранения, а {@code acknowledgedAt} останется исходным — время
     * первой реакции важнее последней.
     */
    public void moveTo(IncidentStatus target, String newResolution, OffsetDateTime at) {
        status = target;
        switch (target) {
            case ACKNOWLEDGED -> {
                if (acknowledgedAt == null) {
                    acknowledgedAt = at;
                }
            }
            case RESOLVED -> {
                resolution = newResolution;
                resolvedAt = at;
            }
            case CLOSED -> {
                if (newResolution != null && !newResolution.isBlank()) {
                    resolution = newResolution;
                }
                closedAt = at;
            }
            case IN_PROGRESS -> {
                // Возврат к работе: снимаем отметки завершения, иначе инцидент
                // будет числиться и открытым, и устранённым одновременно.
                resolvedAt = null;
                closedAt = null;
            }
            case OPEN -> {
                acknowledgedAt = null;
                assignedTo = null;
            }
            default -> throw new IllegalStateException("Необработанный переход: " + target);
        }
    }

    public void linkPublicAlert(String alertCode) {
        publicAlertCode = alertCode;
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public IncidentCategory getCategory() { return category; }
    public IncidentSeverity getSeverity() { return severity; }
    public IncidentStatus getStatus() { return status; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getLineCode() { return lineCode; }
    public String getStationCode() { return stationCode; }
    public String getReportedBy() { return reportedBy; }
    public String getAssignedTo() { return assignedTo; }
    public String getResolution() { return resolution; }
    public String getPublicAlertCode() { return publicAlertCode; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public OffsetDateTime getAcknowledgedAt() { return acknowledgedAt; }
    public OffsetDateTime getResolvedAt() { return resolvedAt; }
    public OffsetDateTime getClosedAt() { return closedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
