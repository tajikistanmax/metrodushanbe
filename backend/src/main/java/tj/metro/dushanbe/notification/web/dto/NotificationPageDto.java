package tj.metro.dushanbe.notification.web.dto;

import java.util.List;

/**
 * Страница ленты рассылок консоли (NTF-01).
 *
 * <p>Форма полей повторяет {@code ImportPageDto} буквально — как и
 * {@code NotificationDeliveryPageDto}: контракт постраничной выдачи у консоли
 * один, и третий его вариант, отличающийся только именами полей, ни клиенту,
 * ни оператору ничего не даёт.
 */
public record NotificationPageDto(List<NotificationDto> items,
                                  int page,
                                  int size,
                                  long totalElements,
                                  int totalPages) {
}
