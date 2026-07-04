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
import tj.metro.dushanbe.network.web.dto.LineDto;

/**
 * Линии метрополитена. Итоговые пути с учётом context-path: /api/v1/lines...
 * Контракт — docs/dev-conventions.md §3.
 */
@RestController
@RequestMapping("/v1/lines")
@Tag(name = "Lines", description = "Линии метрополитена")
public class LineController {

    private final NetworkService networkService;

    public LineController(NetworkService networkService) {
        this.networkService = networkService;
    }

    @GetMapping
    @Operation(summary = "Список линий",
            description = "Возвращает все линии, опционально отфильтрованные по статусу. "
                    + "Поле name — полный i18n-объект {tg, ru, en}.")
    public List<LineDto> list(
            @Parameter(description = "Фильтр по статусу: planned|under_construction|testing|active|suspended|decommissioned")
            @RequestParam(name = "status", required = false) String status) {
        return networkService.lines(status);
    }

    @GetMapping("/{code}")
    @Operation(summary = "Карточка линии",
            description = "Возвращает линию по стабильному коду (например, L1). 404 — line.not_found.")
    public LineDto byCode(@PathVariable("code") String code) {
        return networkService.lineByCode(code);
    }
}
