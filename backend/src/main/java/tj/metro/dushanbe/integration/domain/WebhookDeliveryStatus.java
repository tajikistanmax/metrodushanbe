package tj.metro.dushanbe.integration.domain;

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
 * Состояние доставки вебхука подписчику (INT-05).
 *
 * <p>Имя намеренно длиннее очевидного {@code DeliveryStatus}: короткое уже занято
 * модулем notification, и два одноимённых enum-а в одном проекте — гарантированная
 * путаница в импортах и в чтении стектрейсов.
 *
 * <p>Переходы заданы явной картой — тем же приёмом, что в {@code IncidentStatus}.
 *
 * <pre>
 *   pending ──→ sent
 *      │
 *      └──→ failed ──→ dead        (retry исчерпан)
 *             │  ↑        │
 *             │  └────────┘        (диспетчер пробует снова)
 *             └───────────┴──→ pending   (ручной повтор оператора, U-OPS-04)
 * </pre>
 *
 * <p><b>Почему {@code dead} — это DLQ, а не отдельная таблица.</b> Мёртвая
 * доставка — та же самая доставка, у которой кончились попытки: у неё тот же код,
 * тот же счётчик attempts и та же причина ошибки. Переносить её в другую таблицу
 * значило бы разорвать историю попыток надвое и заставить очередь ошибок читать
 * UNION. Подробнее — в шапке V024__integrations.sql.
 *
 * <p><b>Почему из {@code sent} нет выхода.</b> Подписчик подтвердил приём. Любой
 * повтор после этого — новая доставка нового события, а не воскрешение старой:
 * иначе счётчик attempts перестанет значить «сколько раз мы побеспокоили
 * подписчика этим событием», а разбор двойной доставки станет невозможен.
 */
public enum WebhookDeliveryStatus {

    /** Ждёт первой отправки или возвращена в очередь вручную. */
    PENDING,

    /** Короткая lease-заявка конкретного worker на сетевую попытку. */
    PROCESSING,

    /** Подписчик ответил 2xx. Терминальное состояние. */
    SENT,

    /** Попытка провалилась, retry ещё остался — ждёт next_attempt_at. */
    FAILED,

    /** Retry исчерпан (DLQ). Дальше — только ручной повтор оператора. */
    DEAD;

    private static final Map<WebhookDeliveryStatus, Set<WebhookDeliveryStatus>> TRANSITIONS = Map.of(
            PENDING, EnumSet.of(PROCESSING),
            PROCESSING, EnumSet.of(PENDING, SENT, FAILED, DEAD),
            SENT, EnumSet.noneOf(WebhookDeliveryStatus.class),
            FAILED, EnumSet.of(PENDING, PROCESSING),
            DEAD, EnumSet.of(PENDING));

    /** Статусы, которые забирает диспетчер: «эту доставку ещё надо довезти». */
    public static final List<WebhookDeliveryStatus> DUE = List.of(PENDING, FAILED);

    /** Статусы операторской очереди ошибок (U-OPS-04). */
    public static final List<WebhookDeliveryStatus> FAILURES = List.of(FAILED, DEAD);

    /** Значение для БД и REST — нижний регистр (совпадает с chk_webhook_delivery_status). */
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Допустим ли переход в {@code target} из текущего состояния. */
    public boolean canMoveTo(WebhookDeliveryStatus target) {
        return TRANSITIONS.get(this).contains(target);
    }

    /** Куда можно перейти отсюда — для подсказки оператору и валидации. */
    public Set<WebhookDeliveryStatus> allowedTransitions() {
        return TRANSITIONS.get(this);
    }

    /** Требует ли состояние заполненной причины (см. chk_webhook_delivery_error). */
    public boolean requiresError() {
        return this == FAILED || this == DEAD;
    }

    /** Можно ли вернуть доставку в очередь вручную (U-OPS-04). */
    public boolean retryable() {
        return canMoveTo(PENDING);
    }

    public static Optional<WebhookDeliveryStatus> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(status -> status.code().equals(code.toLowerCase(Locale.ROOT)))
                .findFirst();
    }

    public static List<String> codes() {
        return Arrays.stream(values()).map(WebhookDeliveryStatus::code).toList();
    }

    /** Хранит код в нижнем регистре — @Enumerated(STRING) нарушил бы CHECK. */
    @Converter(autoApply = false)
    public static class Persistence implements AttributeConverter<WebhookDeliveryStatus, String> {

        @Override
        public String convertToDatabaseColumn(WebhookDeliveryStatus attribute) {
            return attribute == null ? null : attribute.code();
        }

        @Override
        public WebhookDeliveryStatus convertToEntityAttribute(String dbData) {
            if (dbData == null) {
                return null;
            }
            return fromCode(dbData).orElseThrow(() ->
                    new IllegalStateException("Неизвестный статус в webhook_delivery.status: " + dbData));
        }
    }
}
