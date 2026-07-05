package tj.metro.dushanbe.routing.web.dto;

import java.util.Map;

/**
 * Станция в последовательности маршрута.
 *
 * <p>{@code name} — полный i18n-объект {"tg","ru","en"}; {@code lineCode} — линия,
 * по которой едут ПОСЛЕ этой станции (для последней станции — линия прибытия);
 * {@code transfer} — {@code true}, если на этой станции выполняется пересадка на
 * следующую линию (BR-RTE-2). Пересадочная станция в списке присутствует один раз.
 */
public record RouteStopDto(String code,
                           Map<String, String> name,
                           String lineCode,
                           boolean transfer) {
}
