package tj.metro.dushanbe.schedule.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tj.metro.dushanbe.common.error.BadRequestException;
import tj.metro.dushanbe.common.error.NotFoundException;
import tj.metro.dushanbe.network.repository.MetroLineRepository;
import tj.metro.dushanbe.network.repository.MetroStationRepository;
import tj.metro.dushanbe.network.repository.MetroStationLineRepository;
import tj.metro.dushanbe.schedule.domain.LineSchedule;
import tj.metro.dushanbe.schedule.repository.LineScheduleRepository;
import tj.metro.dushanbe.schedule.web.dto.ArrivalDto;
import tj.metro.dushanbe.schedule.web.dto.LineScheduleDto;
import tj.metro.dushanbe.schedule.web.dto.StationArrivalsDto;

/**
 * Публичный контур статического расписания (ТЗ §6.2.4, MVP):
 * график движения линии по типу дня (SCH-01) и оценка ближайших прибытий
 * на станции (SCH-03, headway-based).
 *
 * <p>«Сейчас» берётся из инжектируемого {@link Clock} — как в alert/content,
 * для детерминированной тестируемости через {@code Clock.fixed(...)}. Дата и
 * время суток вычисляются в зоне часов Clock; в проде Clock настраивается на
 * зону эксплуатации метро (Asia/Dushanbe). Принадлежность станции линии
 * резолвится через {@link MetroStationLineRepository} модуля network (read-only
 * использование соседнего модуля — осознанное допущение модульного монолита).
 *
 * <p>Прибытия — <b>оценочные</b> (из headway, не из realtime). TODO(realtime,
 * SCH-03/05/08): заменить оценку на прогноз из подтверждённого realtime-источника
 * (BR-SCH-1/2). TODO(BR-RTE-3, routing):
 * учёт service hours при построении маршрута — интеграция с модулем routing.
 */
@Service
@Transactional(readOnly = true)
public class ScheduleService {

    /** Допустимые типы дня (совпадают с CHECK ck_line_schedule_day_type в V011). */
    public static final Set<String> DAY_TYPES = Set.of("weekday", "weekend", "holiday");

    /** Разумный потолок числа прибытий в одном ответе. */
    private static final int MAX_ARRIVALS = 20;
    private static final int DEFAULT_ARRIVALS = 5;

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    private final LineScheduleRepository scheduleRepository;
    private final MetroLineRepository lineRepository;
    private final MetroStationRepository stationRepository;
    private final MetroStationLineRepository stationLineRepository;
    private final CalendarExceptionService calendarExceptionService;
    private final Clock clock;

    public ScheduleService(LineScheduleRepository scheduleRepository,
                           MetroLineRepository lineRepository,
                           MetroStationRepository stationRepository,
                           MetroStationLineRepository stationLineRepository,
                           CalendarExceptionService calendarExceptionService,
                           Clock clock) {
        this.scheduleRepository = scheduleRepository;
        this.lineRepository = lineRepository;
        this.stationRepository = stationRepository;
        this.stationLineRepository = stationLineRepository;
        this.calendarExceptionService = calendarExceptionService;
        this.clock = clock;
    }

    /**
     * График движения линии для типа дня. {@code dayType} опционален — если пуст,
 * выводится из текущего дня недели Clock с учётом календарных исключений
 * (CalendarExceptionService, SCH-02).
     *
     * @throws NotFoundException   {@code line.not_found} — линии нет/soft-deleted
     * @throws NotFoundException   {@code schedule.not_found} — на этот тип дня графика нет
     * @throws BadRequestException {@code schedule.day_type_invalid} — недопустимый dayType
     */
    @Cacheable(value = "schedules", key = "#lineCode + ':' + #dayType")
    public LineScheduleDto lineSchedule(String lineCode, String dayType) {
        requireLineExists(lineCode);
        String resolvedDayType = resolveDayType(dayType);
        LineSchedule schedule = effectiveSchedule(lineCode, resolvedDayType)
                .orElseThrow(() -> new NotFoundException("schedule.not_found",
                        "График для линии '" + lineCode + "' и дня '" + resolvedDayType + "' не найден"));
        return toDto(schedule);
    }

