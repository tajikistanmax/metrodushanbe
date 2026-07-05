package tj.metro.dushanbe.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Событие аудита (таблица {@code audit_event}, ТЗ §6.2.10, ADM-05, BR-ADM-1).
 * Журнал append-only: экземпляры только создаются, никогда не изменяются и не
 * удаляются прикладным кодом. Снимки {@code before}/{@code after} — состояние
 * сущности до/после операции (JSONB); {@code before} = null для создания.
 */
@Entity
@Table(name = "audit_event")
public class AuditEvent {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    /** Субъект действия. В dev-контуре — значение заголовка {@code X-Admin-Actor}. */
    @Column(name = "actor", nullable = false, length = 128)
    private String actor;

    /** Машиночитаемый код действия, например {@code line.create}, {@code alert.publish}. */
    @Column(name = "action", nullable = false, length = 64)
    private String action;

    /** Тип сущности: line|station|station_exit|accessibility_feature|alert|news. */
    @Column(name = "entity_type", nullable = false, length = 64)
    private String entityType;

    /** Стабильный код/слаг затронутой сущности. */
    @Column(name = "entity_id", nullable = false, length = 160)
    private String entityId;

    /** Снимок состояния до операции; null для создания. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_state")
    private Map<String, Object> before;

    /** Снимок состояния после операции; null, если сущность удалена без снимка. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_state")
    private Map<String, Object> after;

    @Column(name = "at", nullable = false)
    private OffsetDateTime at;

    /** Конструктор для JPA. */
    protected AuditEvent() {
    }

    public AuditEvent(UUID id, String actor, String action, String entityType, String entityId,
                      Map<String, Object> before, Map<String, Object> after, OffsetDateTime at) {
        this.id = id;
        this.actor = actor;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.before = before;
        this.after = after;
        this.at = at;
    }

    @PrePersist
    void onCreate() {
        if (at == null) {
            at = OffsetDateTime.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public String getActor() {
        return actor;
    }

    public String getAction() {
        return action;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public Map<String, Object> getBefore() {
        return before;
    }

    public Map<String, Object> getAfter() {
        return after;
    }

    public OffsetDateTime getAt() {
        return at;
    }
}
