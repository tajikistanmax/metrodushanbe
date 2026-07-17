package tj.metro.dushanbe.citizen.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Обращение гражданина и его операционный жизненный цикл (REQ-01/03/04). */
@Entity
@Table(name = "citizen_request")
public class CitizenRequest {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "public_code", nullable = false, unique = true, length = 24)
    private String publicCode;

    @Column(name = "tracking_token_hash", nullable = false, length = 64)
    private String trackingTokenHash;

    @Column(name = "request_type", nullable = false, length = 32)
    private String type;

    @Column(name = "priority", nullable = false, length = 16)
    private String priority;

    @Column(name = "status", nullable = false, length = 24)
    private String status;

    @Column(name = "subject", nullable = false, length = 200)
    private String subject;

    @Column(name = "message", nullable = false, columnDefinition = "text")
    private String message;

    @Column(name = "contact_name", length = 160)
    private String contactName;

    @Column(name = "contact_email", length = 254)
    private String contactEmail;

    @Column(name = "contact_phone", length = 40)
    private String contactPhone;

    @Column(name = "line_code", length = 64)
    private String lineCode;

    @Column(name = "station_code", length = 64)
    private String stationCode;

    @Column(name = "response_text", columnDefinition = "text")
    private String response;

    @Column(name = "assigned_to", length = 128)
    private String assignedTo;

    @Column(name = "sla_response_due_at", nullable = false)
    private OffsetDateTime responseDueAt;

    @Column(name = "sla_resolution_due_at", nullable = false)
    private OffsetDateTime resolutionDueAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    protected CitizenRequest() {
    }

    public CitizenRequest(UUID id, String publicCode, String trackingTokenHash,
                          String type, String priority, String subject, String message,
                          String contactName, String contactEmail, String contactPhone,
                          String lineCode, String stationCode, OffsetDateTime responseDueAt,
                          OffsetDateTime resolutionDueAt, OffsetDateTime createdAt) {
        this.id = id;
        this.publicCode = publicCode;
        this.trackingTokenHash = trackingTokenHash;
        this.type = type;
        this.priority = priority;
        this.status = "new";
        this.subject = subject;
        this.message = message;
        this.contactName = contactName;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
        this.lineCode = lineCode;
        this.stationCode = stationCode;
        this.responseDueAt = responseDueAt;
        this.resolutionDueAt = resolutionDueAt;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    /** Применяет проверенный сервисом переход статуса и ответ оператора. */
    public void updateWorkflow(String nextStatus, String response, String assignedTo,
                               OffsetDateTime updatedAt) {
        this.status = nextStatus;
        this.response = response;
        this.assignedTo = assignedTo;
        this.updatedAt = updatedAt;
        if ("resolved".equals(nextStatus) || "closed".equals(nextStatus)) {
            if (resolvedAt == null) {
                resolvedAt = updatedAt;
            }
        } else {
            resolvedAt = null;
        }
    }

    public UUID getId() {
        return id;
    }

    public String getPublicCode() {
        return publicCode;
    }

    public String getTrackingTokenHash() {
        return trackingTokenHash;
    }

    public String getType() {
        return type;
    }

    public String getPriority() {
        return priority;
    }

    public String getStatus() {
        return status;
    }

    public String getSubject() {
        return subject;
    }

    public String getMessage() {
        return message;
    }

    public String getContactName() {
        return contactName;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public String getLineCode() {
        return lineCode;
    }

    public String getStationCode() {
        return stationCode;
    }

    public String getResponse() {
        return response;
    }

    public String getAssignedTo() {
        return assignedTo;
    }

    public OffsetDateTime getResponseDueAt() {
        return responseDueAt;
    }

    public OffsetDateTime getResolutionDueAt() {
        return resolutionDueAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public OffsetDateTime getResolvedAt() {
        return resolvedAt;
    }
}
