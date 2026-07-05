package tj.metro.dushanbe.schedule.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tj.metro.dushanbe.schedule.service.ScheduleService;
import tj.metro.dushanbe.schedule.web.dto.LineScheduleDto;
import tj.metro.dushanbe.schedule.web.dto.StationArrivalsDto;

/**
 * Статическое расписание (ТЗ §6.2.4). Итоговые пути с учётом context-path:
 * /api/v1/lines/{code}/schedule и /api/v1/stations/{code}/arrivals.
 * Контракт — docs/dev-conventions.md §3.
 *
 * <p>Прибытия — ОЦЕНОЧНЫЕ (headway-based), до появления realtime (SCH-03/08).
 */
@RestController
@Tag(name = "Schedule", description = "Расписание движения и оценочные прибытия")
public class ScheduleController {

    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @GetMapping("/v1/lines/{code}/schedule")
    @Operation(summary = "График движения линии",
            description = "Возвращает график движения линии для типа дня (SCH-01): первое/последнее "
                    + "отправление (HH:mm) и интервал движения (headway). dayType опционален — "
                    + "по умолчанию выводится из текущего дня недели (сб/вс → weekend, иначе weekday). "
                    + "404 — line.not_found (нет линии) или schedule.not_found (нет графика на этот день).")
    public LineScheduleDto schedule(
            @PathVariable("code") String code,
            @Parameter(description = "Тип дня: weekday|weekend|holiday. Если не задан — выводится из дня недели")
            @RequestParam(name = "dayType", required = false) String dayType) {
        return scheduleService.lineSchedule(code, dayType);
    }

    @GetMapping("/v1/stations/{code}/arrivals")
    @Operation(summary = "Оценочные ближайшие прибытия на станции",
            description = "Возвращает ОЦЕНОЧНЫЕ ближайшие прибытия по линии (SCH-03): вычисляются из "
                    + "статического графика (headway) и текущего времени, а НЕ из realtime "
                    + "(estimated=true всегда). Вне часов работы или без графика на день — "
                    + "serviceActive=false и пустой arrivals. lineCode обязателен. "
                    + "404 — line.not_found/station.not_found; 400 — schedule.line_code_required, "
                    + "schedule.station_not_on_line, schedule.day_type_invalid, schedule.limit_invalid.")
    public StationArrivalsDto arrivals(
            @PathVariable("code") String code,
            @Parameter(description = "Код линии, по которой оцениваются прибытия (обязателен), например L1")
            @RequestParam(name = "lineCode", required = false) String lineCode,
            @Parameter(description = "Тип дня: weekday|weekend|holiday. Если не задан — выводится из дня недели")
            @RequestParam(name = "dayType", required = false) String dayType,
            @Parameter(description = "Сколько прибытий вернуть (1..20, по умолчанию 5)")
            @RequestParam(name = "limit", required = false) Integer limit) {
        return scheduleService.arrivals(code, lineCode, dayType, limit);
    }
}
