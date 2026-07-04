package tj.metro.dushanbe.network.web.dto;

import java.util.Map;

/**
 * GeoJSON Feature (RFC 7946). Собирается вручную, без сторонних библиотек:
 * {@code geometry} — Map вида {"type": "...", "coordinates": [...]}.
 * Порядок полей повторяет data/demo-network.geojson.
 */
public record GeoJsonFeature(String type,
                             String id,
                             Map<String, Object> properties,
                             Map<String, Object> geometry) {
}
