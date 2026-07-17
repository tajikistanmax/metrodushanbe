package tj.metro.dushanbe.ticketing.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Что именно заблокировано в чёрном списке (TKT-06).
 *
 * <p>Карты переходов здесь нет намеренно, в отличие от {@code TicketStatus} и
 * соседей: это не состояние, а вид субъекта. Запись чёрного списка не меняет
 * свой предмет — заблокированный токен не «переходит» в заблокированного
 * покупателя. Снятие блокировки — удаление записи, а не переход.
 *
 * <p>Три уровня нужны потому, что закрывают разные сценарии:
 * <ul>
 *   <li>{@link #TICKET} — скомпрометирован конкретный документ;
 *   <li>{@link #TOKEN} — утёк конкретный QR (турникет прислал подозрительный
 *       токен, билет по нему может быть ещё не найден);
 *   <li>{@link #RIDER} — недобросовестный покупатель: блокируются и уже
 *       выпущенные билеты, и новые покупки.
 * </ul>
 */
public enum BlocklistSubjectType {

    /** Субъект — ticket.code. */
    TICKET,

    /** Субъект — SHA-256 токена QR. Сам токен нигде не хранится (см. V023). */
    TOKEN,

    /** Субъект — ticket.rider_ref: идентификатор покупателя. */
    RIDER;

    /** Значение для БД и REST — нижний регистр (совпадает с chk_ticket_blocklist_subject_type). */
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Требует ли значение хеширования перед записью в чёрный список.
     *
     * <p>Оператор вводит токен как есть (его прислал турникет), но хранить токен
     * открытым нельзя — сервис заменяет его на SHA-256.
     */
    public boolean hashed() {
        return this == TOKEN;
    }

    public static Optional<BlocklistSubjectType> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(type -> type.code().equals(code.toLowerCase(Locale.ROOT)))
                .findFirst();
    }

    public static List<String> codes() {
        return Arrays.stream(values()).map(BlocklistSubjectType::code).toList();
    }

    /** Хранит код в нижнем регистре — @Enumerated(STRING) нарушил бы CHECK. */
    @Converter(autoApply = false)
    public static class Persistence implements AttributeConverter<BlocklistSubjectType, String> {

        @Override
        public String convertToDatabaseColumn(BlocklistSubjectType attribute) {
            return attribute == null ? null : attribute.code();
        }

        @Override
        public BlocklistSubjectType convertToEntityAttribute(String dbData) {
            if (dbData == null) {
                return null;
            }
            return fromCode(dbData).orElseThrow(() -> new IllegalStateException(
                    "Неизвестный тип в ticket_blocklist.subject_type: " + dbData));
        }
    }
}
