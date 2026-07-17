package tj.metro.dushanbe.notification.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Тип рассылки (NTF-03).
 *
 * <p>Тип — это не то же самое, что severity алерта: он определяет, можно ли
 * получателю от рассылки отписаться. {@link #PROMO} обязан быть отключаемым,
 * {@link #INCIDENT} — нет, иначе пассажир пропустит аварию. Правило живёт в
 * {@link #optional()}, а не в сервисе рассылки, чтобы его нельзя было обойти.
 */
public enum NotificationType {

    /** Информационное сообщение общего характера. */
    INFO,

    /** Предупреждение: что-то работает не штатно, но сеть на ходу. */
    WARNING,

    /** Авария: движение нарушено, сообщение обязательно к доставке. */
    INCIDENT,

    /** Плановые работы, известные заранее. */
    MAINTENANCE,

    /** Промо и опросы — единственный тип, от которого можно отписаться. */
    PROMO;

    /** Значение для БД и REST — нижний регистр (совпадает с chk_notification_message_type). */
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Можно ли получателю отказаться от рассылок этого типа.
     *
     * <p>Отписка от аварийных и служебных сообщений недопустима: это вопрос
     * безопасности пассажира, а не удобства.
     */
    public boolean optional() {
        return this == PROMO;
    }

    public static Optional<NotificationType> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(type -> type.code().equals(code.toLowerCase(Locale.ROOT)))
                .findFirst();
    }

    public static List<String> codes() {
        return Arrays.stream(values()).map(NotificationType::code).toList();
    }

    /** Хранит код в нижнем регистре — @Enumerated(STRING) нарушил бы CHECK. */
    @Converter(autoApply = false)
    public static class Persistence implements AttributeConverter<NotificationType, String> {

        @Override
        public String convertToDatabaseColumn(NotificationType attribute) {
            return attribute == null ? null : attribute.code();
        }

        @Override
        public NotificationType convertToEntityAttribute(String dbData) {
            if (dbData == null) {
                return null;
            }
            return fromCode(dbData).orElseThrow(() ->
                    new IllegalStateException("Неизвестный тип в notification_message.type: " + dbData));
        }
    }
}
