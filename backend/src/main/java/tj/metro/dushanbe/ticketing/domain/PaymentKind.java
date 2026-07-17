package tj.metro.dushanbe.ticketing.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * За что платёж: покупка билета или пополнение проездного (U-CIT-08).
 *
 * <p>Карты переходов нет: назначение платежа фиксируется при создании.
 *
 * <p>Различие существенно для возврата (TKT-03): возвращается покупка целиком,
 * а пополнение в demo не возвращается — правил перерасчёта уже потраченного
 * остатка нет (внешний блокер), и «возврат» пополнения означал бы отдать деньги,
 * не сняв соответствующую сумму с баланса.
 */
public enum PaymentKind {

    /** Покупка билета. */
    PURCHASE,

    /** Пополнение проездного. */
    TOPUP;

    /** Значение для БД и REST — нижний регистр (совпадает с chk_payment_kind). */
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Подлежит ли платёж возврату (TKT-03). См. javadoc класса. */
    public boolean refundable() {
        return this == PURCHASE;
    }

    public static Optional<PaymentKind> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(kind -> kind.code().equals(code.toLowerCase(Locale.ROOT)))
                .findFirst();
    }

    public static List<String> codes() {
        return Arrays.stream(values()).map(PaymentKind::code).toList();
    }

    /** Хранит код в нижнем регистре — @Enumerated(STRING) нарушил бы CHECK. */
    @Converter(autoApply = false)
    public static class Persistence implements AttributeConverter<PaymentKind, String> {

        @Override
        public String convertToDatabaseColumn(PaymentKind attribute) {
            return attribute == null ? null : attribute.code();
        }

        @Override
        public PaymentKind convertToEntityAttribute(String dbData) {
            if (dbData == null) {
                return null;
            }
            return fromCode(dbData).orElseThrow(() ->
                    new IllegalStateException("Неизвестное назначение в payment.kind: " + dbData));
        }
    }
}
