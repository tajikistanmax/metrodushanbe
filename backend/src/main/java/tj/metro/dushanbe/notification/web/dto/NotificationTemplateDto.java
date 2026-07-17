package tj.metro.dushanbe.notification.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Заготовка текста рассылки (NTF-05). {@code title}/{@code body} — полные
 * i18n-объекты {"tg","ru","en"}; {@code name} — служебное имя для консоли.
 */
public record NotificationTemplateDto(String code,
                                      String name,
                                      String type,
                                      Map<String, String> title,
                                      Map<String, String> body,
                                      List<String> channels,
                                      boolean active,
                                      Instant updatedAt) {
}
