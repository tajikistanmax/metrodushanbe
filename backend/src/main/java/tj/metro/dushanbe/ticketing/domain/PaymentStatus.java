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
 * Состояние платежа (TKT-03/05).
 *
 * <pre>
 *   pending → authorized → captured → refunded
 *      │           │
 *      └───────────┴──────→ failed
 * </pre>
 *
 * <p>Двухшаговость (authorize → capture) сохранена намеренно, хотя demo-провайдер
 * выполняет оба шага подряд: боевой эквайринг работает именно так, и если сейчас
 * схлопнуть шаги в один «paid», то при подключении реального провайдера придётся
 * переписывать и модель, и уже накопленную историю.
 *
 * <p><b>Почему из {@code captured} нельзя в {@code failed}.</b> Захваченный
 * платёж — это списанные деньги. «Провалить» его задним числом значит потерять
 * след списания; единственный законный выход — возврат (refunded), оставляющий
 * в истории оба факта: и списание, и возврат.
 *
 * <p><b>Почему {@code refunded} терминален.</b> Частичных и повторных возвратов
 * в demo нет (правила перерасчёта не утверждены), поэтому возвращённый платёж
 * закрыт окончательно — иначе одну покупку можно было бы вернуть дважды.
 */
public enum PaymentStatus {

    /** Создан, ответ провайдера ещё не получен. */
    PENDING,

    /** Провайдер подтвердил средства, списание ещё не выполнено. */
    AUTHORIZED,

    /** Средства списаны. Билет выпускается только после этого состояния. */
    CAPTURED,

    /** Отклонён. Терминальное состояние; причина обязательна (chk_payment_error). */
    FAILED,

    /** Возвращён (TKT-03). Терминальное состояние. */
    REFUNDED;

    private static final Map<PaymentStatus, Set<PaymentStatus>> TRANSITIONS = Map.of(
            PENDING, EnumSet.of(AUTHORIZED, FAILED),
            AUTHORIZED, EnumSet.of(CAPTURED, FAILED),
            CAPTURED, EnumSet.of(REFUNDED),
            FAILED, EnumSet.noneOf(PaymentStatus.class),
            REFUNDED, EnumSet.noneOf(PaymentStatus.class));

    /** Значение для БД и REST — нижний регистр (совпадает с chk_payment_status). */
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Допустим ли переход в {@code target} из текущего состояния. */
    public boolean canMoveTo(PaymentStatus target) {
        return TRANSITIONS.get(this).contains(target);
    }

    /** Куда можно перейти отсюда — для подсказки оператору и валидации. */
    public Set<PaymentStatus> allowedTransitions() {
        return TRANSITIONS.get(this);
    }

    /** Состояние окончательное: изменений больше не будет. */
    public boolean terminal() {
        return TRANSITIONS.get(this).isEmpty();
    }

    /** Можно ли по этому платежу оформить возврат (TKT-03). */
    public boolean refundable() {
        return this == CAPTURED;
    }

    public static Optional<PaymentStatus> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(status -> status.code().equals(code.toLowerCase(Locale.ROOT)))
                .findFirst();
    }

    public static List<String> codes() {
        return Arrays.stream(values()).map(PaymentStatus::code).toList();
    }

    /** Хранит код в нижнем регистре — @Enumerated(STRING) нарушил бы CHECK. */
    @Converter(autoApply = false)
    public static class Persistence implements AttributeConverter<PaymentStatus, String> {

        @Override
        public String convertToDatabaseColumn(PaymentStatus attribute) {
            return attribute == null ? null : attribute.code();
        }

        @Override
        public PaymentStatus convertToEntityAttribute(String dbData) {
            if (dbData == null) {
                return null;
            }
            return fromCode(dbData).orElseThrow(() ->
                    new IllegalStateException("Неизвестный статус в payment.status: " + dbData));
        }
    }
}
