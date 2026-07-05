package tj.metro.dushanbe.routing.web.dto;

import java.util.List;
import java.util.Map;

/**
 * Участок маршрута в пределах одной линии (без пересадок внутри участка).
 * Границы участков — пересадочные узлы.
 *
 * <p>{@code lineName} — полный i18n-объект {"tg","ru","en"}; {@code colorHex} — цвет
 * линии (для отрисовки); {@code stations} — коды станций участка по порядку следования,
 * включая станцию посадки и станцию высадки/пересадки; {@code segmentCount} — число
 * перегонов участка ({@code stations.size() - 1}); {@code estimatedMinutes} —
 * оценочное время участка (только перегоны, без штрафа за пересадку).
 */
public record RouteLegDto(String lineCode,
                          Map<String, String> lineName,
                          String colorHex,
                          List<String> stations,
                          int segmentCount,
                          int estimatedMinutes) {
}
