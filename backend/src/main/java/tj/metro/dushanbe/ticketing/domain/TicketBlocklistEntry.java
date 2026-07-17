package tj.metro.dushanbe.ticketing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Запись чёрного списка (TKT-06).
 *
 * <p>Проверяется при каждой валидации и каждой покупке. Флага «активна» нет
 * намеренно: снятие блокировки — удаление записи, а её след остаётся в аудите
 * (кто снял, когда и что было в снимке before). Два источника правды о том,
 * действует ли блокировка (наличие строки и флаг), рано или поздно разошлись бы.
 *
 * <p>{@code updatedAt} нет по той же причине: запись не редактируется — ошибочную
 * блокировку снимают и заводят заново, чтобы в аудите остались оба факта.
 */
@Entity
@Table(name = "ticket_blocklist")
public class TicketBlocklistEntry {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 64)
    private String code;

    @Convert(converter = BlocklistSubjectType.Persistence.class)
    @Column(name = "subject_type", nullable = false, length = 16)
    private BlocklistSubjectType subjectType;

    /** Для {@code token} здесь SHA-256, а не токен — см. BlocklistSubjectType. */
    @Column(name = "subject_code", nullable = false, length = 64)
    private String subjectCode;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Column(name = "created_by", nullable = false, length = 64)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected TicketBlocklistEntry() {
    }

    public TicketBlocklistEntry(UUID id, String code, BlocklistSubjectType subjectType,
                                String subjectCode, String reason, String createdBy) {
        this.id = id;
        this.code = code;
        this.subjectType = subjectType;
        this.subjectCode = subjectCode;
        this.reason = reason;
        this.createdBy = createdBy;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public BlocklistSubjectType getSubjectType() { return subjectType; }
    public String getSubjectCode() { return subjectCode; }
    public String getReason() { return reason; }
    public String getCreatedBy() { return createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
