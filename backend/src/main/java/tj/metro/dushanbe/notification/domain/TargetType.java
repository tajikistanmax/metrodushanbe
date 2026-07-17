package tj.metro.dushanbe.notification.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Вид адресации рассылки (NTF-02).
 *
 * <p>{@link #LINE} и {@link #STATION} ссылаются на объекты сети по стабильному коду
 * и совпадают по смыслу с таргетами service_alert — публичный фид фильтруется по
 * ним по тем же правилам (docs/dev-conventions.md §3), чтобы оператору не нужно
 * было держать в голове два разных правила адресации.
 *
 * <p>{@link #SEGMENT} и {@link #ROLE} — адресация не по географии, а по аудитории,
 * поэтому географические фильтры публичного фида их не выбирают.
 */
public enum TargetType {

    /** Вся линия: metro_line.code. */
    LINE,

    /** Отдельная станция: metro_station.code. */
    STATION,

    /** Сегмент аудитории (например, «пользователи проездных»). */
    SEGMENT,

    /** Роль в консоли: адресная рассылка для персонала. */
    ROLE;

    /** Значение для БД и REST — нижний регистр (совпадает с chk_notification_target_type). */
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Адресует ли таргет объект сети (а не аудиторию) — по таким идёт фильтр фида. */
    public boolean geographic() {
        return this == LINE || this == STATION;
    }

    public static Optional<TargetType> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(type -> type.code().equals(code.toLowerCase(Locale.ROOT)))
                .findFirst();
    }

    public static List<String> codes() {
        return Arrays.stream(values()).map(TargetType::code).toList();
    }

    /** Хранит код в нижнем регистре — @Enumerated(STRING) нарушил бы CHECK. */
    @Converter(autoApply = false)
    public static class Persistence implements AttributeConverter<TargetType, String> {

        @Override
        public String convertToDatabaseColumn(TargetType attribute) {
            return attribute == null ? null : attribute.code();
        }

        @Override
        public TargetType convertToEntityAttribute(String dbData) {
            if (dbData == null) {
                return null;
            }
            return fromCode(dbData).orElseThrow(() ->
                    new IllegalStateException(
                            "Неизвестный тип в notification_target.target_type: " + dbData));
        }
    }
}
