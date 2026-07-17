package tj.metro.dushanbe.incident.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Критичность инцидента.
 *
 * <p>Отдельно от {@code service_alert.severity} (info|warning|critical): та шкала
 * отвечает на вопрос «как громко сказать пассажиру», эта — «насколько срочно
 * реагировать оператору». Смешивать их нельзя: рядовая техническая неисправность
 * бывает высокоприоритетной внутри и незаметной снаружи.
 */
public enum IncidentSeverity {

    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    /** Значение для БД и REST (совпадает с chk_incident_severity). */
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<IncidentSeverity> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(severity -> severity.code().equals(code.toLowerCase(Locale.ROOT)))
                .findFirst();
    }

    public static List<String> codes() {
        return Arrays.stream(values()).map(IncidentSeverity::code).toList();
    }

    @Converter(autoApply = false)
    public static class Persistence implements AttributeConverter<IncidentSeverity, String> {

        @Override
        public String convertToDatabaseColumn(IncidentSeverity attribute) {
            return attribute == null ? null : attribute.code();
        }

        @Override
        public IncidentSeverity convertToEntityAttribute(String dbData) {
            if (dbData == null) {
                return null;
            }
            return fromCode(dbData).orElseThrow(() ->
                    new IllegalStateException("Неизвестная критичность в incident.severity: " + dbData));
        }
    }
}
