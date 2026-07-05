package tj.metro.dushanbe.network.web.dto;

import java.util.List;
import java.util.Map;

/**
 * Детальная карточка станции в ответах API (NET-02/NET-03).
 * Надмножество {@link StationDto}: сохраняет все поля списочной карточки
 * (backward-compatible для GET /api/v1/stations/&#123;code&#125;) и добавляет
 * структурированные {@code exits} и {@code accessibilityFeatures}.
 *
 * <p>{@code name} — полный i18n-объект {"tg","ru","en"};
 * {@code lines} — коды линий станции в порядке sort_order линии;
 * {@code coordinates} — [lon, lat] (порядок осей GeoJSON, EPSG:4326);
 * {@code accessibility} — краткие теги доступности (master data станции);
 * {@code exits} — выходы станции; {@code accessibilityFeatures} — объекты
 * доступности с типом, описанием и статусом работоспособности.
 */
public record StationDetailDto(String code,
                               Map<String, String> name,
                               String status,
                               List<String> lines,
                               boolean isTransfer,
                               List<String> accessibility,
                               List<Double> coordinates,
                               List<StationExitDto> exits,
                               List<AccessibilityFeatureDto> accessibilityFeatures) {
}
