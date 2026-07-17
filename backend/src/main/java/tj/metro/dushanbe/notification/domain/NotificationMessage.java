package tj.metro.dushanbe.notification.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
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
 * Рассылка: что, кому, по каким каналам и в каком состоянии (NTF-01…06).
 *
 * <p>Тексты хранятся здесь целиком, даже если рассылка создана из шаблона: копия
 * делается один раз при создании, и дальше сообщение от шаблона не зависит
 * (см. шапку V022). {@code templateCode}/{@code alertCode} — только следы
 * происхождения, без FK.
 *
 * <p>Состояние меняется только через {@link #moveTo}: он же проставляет
 * {@code sentAt}. Прямого сеттера статуса нет намеренно — иначе сообщение легко
 * оставить в состоянии {@code sent} без {@code sent_at}, а БД такую запись не
 * примет (chk_notification_message_sent).
 */
@Entity
@Table(name = "notification_message")
public class NotificationMessage {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 64)
    private String code;

    /** Шаблон-источник, если рассылка создана из шаблона. Только след, без FK. */
    @Column(name = "template_code", length = 64)
    private String templateCode;

    /** Связанный service_alert, если рассылка сделана по алерту. Только след, без FK. */
    @Column(name = "alert_code", length = 64)
    private String alertCode;

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

    @Convert(converter = NotificationStatus.Persistence.class)
    @Column(name = "status", nullable = false, length = 16)
    private NotificationStatus status;

    /** Отложенная публикация (NTF-05); NULL = черновик либо немедленная отправка. */
    @Column(name = "scheduled_at")
    private OffsetDateTime scheduledAt;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @Column(name = "created_by", nullable = false, length = 64)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** Адресация (NTF-02). ПУСТОЙ набор = вся сеть — семантика service_alert_target. */
    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.LAZY)
    private List<NotificationTarget> targets = new ArrayList<>();

    /** Конструктор для JPA. */
    protected NotificationMessage() {
    }

    /**
     * Новая рассылка всегда стартует черновиком: планирование и отправка — это
     * отдельные переходы (NotificationStatus.TRANSITIONS), а не параметр создания.
     * Иначе рассылку можно было бы создать сразу в {@code sent}, минуя доставку.
     */
    public NotificationMessage(UUID id, String code, String templateCode, String alertCode,
                               NotificationType type, Map<String, String> titleI18n,
                               Map<String, String> bodyI18n, List<String> channels,
                               OffsetDateTime scheduledAt, String createdBy) {
        this.id = id;
        this.code = code;
        this.templateCode = templateCode;
        this.alertCode = alertCode;
        this.type = type;
        this.titleI18n = titleI18n;
        this.bodyI18n = bodyI18n;
        this.channels = new ArrayList<>(channels);
        this.status = NotificationStatus.DRAFT;
        this.scheduledAt = scheduledAt;
        this.createdBy = createdBy;
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

    /**
     * Редакционное изменение рассылки. Запрет на правку замороженной рассылки
     * ({@code status.frozen()}) — гейт вызывающего сервиса: там он даёт оператору
     * внятную 400, а не падение.
     */
    public void update(NotificationType newType, Map<String, String> newTitleI18n,
                       Map<String, String> newBodyI18n, List<String> newChannels,
                       OffsetDateTime newScheduledAt) {
        type = newType;
        titleI18n = newTitleI18n;
        bodyI18n = newBodyI18n;
        channels = new ArrayList<>(newChannels);
        scheduledAt = newScheduledAt;
    }

    /**
     * Переводит рассылку в новое состояние и проставляет отметку отправки.
     * Допустимость перехода проверяет вызывающий сервис — здесь только
     * последствия, чтобы состояние и отметки не разъезжались.
     *
     * <p>Инварианты повторяют CHECK-констрейнты V022, чтобы нарушение ловилось
     * здесь, а не в виде ConstraintViolation из глубины Hibernate:
     * {@code scheduled} обязан знать своё время (chk_notification_message_scheduled),
     * {@code sent} — свой факт отправки (chk_notification_message_sent).
     */
    public void moveTo(NotificationStatus target, OffsetDateTime at) {
        if (target == NotificationStatus.SCHEDULED && scheduledAt == null) {
            throw new IllegalStateException(
                    "Рассылка " + code + " не может быть запланирована без scheduledAt");
        }
        status = target;
        if (target == NotificationStatus.SENT) {
            sentAt = at;
        }
    }

    /** Добавляет таргет (NTF-02), сохраняя обратную ссылку — иначе message_id будет NULL. */
    public NotificationTarget addTarget(TargetType targetType, String targetCode) {
        NotificationTarget target = new NotificationTarget(
                UUID.randomUUID(), this, targetType, targetCode);
        targets.add(target);
        return target;
    }

    /** Полная замена адресации; orphanRemoval убирает отвязанные строки. */
    public void replaceTargets(List<NotificationTarget> newTargets) {
        targets.clear();
        targets.addAll(newTargets);
    }

    /** Пустая адресация = рассылка на всю сеть (та же семантика, что у алертов). */
    public boolean networkWide() {
        return targets.isEmpty();
    }

    /** Уходит ли рассылка в канал (NTF-01). */
    public boolean hasChannel(NotificationChannel channel) {
        return channels != null && channels.contains(channel.code());
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getTemplateCode() { return templateCode; }
    public String getAlertCode() { return alertCode; }
    public NotificationType getType() { return type; }
    public Map<String, String> getTitleI18n() { return titleI18n; }
    public Map<String, String> getBodyI18n() { return bodyI18n; }
    public List<String> getChannels() { return channels; }
    public NotificationStatus getStatus() { return status; }
    public OffsetDateTime getScheduledAt() { return scheduledAt; }
    public OffsetDateTime getSentAt() { return sentAt; }
    public String getCreatedBy() { return createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public List<NotificationTarget> getTargets() { return targets; }
}
