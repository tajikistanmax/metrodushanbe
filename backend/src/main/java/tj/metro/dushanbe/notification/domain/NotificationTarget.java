package tj.metro.dushanbe.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Адресация рассылки (NTF-02): ссылка на объект сети или на аудиторию по коду.
 *
 * <p>В отличие от {@code AlertTarget}, который сделан @Embeddable, здесь полноценная
 * сущность: у notification_target есть собственный id (V022), по таргетам идёт
 * самостоятельный поиск (ix_notification_target_lookup) и отдельный репозиторий.
 */
@Entity
@Table(name = "notification_target")
public class NotificationTarget {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    /** Таргет не имеет смысла в отрыве от рассылки — отсюда ON DELETE CASCADE в V022. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false)
    private NotificationMessage message;

    @Convert(converter = TargetType.Persistence.class)
    @Column(name = "target_type", nullable = false, length = 16)
    private TargetType targetType;

    /** metro_line.code / metro_station.code / код сегмента / код роли. */
    @Column(name = "target_code", nullable = false, length = 64)
    private String targetCode;

    /** Конструктор для JPA. */
    protected NotificationTarget() {
    }

    public NotificationTarget(UUID id, NotificationMessage message, TargetType targetType,
                              String targetCode) {
        this.id = id;
        this.message = message;
        this.targetType = targetType;
        this.targetCode = targetCode;
    }

    /** Совпадает ли таргет с парой тип/код — предикат фильтров публичного фида. */
    public boolean matches(TargetType type, String code) {
        return targetType == type && targetCode.equals(code);
    }

    public UUID getId() { return id; }
    public NotificationMessage getMessage() { return message; }
    public TargetType getTargetType() { return targetType; }
    public String getTargetCode() { return targetCode; }
}
