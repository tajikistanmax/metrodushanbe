package tj.metro.dushanbe.ticketing.domain;

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
 * Состояние возврата (TKT-03).
 *
 * <pre>
 *   pending → completed
 *      └────→ failed
 * </pre>
 *
 * <p>Провал возврата — отдельное состояние, а не удаление записи: попытка вернуть
 * деньги обязана остаться в истории, даже если провайдер её отклонил. Иначе
 * пассажир, которому возврат не дошёл, не сможет ничего доказать, а оператор —
 * ничего найти.
 */
public enum RefundStatus {

    /** Создан, ответ провайдера ещё не получен. */
    PENDING,

    /** Средства возвращены. Терминальное состояние. */
    COMPLETED,

    /** Провайдер отклонил возврат. Терминальное; причина обязательна (chk_refund_error). */
    FAILED;

    private static final Map<RefundStatus, Set<RefundStatus>> TRANSITIONS = Map.of(
            PENDING, EnumSet.of(COMPLETED, FAILED),
            COMPLETED, EnumSet.noneOf(RefundStatus.class),
            FAILED, EnumSet.noneOf(RefundStatus.class));

    /** Значение для БД и REST — нижний регистр (совпадает с chk_refund_status). */
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Допустим ли переход в {@code target} из текущего состояния. */
    public boolean canMoveTo(RefundStatus target) {
        return TRANSITIONS.get(this).contains(target);
    }

    /** Куда можно перейти отсюда — для подсказки оператору и валидации. */
    public Set<RefundStatus> allowedTransitions() {
        return TRANSITIONS.get(this);
    }

    /** Состояние окончательное: изменений больше не будет. */
    public boolean terminal() {
        return TRANSITIONS.get(this).isEmpty();
    }

    public static Optional<RefundStatus> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(status -> status.code().equals(code.toLowerCase(Locale.ROOT)))
                .findFirst();
    }

    public static List<String> codes() {
        return Arrays.stream(values()).map(RefundStatus::code).toList();
    }

    /** Хранит код в нижнем регистре — @Enumerated(STRING) нарушил бы CHECK. */
    @Converter(autoApply = false)
    public static class Persistence implements AttributeConverter<RefundStatus, String> {

        @Override
        public String convertToDatabaseColumn(RefundStatus attribute) {
            return attribute == null ? null : attribute.code();
        }

        @Override
        public RefundStatus convertToEntityAttribute(String dbData) {
            if (dbData == null) {
                return null;
            }
            return fromCode(dbData).orElseThrow(() ->
                    new IllegalStateException("Неизвестный статус в refund.status: " + dbData));
        }
    }
}
