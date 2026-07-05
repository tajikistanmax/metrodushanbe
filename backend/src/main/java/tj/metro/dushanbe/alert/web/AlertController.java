package tj.metro.dushanbe.alert.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tj.metro.dushanbe.alert.service.AlertService;
import tj.metro.dushanbe.alert.web.dto.AlertDto;

/**
 * Сервисные уведомления. Итоговый путь с учётом context-path: /api/v1/alerts.
 * Контракт — docs/dev-conventions.md §3, ТЗ §6.2.6.
 */
@RestController
@RequestMapping("/v1/alerts")
@Tag(name = "Alerts", description = "Сервисные уведомления и инциденты")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping
    @Operation(summary = "Активные сервисные уведомления",
            description = "Возвращает только published-уведомления в окне действия "
                    + "(starts_at <= now < ends_at; ends_at null = бессрочно), отсортированные "
                    + "по severity (critical, warning, info) и starts_at по убыванию. "
                    + "Пустой массив targets = уведомление на всю сеть. "
                    + "Поля title/body — полные i18n-объекты {tg, ru, en}.")
    public List<AlertDto> list(
            @Parameter(description = "Фильтр по линии (стабильный код, например L2): уведомления, "
                    + "таргетированные этой линией (станционные таргеты линию не расширяют); "
                    + "network-wide уведомления включаются всегда. Вместе со stationCode — "
                    + "объединение: уведомление попадает, если проходит хотя бы один фильтр")
            @RequestParam(name = "lineCode", required = false) String lineCode,
            @Parameter(description = "Фильтр по станции (стабильный код, например ST-HUB-CENTER): "
                    + "уведомления, таргетированные этой станцией ИЛИ любой линией, которой станция "
                    + "принадлежит; network-wide уведомления включаются всегда. Вместе с lineCode — "
                    + "объединение: уведомление попадает, если проходит хотя бы один фильтр")
            @RequestParam(name = "stationCode", required = false) String stationCode,
            @Parameter(description = "Фильтр по важности: info|warning|critical. Иное значение — 400 alert.severity_invalid")
            @RequestParam(name = "severity", required = false) String severity) {
        return alertService.activeAlerts(lineCode, stationCode, severity);
    }
}
