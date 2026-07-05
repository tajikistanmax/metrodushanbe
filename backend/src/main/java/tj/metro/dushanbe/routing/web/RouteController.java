package tj.metro.dushanbe.routing.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tj.metro.dushanbe.routing.service.RoutingService;
import tj.metro.dushanbe.routing.web.dto.RouteDto;

/**
 * Маршрутизация (ТЗ §6.2.3). Итоговый путь с учётом context-path: /api/v1/routes.
 * Контракт ошибок — docs/dev-conventions.md §3 (единый envelope).
 */
@RestController
@RequestMapping("/v1/routes")
@Tag(name = "Routing", description = "Поиск маршрута станция→станция")
public class RouteController {

    private final RoutingService routingService;

    public RouteController(RoutingService routingService) {
        this.routingService = routingService;
    }

    @GetMapping
    @Operation(summary = "Маршрут станция→станция",
            description = "Строит кратчайший маршрут (RTE-01) между станциями по их стабильным кодам "
                    + "(from/to, например ST-L1-01 и ST-L2-06). Возвращает участки по линиям (legs), "
                    + "последовательность станций (stops), число пересадок и оценочное время в пути "
                    + "(RTE-05; оценка-заглушка до реального расписания). Ранжирование по "
                    + "(время, пересадки) (BR-RTE-2); закрытые станции исключаются (BR-RTE-1). "
                    + "Несуществующий код станции → 404 route.station_not_found; нет пути → found=false.")
    public RouteDto route(
            @Parameter(description = "Код станции отправления (например ST-L1-01)", required = true)
            @RequestParam(name = "from") String from,
            @Parameter(description = "Код станции назначения (например ST-L2-06)", required = true)
            @RequestParam(name = "to") String to) {
        return routingService.route(from, to);
    }
}
