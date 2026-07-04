package tj.metro.dushanbe.network.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tj.metro.dushanbe.network.service.NetworkService;
import tj.metro.dushanbe.network.web.dto.GeoJsonFeatureCollection;

/**
 * Гео-слой сети. Итоговый путь с учётом context-path: /api/v1/network/geojson.
 * Схема ответа совместима с data/demo-network.geojson (офлайн-fallback для web).
 */
@RestController
@RequestMapping("/v1/network")
@Tag(name = "Network", description = "Гео-слой сети метрополитена")
public class NetworkController {

    private final NetworkService networkService;

    public NetworkController(NetworkService networkService) {
        this.networkService = networkService;
    }

    @GetMapping(value = "/geojson", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "GeoJSON всей сети",
            description = "FeatureCollection (RFC 7946): линии (LineString) и станции (Point). "
                    + "Схема свойств идентична data/demo-network.geojson: "
                    + "feature_type, code, name{tg,ru,en}, color_hex/status/lines/is_transfer/accessibility.")
    public GeoJsonFeatureCollection geojson() {
        return networkService.networkGeoJson();
    }
}