    /**
     * Оценочные ближайшие прибытия на станции по линии (SCH-03, headway-based).
     * {@code dayType} опционален (см. {@link #lineSchedule}); {@code limit} — сколько
     * прибытий вернуть (1..{@value #MAX_ARRIVALS}, по умолчанию {@value #DEFAULT_ARRIVALS}).
     *
     * <p>Вне часов работы линии или при отсутствии графика — {@code serviceActive=false}
     * и пустой список (BR-SCH-2). Значения всегда {@code estimated=true}.
     *
     * @throws NotFoundException   {@code line.not_found} / {@code station.not_found}
     * @throws BadRequestException {@code schedule.line_code_required} — не задан lineCode;
     *                             {@code schedule.station_not_on_line} — станция не на линии;
     *                             {@code schedule.day_type_invalid} — недопустимый dayType;
     *                             {@code schedule.limit_invalid} — limit вне диапазона
     */
    public StationArrivalsDto arrivals(String stationCode, String lineCode, String dayType, Integer limit) {
        if (isBlank(lineCode)) {
            throw new BadRequestException("schedule.line_code_required",
                    "Не задан обязательный параметр 'lineCode'",
                    Map.of("parameter", "lineCode"));
        }
        int resolvedLimit = resolveLimit(limit);
        requireStationExists(stationCode);
        requireLineExists(lineCode);
        requireStationOnLine(stationCode, lineCode);
        String resolvedDayType = resolveDayType(dayType);

        Instant now = clock.instant();
        LocalTime nowTime = LocalTime.now(clock);

        return effectiveSchedule(lineCode, resolvedDayType)
                .filter(s -> s.isWithinServiceHours(nowTime))
                .map(s -> new StationArrivalsDto(stationCode, lineCode, resolvedDayType, true,
                        s.getHeadwayMinutes(), true, now,
                        estimateArrivals(s, nowTime, resolvedLimit)))
                .orElseGet(() -> new StationArrivalsDto(stationCode, lineCode, resolvedDayType, false,
                        null, true, now, List.of()));
    }

    /**
     * Ближайшие прибытия на сетке headway: t = firstDeparture + k*headway, где t >= now
     * и t <= lastDeparture, не более {@code limit} штук. Прибытие в текущую минуту
     * (t == now) включается с {@code etaMinutes = 0}.
     */
    private List<ArrivalDto> estimateArrivals(LineSchedule schedule, LocalTime now, int limit) {
        int headway = schedule.getHeadwayMinutes();
        long fromFirst = Duration.between(schedule.getFirstDeparture(), now).toMinutes();
        // округляем вверх до следующего кратного headway, чтобы t >= now
        long steps = Math.max(0, (fromFirst + headway - 1) / headway);
        List<ArrivalDto> arrivals = new ArrayList<>();
        LocalTime candidate = schedule.getFirstDeparture().plusMinutes(steps * headway);
        while (arrivals.size() < limit && !candidate.isAfter(schedule.getLastDeparture())) {
            arrivals.add(new ArrivalDto(candidate.format(HHMM), Duration.between(now, candidate).toMinutes()));
            candidate = candidate.plusMinutes(headway);
        }
        return arrivals;
    }

    private java.util.Optional<LineSchedule> effectiveSchedule(String lineCode, String dayType) {
        List<LineSchedule> effective =
                scheduleRepository.findEffective(lineCode, dayType, LocalDate.now(clock));
        return effective.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(effective.getFirst());
    }

    private String resolveDayType(String dayType) {
        if (isBlank(dayType)) {
            LocalDate today = LocalDate.now(clock);
            String resolved = calendarExceptionService.resolveDayType(today);
            // Праздник/особый день имеет собственный график (day_type='holiday'); без этой
            // ветки "holiday" схлопывался бы в "weekday" и календарные исключения (V014/V016)
            // в авто-режиме никогда не срабатывали.
            if ("holiday".equals(resolved)) {
                return "holiday";
            }
            return "weekend".equals(resolved) || "saturday".equals(resolved) || "sunday".equals(resolved)
                    ? "weekend" : "weekday";
        }
        if (!DAY_TYPES.contains(dayType)) {
            throw new BadRequestException("schedule.day_type_invalid",
                    "Недопустимое значение параметра 'dayType': " + dayType,
                    Map.of("parameter", "dayType", "value", dayType, "allowed", DAY_TYPES));
        }
        return dayType;
    }

    private int resolveLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_ARRIVALS;
        }
        if (limit < 1 || limit > MAX_ARRIVALS) {
            throw new BadRequestException("schedule.limit_invalid",
                    "Параметр 'limit' должен быть в диапазоне 1.." + MAX_ARRIVALS,
                    Map.of("parameter", "limit", "value", limit, "min", 1, "max", MAX_ARRIVALS));
        }
        return limit;
    }

    private void requireLineExists(String lineCode) {
        if (lineRepository.findByCodeAndDeletedAtIsNull(lineCode).isEmpty()) {
            throw NotFoundException.line(lineCode);
        }
    }

    private void requireStationExists(String stationCode) {
        if (stationRepository.findByCodeAndDeletedAtIsNull(stationCode).isEmpty()) {
            throw NotFoundException.station(stationCode);
        }
    }

    private void requireStationOnLine(String stationCode, String lineCode) {
        if (!stationLineRepository.findLineCodesByStationCode(stationCode).contains(lineCode)) {
            throw new BadRequestException("schedule.station_not_on_line",
                    "Станция '" + stationCode + "' не принадлежит линии '" + lineCode + "'",
                    Map.of("stationCode", stationCode, "lineCode", lineCode));
        }
    }

    private static LineScheduleDto toDto(LineSchedule s) {
        return new LineScheduleDto(
                s.getLineCode(),
                s.getDayType(),
                s.getFirstDeparture().format(HHMM),
                s.getLastDeparture().format(HHMM),
                s.getHeadwayMinutes(),
                s.getEffectiveFrom(),
                s.getEffectiveTo());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
