package tj.metro.dushanbe.network.web.dto;

import java.util.List;
import java.util.Map;

/**
 * Выход станции в ответах API.
 * {@code name} — полный i18n-объект {"tg","ru","en"};
 * {@code isAccessible} — признак безбарьерного выхода;
 * {@code coordinates} — [lon, lat] (порядок осей GeoJSON, EPSG:4326).
 */
public record StationExitDto(String code,
                             Map<String, String> name,
                             boolean isAccessible,
                             List<Double> coordinates) {
}
