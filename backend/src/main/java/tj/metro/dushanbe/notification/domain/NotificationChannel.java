package tj.metro.dushanbe.notification.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Канал доставки (NTF-01).
 *
 * <p>Конвертера здесь нет намеренно: канал хранится не колонкой сущности, а
 * элементом jsonb-массива {@code channels} и колонкой {@code notification_delivery.channel},
 * которую пишет сервис доставки. Enum нужен как единый словарь кодов для валидации.
 *
 * <p><b>Что реально отправляется.</b> Реального провайдера нет ни у одного канала,
 * кроме {@link #IN_APP}: push/email/SMS требуют внешних доступов, которых пока нет
 * (см. внешние блокеры в docs/implementation-status.md). Поэтому {@link #external()}
 * отделяет каналы, для которых доставка на demo-контуре только имитируется, от
 * in-app, который работает по-настоящему — фид отдаётся публичным API.
 */
public enum NotificationChannel {

    /** Лента в приложении/портале. Единственный канал, работающий без внешних доступов. */
    IN_APP,

    /** Мобильный push. Требует FCM/APNs — на demo-контуре имитируется. */
    PUSH,

    /** Электронная почта. Требует SMTP/провайдера — на demo-контуре имитируется. */
    EMAIL,

    /** SMS. Опциональный канал (NTF-01), требует агрегатора — имитируется. */
    SMS;

    /** Значение для БД и REST — нижний регистр (совпадает с chk_notification_delivery_channel). */
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Нужен ли каналу внешний провайдер.
     *
     * <p>Для таких каналов на demo-контуре доставка имитируется, и это обязано
     * быть видно оператору — молча показывать «доставлено» там, где ничего не
     * ушло, хуже, чем не иметь канала вовсе.
     */
    public boolean external() {
        return this != IN_APP;
    }

    public static Optional<NotificationChannel> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(channel -> channel.code().equals(code.toLowerCase(Locale.ROOT)))
                .findFirst();
    }

    public static List<String> codes() {
        return Arrays.stream(values()).map(NotificationChannel::code).toList();
    }
}
