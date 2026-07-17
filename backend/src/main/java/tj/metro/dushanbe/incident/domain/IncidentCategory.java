package tj.metro.dushanbe.incident.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Категория инцидента (разрезы дашборда: безопасность / технические / прочее). */
public enum IncidentCategory {

    /** Угроза жизни и здоровью, правопорядок. */
    SAFETY,

    /** Отказ техники: подвижной состав, эскалаторы, сигнализация, энергоснабжение. */
    TECHNICAL,

    /** Происшествие с пассажиром: травма, потеря вещей, конфликт. */
    PASSENGER,

    /** Инфраструктура станций и тоннелей: протечки, отделка, конструкции. */
    INFRASTRUCTURE,

    OTHER;

    /** Значение для БД и REST (совпадает с chk_incident_category). */
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<IncidentCategory> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(category -> category.code().equals(code.toLowerCase(Locale.ROOT)))
                .findFirst();
    }

    public static List<String> codes() {
        return Arrays.stream(values()).map(IncidentCategory::code).toList();
    }

    @Converter(autoApply = false)
    public static class Persistence implements AttributeConverter<IncidentCategory, String> {

        @Override
        public String convertToDatabaseColumn(IncidentCategory attribute) {
            return attribute == null ? null : attribute.code();
        }

        @Override
        public IncidentCategory convertToEntityAttribute(String dbData) {
            if (dbData == null) {
                return null;
            }
            return fromCode(dbData).orElseThrow(() ->
                    new IllegalStateException("Неизвестная категория в incident.category: " + dbData));
        }
    }
}
