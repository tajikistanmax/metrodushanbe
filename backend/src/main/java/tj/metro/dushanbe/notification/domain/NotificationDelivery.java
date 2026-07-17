package tj.metro.dushanbe.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Факт доставки по одному каналу одному получателю (NTF-06): подтверждение и повтор.
 *
 * <p>Внешнего кода (как {@code code} у остальных сущностей) у доставки нет — в V022
 * такой колонки нет намеренно: доставка не самостоятельный объект учёта, оператор
 * приходит к ней от рассылки. Внешний идентификатор для REST — её {@code id}.
 *
 * <p>Состояние меняется только через markSent/markDelivered/markFailed/retry:
 * иначе легко получить {@code failed} без причины, чего не примет БД
 * (chk_notification_delivery_error), либо «доставлено» без отметки времени.
 */
@Entity
@Table(name = "notification_delivery", uniqueConstraints = @UniqueConstraint(
        name = "uq_notification_delivery_recipient",
        columnNames = {"message_id", "channel", "recipient"}))
public class NotificationDelivery {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false)
    private NotificationMessage message;

    /** Код канала (NotificationChannel.code()) — см. chk_notification_delivery_channel. */
    @Column(name = "channel", nullable = false, length = 16)
    private String channel;

    /** Адрес в терминах канала. На demo-контуре — только demo-получатели (см. V022). */
    @Column(name = "recipient", nullable = false, length = 254)
    private String recipient;

    @Convert(converter = DeliveryStatus.Persistence.class)
    @Column(name = "status", nullable = false, length = 16)
    private DeliveryStatus status;

    /** Счётчик попыток для повторной отправки (NTF-06). */
    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @Column(name = "delivered_at")
    private OffsetDateTime deliveredAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** Конструктор для JPA. */
    protected NotificationDelivery() {
    }

    /** Доставка всегда рождается в очереди: отправку фиксирует только {@link #markSent}. */
    public NotificationDelivery(UUID id, NotificationMessage message, NotificationChannel channel,
                                String recipient) {
        this.id = id;
        this.message = message;
        this.channel = channel.code();
        this.recipient = recipient;
        this.status = DeliveryStatus.PENDING;
        this.attempts = 0;
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
     * Отдано каналу. Инкрементирует {@code attempts}: попытка засчитывается по
     * факту обращения к каналу, а не по её исходу, — иначе счётчик повторов
     * (NTF-06) не покажет, сколько раз мы реально дёргали провайдера.
     */
    public void markSent(OffsetDateTime at) {
        requireTransition(DeliveryStatus.SENT);
        requireTime(at);
        status = DeliveryStatus.SENT;
        attempts++;
        sentAt = at;
    }

    /** Канал подтвердил доставку получателю. */
    public void markDelivered(OffsetDateTime at) {
        requireTransition(DeliveryStatus.DELIVERED);
        requireTime(at);
        if (sentAt == null || at.isBefore(sentAt)) {
            throw new IllegalArgumentException("Подтверждение доставки не может быть раньше отправки");
        }
        status = DeliveryStatus.DELIVERED;
        deliveredAt = at;
    }

    /**
     * Провал доставки. Причина обязательна: без неё очередь ошибок (OPS-04)
     * бесполезна и запись не пройдёт chk_notification_delivery_error — поэтому
     * пустая причина отвергается здесь, а не в БД.
     */
    public void markFailed(String error, OffsetDateTime at) {
        if (error == null || error.isBlank()) {
            throw new IllegalArgumentException(
                    "Провал доставки обязан нести причину (chk_notification_delivery_error)");
        }
        requireTransition(DeliveryStatus.FAILED);
        requireTime(at);
        // Попытка засчитывается ровно один раз — по факту обращения к каналу.
        // Если markSent уже был, эта же попытка уже посчитана, и асинхронный
        // отказ канала (bounce) не создаёт вторую: иначе одна отправка с отлупом
        // выглядела бы в очереди (OPS-04) как два повтора, и потолок попыток
        // исчерпывался бы вдвое быстрее реального числа обращений.
        // Инкремент нужен лишь когда до канала дойти не удалось вовсе.
        if (status == DeliveryStatus.PENDING) {
            attempts++;
        }
        status = DeliveryStatus.FAILED;
        lastError = error;
        updatedAt = at;
    }

    /**
     * Возврат в очередь для повторной отправки (NTF-06).
     *
     * <p>Отметки sent_at/delivered_at снимаются: доставка, ожидающая новой попытки,
     * не может одновременно числиться отправленной. {@code lastError} намеренно
     * сохраняется — это история того, из-за чего понадобился повтор, и она нужна
     * оператору после неудачного второго круга.
     */
    public void retry(OffsetDateTime at) {
        requireTransition(DeliveryStatus.PENDING);
        requireTime(at);
        status = DeliveryStatus.PENDING;
        sentAt = null;
        deliveredAt = null;
        updatedAt = at;
    }

    /** Имитируется ли доставка по этому каналу на demo-контуре (NotificationChannel.external()). */
    public boolean simulated() {
        return NotificationChannel.fromCode(channel)
                .map(NotificationChannel::external)
                .orElse(false);
    }

    private void requireTransition(DeliveryStatus target) {
        if (!status.canMoveTo(target)) {
            throw new IllegalStateException(
                    "Недопустимый переход доставки: " + status.code() + " -> " + target.code());
        }
    }

    private static void requireTime(OffsetDateTime at) {
        if (at == null) {
            throw new IllegalArgumentException("Время изменения доставки обязательно");
        }
    }

    public UUID getId() { return id; }
    public NotificationMessage getMessage() { return message; }
    public String getChannel() { return channel; }
    public String getRecipient() { return recipient; }
    public DeliveryStatus getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public String getLastError() { return lastError; }
    public OffsetDateTime getSentAt() { return sentAt; }
    public OffsetDateTime getDeliveredAt() { return deliveredAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
