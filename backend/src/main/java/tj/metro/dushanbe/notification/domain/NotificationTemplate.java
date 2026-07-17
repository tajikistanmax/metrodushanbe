package tj.metro.dushanbe.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Заготовка текста рассылки (NTF-05).
 *
 * <p>Шаблон — источник значений ТОЛЬКО в момент создания рассылки: тексты
 * копируются в {@link NotificationMessage} и дальше живут своей жизнью. Поэтому
 * FK из сообщения на шаблон нет (см. шапку V022): вывод шаблона из употребления
 * не имеет права переписать историю того, что уже разослано.
 */
@Entity
@Table(name = "notification_template")
public class NotificationTemplate {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 64)
    private String code;

    /** Служебное имя для консоли; публикуется не оно, а title_i18n. */
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Convert(converter = NotificationType.Persistence.class)
    @Column(name = "type", nullable = false, length = 24)
    private NotificationType type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "title_i18n", nullable = false)
    private Map<String, String> titleI18n;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "body_i18n", nullable = false)
    private Map<String, String> bodyI18n;

    /** Коды каналов (NotificationChannel.code()); состав проверяет сервис — см. V022. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "channels", nullable = false)
    private List<String> channels;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** Конструктор для JPA. */
    protected NotificationTemplate() {
    }

    public NotificationTemplate(UUID id, String code, String name, NotificationType type,
                                Map<String, String> titleI18n, Map<String, String> bodyI18n,
                                List<String> channels, boolean active) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.type = type;
        this.titleI18n = titleI18n;
        this.bodyI18n = bodyI18n;
        this.channels = new ArrayList<>(channels);
        this.active = active;
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

    /** Редактирование шаблона; стабильный код не меняется. Гейты — на вызывающем сервисе. */
    public void update(String newName, NotificationType newType, Map<String, String> newTitleI18n,
                       Map<String, String> newBodyI18n, List<String> newChannels, boolean newActive) {
        name = newName;
        type = newType;
        titleI18n = newTitleI18n;
        bodyI18n = newBodyI18n;
        channels = new ArrayList<>(newChannels);
        active = newActive;
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public NotificationType getType() { return type; }
    public Map<String, String> getTitleI18n() { return titleI18n; }
    public Map<String, String> getBodyI18n() { return bodyI18n; }
    public List<String> getChannels() { return channels; }
    public boolean isActive() { return active; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
