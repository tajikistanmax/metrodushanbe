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
 * Состояние рассылки (NTF-05).
 *
 * <p>Переходы заданы явной картой — тем же приёмом, что в {@code IncidentStatus}:
 * набор допустимых действий виден в одном месте и не расходится с UI.
 *
 * <pre>
 *   draft ⇄ scheduled → sending → sent
 *     │         │          │
 *     └─────────┴──────────┴────→ cancelled
 * </pre>
 *
 * <p><b>Почему из {@code sending} нельзя в {@code draft}.</b> Как только начата
 * доставка, часть получателей уже получила текст. Вернуть рассылку в черновик и
 * отредактировать — значит разослать двум группам разные тексты под одним кодом,
 * и разбор жалобы станет невозможен. Единственный выход из sending — довести до
 * sent или отменить остаток (cancelled), сохранив уже отправленное в истории.
 */
public enum NotificationStatus {

    /** Готовится, получателям не видна, редактируется свободно. */
    DRAFT,

    /** Запланирована на scheduled_at, ждёт своего времени. */
    SCHEDULED,

    /** Идёт доставка по каналам. Тексты уже заморожены. */
    SENDING,

    /** Доставка завершена. Терминальное состояние. */
    SENT,

    /** Отменена. Терминальное состояние. */
    CANCELLED;

    private static final Map<NotificationStatus, Set<NotificationStatus>> TRANSITIONS = Map.of(
            DRAFT, EnumSet.of(SCHEDULED, SENDING, CANCELLED),
            SCHEDULED, EnumSet.of(DRAFT, SENDING, CANCELLED),
            SENDING, EnumSet.of(SENT, CANCELLED),
            SENT, EnumSet.noneOf(NotificationStatus.class),
            CANCELLED, EnumSet.noneOf(NotificationStatus.class));

    /** Значение для БД и REST — нижний регистр (совпадает с chk_notification_message_status). */
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Допустим ли переход в {@code target} из текущего состояния. */
    public boolean canMoveTo(NotificationStatus target) {
        return TRANSITIONS.get(this).contains(target);
    }

    /** Куда можно перейти отсюда — для подсказки оператору и валидации. */
    public Set<NotificationStatus> allowedTransitions() {
        return TRANSITIONS.get(this);
    }

    /**
     * Заморожены ли тексты и адресация.
     *
     * <p>С момента начала доставки править рассылку нельзя — см. пояснение выше.
     */
    public boolean frozen() {
        return this == SENDING || this == SENT || this == CANCELLED;
    }

    public static Optional<NotificationStatus> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(status -> status.code().equals(code.toLowerCase(Locale.ROOT)))
                .findFirst();
    }

    public static List<String> codes() {
        return Arrays.stream(values()).map(NotificationStatus::code).toList();
    }

    /** Хранит код в нижнем регистре — @Enumerated(STRING) нарушил бы CHECK. */
    @Converter(autoApply = false)
    public static class Persistence implements AttributeConverter<NotificationStatus, String> {

        @Override
        public String convertToDatabaseColumn(NotificationStatus attribute) {
            return attribute == null ? null : attribute.code();
        }

        @Override
        public NotificationStatus convertToEntityAttribute(String dbData) {
            if (dbData == null) {
                return null;
            }
            return fromCode(dbData).orElseThrow(() ->
                    new IllegalStateException(
                            "Неизвестный статус в notification_message.status: " + dbData));
        }
    }
}
