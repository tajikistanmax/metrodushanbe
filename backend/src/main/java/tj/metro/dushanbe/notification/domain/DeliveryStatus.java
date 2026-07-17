package tj.metro.dushanbe.notification.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Состояние доставки по одному каналу одному получателю (NTF-06).
 *
 * <pre>
 *   pending → sent → delivered
 *      │       │         │
 *      └───────┴─────────┴──→ failed ──→ pending   (повтор)
 * </pre>
 *
 * <p><b>Почему повтор возвращает в pending, а не сразу в sent.</b> {@code sent} —
 * это факт: «мы отдали сообщение каналу», и он обязан сопровождаться отметкой
 * {@code sent_at}. Нажатие «повторить» такого факта не создаёт — оно лишь ставит
 * доставку обратно в очередь, а отправка может снова провалиться (провайдер всё
 * ещё лежит). Перевод сразу в {@code sent} означал бы, что мы записали отправку,
 * которой не было, — и очередь ошибок (OPS-04) опустела бы не потому, что
 * доставили, а потому, что нажали кнопку. Поэтому повтор — это возврат в начало
 * цикла: pending → (реальная попытка) → sent/failed, с инкрементом {@code attempts}.
 *
 * <p><b>Почему из {@code delivered} всё же можно в {@code failed}.</b> Подтверждение
 * доставки приходит от внешнего канала асинхронно, и отбойник (bounce) может
 * прийти уже после «доставлено». Запретить этот переход — значит хранить заведомо
 * ложное «доставлено» вместо причины отказа.
 */
public enum DeliveryStatus {

    /** В очереди: попытки ещё не было либо доставка возвращена на повтор. */
    PENDING,

    /** Отдано каналу; подтверждения получателя ещё нет. */
    SENT,

    /** Канал подтвердил доставку получателю. */
    DELIVERED,

    /** Провал. Обязан нести причину — см. chk_notification_delivery_error. */
    FAILED;

    private static final Map<DeliveryStatus, Set<DeliveryStatus>> TRANSITIONS = Map.of(
            PENDING, EnumSet.of(SENT, FAILED),
            SENT, EnumSet.of(DELIVERED, FAILED),
            DELIVERED, EnumSet.of(FAILED),
            FAILED, EnumSet.of(PENDING));

    /** Значение для БД и REST — нижний регистр (совпадает с chk_notification_delivery_status). */
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Допустим ли переход в {@code target} из текущего состояния. */
    public boolean canMoveTo(DeliveryStatus target) {
        return TRANSITIONS.get(this).contains(target);
    }

    /** Куда можно перейти отсюда — для подсказки оператору и валидации. */
    public Set<DeliveryStatus> allowedTransitions() {
        return TRANSITIONS.get(this);
    }

    /** Требует ли повторной отправки: то, что висит в очереди ошибок (OPS-04). */
    public boolean retryable() {
        return this == FAILED;
    }

    public static Optional<DeliveryStatus> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(status -> status.code().equals(code.toLowerCase(Locale.ROOT)))
                .findFirst();
    }

    public static List<String> codes() {
        return Arrays.stream(values()).map(DeliveryStatus::code).toList();
    }

    /** Хранит код в нижнем регистре — @Enumerated(STRING) нарушил бы CHECK. */
    @Converter(autoApply = false)
    public static class Persistence implements AttributeConverter<DeliveryStatus, String> {

        @Override
        public String convertToDatabaseColumn(DeliveryStatus attribute) {
            return attribute == null ? null : attribute.code();
        }

        @Override
        public DeliveryStatus convertToEntityAttribute(String dbData) {
            if (dbData == null) {
                return null;
            }
            return fromCode(dbData).orElseThrow(() ->
                    new IllegalStateException(
                            "Неизвестный статус в notification_delivery.status: " + dbData));
        }
    }
}
