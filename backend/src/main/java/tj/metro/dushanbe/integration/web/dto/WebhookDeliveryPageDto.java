package tj.metro.dushanbe.integration.web.dto;

import java.util.List;

/**
 * Страница очереди доставок вебхуков (U-OPS-04).
 *
 * <p>Форма полей повторяет {@code ImportPageDto} буквально — см. соображения в
 * {@code NotificationDeliveryPageDto}. Очередь растёт как события × подписчики,
 * и полная выдача на проде означала бы вычитывание всей таблицы на каждый заход
 * оператора в раздел.
 */
public record WebhookDeliveryPageDto(List<WebhookDeliveryDto> items,
                                     int page,
                                     int size,
                                     long totalElements,
                                     int totalPages) {
}
