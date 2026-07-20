package tj.metro.dushanbe.identity.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Область счётчика неудачных входов (аудит-пункт 9).
 *
 * <p>Счётчиков ровно два, и они отвечают на разные атаки:
 * <ul>
 *   <li>{@link #ACCOUNT} — подбор пароля одной учётной записи с многих адресов
 *       (ботнет, распределённый credential stuffing). Порог низкий: пять неудач.</li>
 *   <li>{@link #IP_USER} — быстрый перебор с одного адреса. Порог выше, потому что
 *       здесь чаще ошибается живой оператор с включённым CapsLock, но окно то же.</li>
 * </ul>
 *
 * <p>Почему не хватило общего {@code app.rate-limit}: тот считает ЛЮБЫЕ запросы по
 * IP, не различает успешные и неудачные, ничего не знает о логине и живёт в памяти
 * процесса — то есть перезапуск обнуляет всю историю подбора.
 */
public enum LoginAttemptScope {

    /** Ключ счётчика — логин. */
    ACCOUNT,

    /** Ключ счётчика — «ip|логин». */
    IP_USER;

    /** Значение для БД — нижний регистр (совпадает с chk_admin_login_attempt_scope). */
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<LoginAttemptScope> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(scope -> scope.code().equals(code.toLowerCase(Locale.ROOT)))
                .findFirst();
    }

    public static List<String> codes() {
        return Arrays.stream(values()).map(LoginAttemptScope::code).toList();
    }

    /** Хранит код в нижнем регистре — @Enumerated(STRING) нарушил бы CHECK. */
    @Converter(autoApply = false)
    public static class Persistence implements AttributeConverter<LoginAttemptScope, String> {

        @Override
        public String convertToDatabaseColumn(LoginAttemptScope attribute) {
            return attribute == null ? null : attribute.code();
        }

        @Override
        public LoginAttemptScope convertToEntityAttribute(String dbData) {
            if (dbData == null) {
                return null;
            }
            return fromCode(dbData).orElseThrow(() ->
                    new IllegalStateException(
                            "Неизвестная область в admin_login_attempt.scope: " + dbData));
        }
    }
}
