package tj.metro.dushanbe.ticketing.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Вид билета: разовая поездка или проездной (U-CIT-08).
 *
 * <p>Карты переходов нет: вид фиксируется при покупке и не меняется — разовый
 * билет не становится проездным.
 *
 * <p><b>Почему это поле вообще есть.</b> В {@code fare_product} (V018) признака
 * типа продукта нет — есть только {@code validity_minutes}. Но валидация ведёт
 * себя по-разному: разовый гасится первым же проходом, проездной действует всё
 * окно. Значит различие нужно, и оно выводится из тарифа при покупке
 * ({@link #fromValidityMinutes}), а результат фиксируется на билете — чтобы
 * пересмотр правила вывода не переписал смысл уже выпущенных билетов.
 */
public enum TicketKind {

    /** Одна поездка: гасится при первой валидации. */
    SINGLE,

    /** Проездной: действует всё окно, пополняется (top-up), не гасится. */
    PASS;

    /**
     * Порог, отделяющий проездной от разовой поездки, — сутки.
     *
     * <p>ДЕМО-ЭВРИСТИКА. Это НЕ утверждённое бизнес-правило: в demo-справочнике
     * DEMO-SINGLE имеет 90 минут, DEMO-MONTHLY — 43200, и порог в сутки их
     * разделяет. Когда тарифы будут утверждены (внешний блокер), правильным
     * решением будет явная колонка типа продукта в fare_product, а не порог.
     */
    public static final int PASS_THRESHOLD_MINUTES = 1440;

    /** Значение для БД и REST — нижний регистр (совпадает с chk_ticket_kind). */
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Гасится ли билет при валидации (TKT-04). */
    public boolean consumedOnValidation() {
        return this == SINGLE;
    }

    /** Можно ли пополнять (U-CIT-08): разовую поездку пополнять бессмысленно. */
    public boolean topUpAllowed() {
        return this == PASS;
    }

    /** Выводит вид из срока действия тарифа. См. {@link #PASS_THRESHOLD_MINUTES}. */
    public static TicketKind fromValidityMinutes(Integer validityMinutes) {
        if (validityMinutes == null || validityMinutes < PASS_THRESHOLD_MINUTES) {
            return SINGLE;
        }
        return PASS;
    }

    public static Optional<TicketKind> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(kind -> kind.code().equals(code.toLowerCase(Locale.ROOT)))
                .findFirst();
    }

    public static List<String> codes() {
        return Arrays.stream(values()).map(TicketKind::code).toList();
    }

    /** Хранит код в нижнем регистре — @Enumerated(STRING) нарушил бы CHECK. */
    @Converter(autoApply = false)
    public static class Persistence implements AttributeConverter<TicketKind, String> {

        @Override
        public String convertToDatabaseColumn(TicketKind attribute) {
            return attribute == null ? null : attribute.code();
        }

        @Override
        public TicketKind convertToEntityAttribute(String dbData) {
            if (dbData == null) {
                return null;
            }
            return fromCode(dbData).orElseThrow(() ->
                    new IllegalStateException("Неизвестный вид в ticket.kind: " + dbData));
        }
    }
}
