package tj.metro.dushanbe.incident.domain;

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
 * Стадия разбора инцидента.
 *
 * <p>Переходы заданы явной картой, а не проверками в сервисе: так набор
 * допустимых действий виден в одном месте и его нельзя случайно разойтись
 * с UI, который эту же карту показывает оператору.
 *
 * <pre>
 *   open → acknowledged → in_progress → resolved → closed
 *            ↑                ↑            │
 *            └────────────────┴────────────┘   (возврат к разбору)
 * </pre>
 */
public enum IncidentStatus {

    /** Зарегистрирован, никто ещё не взял в работу. */
    OPEN,

    /** Принят к сведению, назначен ответственный. */
    ACKNOWLEDGED,

    /** Идёт устранение. */
    IN_PROGRESS,

    /** Устранён, разбор записан; ещё можно вернуть в работу. */
    RESOLVED,

    /** Закрыт окончательно. Терминальное состояние. */
    CLOSED;

    private static final Map<IncidentStatus, Set<IncidentStatus>> TRANSITIONS = Map.of(
            OPEN, EnumSet.of(ACKNOWLEDGED),
            ACKNOWLEDGED, EnumSet.of(IN_PROGRESS, OPEN),
            IN_PROGRESS, EnumSet.of(RESOLVED, ACKNOWLEDGED),
            RESOLVED, EnumSet.of(CLOSED, IN_PROGRESS),
            CLOSED, EnumSet.noneOf(IncidentStatus.class));

    /** Значение для БД и REST — нижний регистр (совпадает с chk_incident_status). */
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Допустим ли переход в {@code target} из текущего состояния. */
    public boolean canMoveTo(IncidentStatus target) {
        return TRANSITIONS.get(this).contains(target);
    }

    /** Куда можно перейти отсюда — для подсказки оператору и валидации. */
    public Set<IncidentStatus> allowedTransitions() {
        return TRANSITIONS.get(this);
    }

    /** Требует ли состояние заполненного разбора (см. chk_incident_resolution). */
    public boolean requiresResolution() {
        return this == RESOLVED || this == CLOSED;
    }

    public static Optional<IncidentStatus> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(status -> status.code().equals(code.toLowerCase(Locale.ROOT)))
                .findFirst();
    }

    public static List<String> codes() {
        return Arrays.stream(values()).map(IncidentStatus::code).toList();
    }

    /** Хранит код в нижнем регистре — @Enumerated(STRING) нарушил бы CHECK. */
    @Converter(autoApply = false)
    public static class Persistence implements AttributeConverter<IncidentStatus, String> {

        @Override
        public String convertToDatabaseColumn(IncidentStatus attribute) {
            return attribute == null ? null : attribute.code();
        }

        @Override
        public IncidentStatus convertToEntityAttribute(String dbData) {
            if (dbData == null) {
                return null;
            }
            return fromCode(dbData).orElseThrow(() ->
                    new IllegalStateException("Неизвестный статус в incident.status: " + dbData));
        }
    }
}
