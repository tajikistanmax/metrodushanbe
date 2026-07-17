package tj.metro.dushanbe.integration.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Тип события сети, публикуемого наружу (INT-02, U-INT-03).
 *
 * <p>Это ПУБЛИЧНЫЙ контракт: коды уходят в теле вебхука и в заголовке
 * {@code X-Metro-Event-Type}, городской портал разбирает их у себя. Переименование
 * кода — ломающее изменение для всех подписчиков, поэтому коды фиксированы и
 * продублированы в chk_outbox_event_type: добавление типа требует и миграции,
 * и значения здесь — рассогласование поймает БД, а не подписчик.
 *
 * <p>Карты переходов у типа события нет и быть не может: событие — это факт, оно
 * не меняет состояние. Меняет состояние доставка ({@link WebhookDeliveryStatus}).
 */
public enum WebhookEventType {

    /** Опубликовано сервисное уведомление для пассажиров. */
    ALERT_PUBLISHED,

    /** Уведомление снято — ситуация в сети разрешилась. */
    ALERT_CLEARED,

    /** Зарегистрирован инцидент, затрагивающий движение. */
    INCIDENT_OPENED,

    /** Инцидент устранён. */
    INCIDENT_RESOLVED,

    /** Изменился статус станции (закрытие, ввод в эксплуатацию). */
    STATION_STATUS_CHANGED,

    /** Изменилось расписание линии, включая календарные исключения. */
    SCHEDULE_CHANGED,

    /** Поезд идёт с задержкой сверх порога (по данным телеметрии, U-INT-04). */
    TRAIN_DELAYED;

    /** Значение для БД и REST — нижний регистр (совпадает с chk_outbox_event_type). */
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<WebhookEventType> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(type -> type.code().equals(code.toLowerCase(Locale.ROOT)))
                .findFirst();
    }

    public static List<String> codes() {
        return Arrays.stream(values()).map(WebhookEventType::code).toList();
    }

    /** Хранит код в нижнем регистре — @Enumerated(STRING) нарушил бы CHECK. */
    @Converter(autoApply = false)
    public static class Persistence implements AttributeConverter<WebhookEventType, String> {

        @Override
        public String convertToDatabaseColumn(WebhookEventType attribute) {
            return attribute == null ? null : attribute.code();
        }

        @Override
        public WebhookEventType convertToEntityAttribute(String dbData) {
            if (dbData == null) {
                return null;
            }
            return fromCode(dbData).orElseThrow(() ->
                    new IllegalStateException("Неизвестный тип в outbox_event.event_type: " + dbData));
        }
    }
}
