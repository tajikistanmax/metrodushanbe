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
 * Состояние выпущенного билета (TKT-03/04/06).
 *
 * <p>Переходы заданы явной картой — тем же приёмом, что в {@code IncidentStatus}
 * и {@code NotificationStatus}: набор допустимых действий виден в одном месте.
 *
 * <pre>
 *   issued → active → used
 *     │        │       │
 *     ├────────┴───────┴──→ refunded   (TKT-03)
 *     ├────────┴───────┴──→ blocked    (TKT-06)
 *     └────────┴──────────→ expired
 * </pre>
 *
 * <p><b>Почему {@code used} не возвращается в {@code active}.</b> Погашение —
 * это факт прохода через турникет, уже случившийся в физическом мире. Вернуть
 * билет в active значило бы разрешить повторный проход по одному разовому
 * билету: ровно та дыра, ради закрытия которой валидация вообще существует.
 * Ошибочное погашение исправляется возвратом (refunded) и выпуском нового
 * билета, а не откатом состояния.
 *
 * <p><b>Почему {@code blocked} терминален.</b> Билет блокируют, когда его токен
 * скомпрометирован или покупка признана мошеннической. Токен уже утёк — снятие
 * блокировки вернуло бы в оборот именно тот QR, который у злоумышленника на
 * руках. Добросовестному пассажиру выпускается новый билет; снятие записи из
 * ticket_blocklist разблокирует токен/покупателя, но не воскрешает билет.
 *
 * <p><b>Почему {@code expired} терминален и не возвращается.</b> Истёкший срок —
 * это оказанная услуга (доступ был доступен всё окно), а не отказ в ней.
 * Продление проездного делается пополнением (top-up) ДО истечения.
 */
public enum TicketStatus {

    /** Оплачен и выпущен, но ещё ни разу не предъявлен. */
    ISSUED,

    /** Предъявлен хотя бы раз и действует (проездной в своём окне). */
    ACTIVE,

    /** Погашен: разовая поездка использована. Повторно не валидируется. */
    USED,

    /** Срок действия вышел. Терминальное состояние. */
    EXPIRED,

    /** Деньги возвращены (TKT-03). Терминальное состояние. */
    REFUNDED,

    /** Заблокирован антифродом или оператором (TKT-06). Терминальное состояние. */
    BLOCKED;

    private static final Map<TicketStatus, Set<TicketStatus>> TRANSITIONS = Map.of(
            ISSUED, EnumSet.of(ACTIVE, USED, EXPIRED, REFUNDED, BLOCKED),
            ACTIVE, EnumSet.of(USED, EXPIRED, REFUNDED, BLOCKED),
            USED, EnumSet.of(REFUNDED, BLOCKED),
            EXPIRED, EnumSet.noneOf(TicketStatus.class),
            REFUNDED, EnumSet.noneOf(TicketStatus.class),
            BLOCKED, EnumSet.noneOf(TicketStatus.class));

    /** Значение для БД и REST — нижний регистр (совпадает с chk_ticket_status). */
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Допустим ли переход в {@code target} из текущего состояния. */
    public boolean canMoveTo(TicketStatus target) {
        return TRANSITIONS.get(this).contains(target);
    }

    /** Куда можно перейти отсюда — для подсказки оператору и валидации. */
    public Set<TicketStatus> allowedTransitions() {
        return TRANSITIONS.get(this);
    }

    /** Состояние окончательное: изменений больше не будет. */
    public boolean terminal() {
        return TRANSITIONS.get(this).isEmpty();
    }

    /**
     * Может ли билет в этом состоянии быть предъявлен на турникете.
     *
     * <p>Это только проверка СОСТОЯНИЯ. Срок действия и чёрный список
     * проверяются отдельно в {@code TicketingService.validate} — состояние о них
     * ничего не знает.
     */
    public boolean validatable() {
        return this == ISSUED || this == ACTIVE;
    }

    public static Optional<TicketStatus> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(status -> status.code().equals(code.toLowerCase(Locale.ROOT)))
                .findFirst();
    }

    public static List<String> codes() {
        return Arrays.stream(values()).map(TicketStatus::code).toList();
    }

    /** Хранит код в нижнем регистре — @Enumerated(STRING) нарушил бы CHECK. */
    @Converter(autoApply = false)
    public static class Persistence implements AttributeConverter<TicketStatus, String> {

        @Override
        public String convertToDatabaseColumn(TicketStatus attribute) {
            return attribute == null ? null : attribute.code();
        }

        @Override
        public TicketStatus convertToEntityAttribute(String dbData) {
            if (dbData == null) {
                return null;
            }
            return fromCode(dbData).orElseThrow(() ->
                    new IllegalStateException("Неизвестный статус в ticket.status: " + dbData));
        }
    }
}
