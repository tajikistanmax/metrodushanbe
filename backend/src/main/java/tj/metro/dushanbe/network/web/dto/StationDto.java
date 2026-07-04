package tj.metro.dushanbe.network.web.dto;

import java.util.List;
import java.util.Map;

/**
 * Станция метрополитена в ответах API.
 * {@code name} — полный i18n-объект {"tg","ru","en"};
 * {@code lines} — коды линий, которым принадлежит станция;
 * {@code coordinates} — [lon, lat] (порядок осей GeoJSON, EPSG:4326).
 */
public record StationDto(String code,
                         Map<String, String> name,
                         String status,
                         List<String> lines,
                         boolean isTransfer,
                         List<String> accessibility,
                         List<Double> coordinates) {
}
