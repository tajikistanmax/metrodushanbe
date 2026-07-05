package tj.metro.dushanbe.network.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tj.metro.dushanbe.network.service.NetworkService;
import tj.metro.dushanbe.network.web.dto.StationDetailDto;
import tj.metro.dushanbe.network.web.dto.StationDto;

/**
 * Станции метрополитена. Итоговые пути с учётом context-path: /api/v1/stations...
 * Контракт — docs/dev-conventions.md §3.
 */
@RestController
@RequestMapping("/v1/stations")
@Tag(name = "Stations", description = "Станции метрополитена")
public class StationController {

    private final NetworkService networkService;

    public StationController(NetworkService networkService) {
        this.networkService = networkService;
    }

    @GetMapping
    @Operation(summary = "Список станций",
            description = "Возвращает станции с фильтрами по линии (?lineCode=) и статусу (?status=). "
                    + "При фильтре по линии станции идут в порядке следования вдоль неё. "
                    + "Поле name — полный i18n-объект {tg, ru, en}; coordinates — [lon, lat].")
    public List<StationDto> list(
            @Parameter(description = "Фильтр по коду линии, например L1 (404 line.not_found, если линии нет)")
            @RequestParam(name = "lineCode", required = false) String lineCode,
            @Parameter(description = "Фильтр по статусу: planned|under_construction|testing|active|temporarily_closed|decommissioned")
            @RequestParam(name = "status", required = false) String status) {
        return networkService.stations(lineCode, status);
    }

    @GetMapping("/{code}")
    @Operation(summary = "Детальная карточка станции",
            description = "Возвращает детальную карточку станции по стабильному коду (например, ST-L1-01): "
                    + "поля станции (name — i18n-объект, coordinates — [lon, lat]) плюс выходы (exits[]) "
                    + "и объекты доступности (accessibilityFeatures[]). 404 — station.not_found.")
    public StationDetailDto byCode(@PathVariable("code") String code) {
        return networkService.stationDetailByCode(code);
    }
}
