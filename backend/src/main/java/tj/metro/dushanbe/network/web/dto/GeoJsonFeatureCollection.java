package tj.metro.dushanbe.network.web.dto;

import java.util.List;
import java.util.Map;

/**
 * GeoJSON FeatureCollection (RFC 7946). {@code metadata} — foreign member,
 * схемно совместимый с data/demo-network.geojson.
 */
public record GeoJsonFeatureCollection(String type,
                                       Map<String, Object> metadata,
                                       List<GeoJsonFeature> features) {
}
