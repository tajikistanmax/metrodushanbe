package tj.metro.dushanbe.notification.web.dto;

import java.util.List;

/**
 * Страница очереди доставок (NTF-06, OPS-04).
 *
 * <p>Форма полей повторяет {@code ImportPageDto} буквально: у консоли уже есть
 * один контракт постраничной выдачи, и второй, отличающийся только именами
 * полей, оператору и клиенту ничего не даёт.
 *
 * <p>Очередь доставок пагинируется не «на вырост»: она растёт как рассылки ×
 * получатели × каналы, то есть быстрее всего остального в системе.
 */
public record NotificationDeliveryPageDto(List<NotificationDeliveryDto> items,
                                          int page,
                                          int size,
                                          long totalElements,
                                          int totalPages) {
}
