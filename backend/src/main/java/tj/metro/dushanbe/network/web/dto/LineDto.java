package tj.metro.dushanbe.network.web.dto;

import java.util.Map;

/**
 * Линия метрополитена в ответах API.
 * {@code name} — полный i18n-объект {"tg","ru","en"} (см. dev-conventions §3).
 */
public record LineDto(String code,
                      Map<String, String> name,
                      String colorHex,
                      String status,
                      int sortOrder) {
}
