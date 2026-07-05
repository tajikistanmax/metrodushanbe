package tj.metro.dushanbe.schedule.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tj.metro.dushanbe.common.error.BadRequestException;
import tj.metro.dushanbe.common.error.NotFoundException;
import tj.metro.dushanbe.network.domain.MetroLine;
import tj.metro.dushanbe.network.domain.MetroStation;
import tj.metro.dushanbe.network.repository.MetroLineRepository;
import tj.metro.dushanbe.network.repository.MetroStationLineRepository;
import tj.metro.dushanbe.network.repository.MetroStationRepository;
import tj.metro.dushanbe.schedule.domain.LineSchedule;
import tj.metro.dushanbe.schedule.repository.LineScheduleRepository;
import tj.metro.dushanbe.schedule.web.dto.ArrivalDto;
import tj.metro.dushanbe.schedule.web.dto.LineScheduleDto;
import tj.metro.dushanbe.schedule.web.dto.StationArrivalsDto;

/**
 * Юнит-тесты ScheduleService без БД: репозитории — mock, «сейчас» — Clock.fixed
 * (детерминизм; зона UTC — время суток из инстанта Clock напрямую).
 * Проверяются: оценка прибытий на сетке headway в часы работы, пустая выдача
 * вне часов работы и без графика, вывод типа дня, 404 по линии/станции,
 * валидация параметров.
 */
class ScheduleServiceTest {

    // Понедельник 2026-07-06, 12:00 UTC — рабочий день, середина часов работы
    private static final Instant MON_NOON = Instant.parse("2026-07-06T12:00:00Z");

    private final LineScheduleRepository scheduleRepository = mock(LineScheduleRepository.class);
    private final MetroLineRepository lineRepository = mock(MetroLineRepository.class);
    private final MetroStationRepository stationRepository = mock(MetroStationRepository.class);
    private final MetroStationLineRepository stationLineRepository = mock(MetroStationLineRepository.class);

    private ScheduleService serviceAt(Instant now) {
        return new ScheduleService(scheduleRepository, lineRepository, stationRepository,
                stationLineRepository, Clock.fixed(now, ZoneOffset.UTC));
    }

    // ---------------------------------------------------------------------
    // arrivals — оценка прибытий
    // ---------------------------------------------------------------------

    @Test
    void arrivalsInServiceHoursReturnsNEstimatesSpacedByHeadway() {
        stationOnLine("ST-L1-01", "L1");
        lineExists("L1");
        // L1 будни: 06:00–23:00, headway 5. В 12:00 (ровно на сетке) первое прибытие — 12:00
        when(scheduleRepository.findEffective(eq("L1"), eq("weekday"), any()))
                .thenReturn(List.of(schedule("L1", "weekday", LocalTime.of(6, 0), LocalTime.of(23, 0), 5)));

        StationArrivalsDto dto = serviceAt(MON_NOON).arrivals("ST-L1-01", "L1", "weekday", 5);

        assertTrue(dto.serviceActive());
        assertTrue(dto.estimated(), "прибытия всегда оценочные (headway-based)");
        assertEquals(5, dto.headwayMinutes());
        assertEquals(5, dto.arrivals().size(), "должно вернуться ровно N=5 прибытий");
        assertEquals(List.of("12:00", "12:05", "12:10", "12:15", "12:20"),
                dto.arrivals().stream().map(ArrivalDto::time).toList());
        assertEquals(List.of(0L, 5L, 10L, 15L, 20L),
                dto.arrivals().stream().map(ArrivalDto::etaMinutes).toList());
    }

    @Test
    void arrivalsAlignsToNextHeadwaySlotWhenNowOffGrid() {
        stationOnLine("ST-L1-01", "L1");
        lineExists("L1");
        when(scheduleRepository.findEffective(eq("L1"), eq("weekday"), any()))
                .thenReturn(List.of(schedule("L1", "weekday", LocalTime.of(6, 0), LocalTime.of(23, 0), 5)));

        // 12:02 — следующая ячейка сетки 06:00+k*5 это 12:05
        StationArrivalsDto dto = serviceAt(Instant.parse("2026-07-06T12:02:00Z"))
                .arrivals("ST-L1-01", "L1", "weekday", 3);

        assertEquals(List.of("12:05", "12:10", "12:15"),
                dto.arrivals().stream().map(ArrivalDto::time).toList());
        assertEquals(3L, dto.arrivals().getFirst().etaMinutes());
    }

    @Test
    void arrivalsCappedByLastDeparture() {
        stationOnLine("ST-L1-01", "L1");
        lineExists("L1");
        when(scheduleRepository.findEffective(eq("L1"), eq("weekday"), any()))
                .thenReturn(List.of(schedule("L1", "weekday", LocalTime.of(6, 0), LocalTime.of(23, 0), 5)));

        // 22:54 — до 23:00 остаётся только 22:55 и 23:00, дальше last_departure
        StationArrivalsDto dto = serviceAt(Instant.parse("2026-07-06T22:54:00Z"))
                .arrivals("ST-L1-01", "L1", "weekday", 5);

        assertEquals(List.of("22:55", "23:00"),
                dto.arrivals().stream().map(ArrivalDto::time).toList());
    }

    @Test
    void arrivalsOutsideServiceHoursIsEmptyAndInactive() {
        stationOnLine("ST-L1-01", "L1");
        lineExists("L1");
        when(scheduleRepository.findEffective(eq("L1"), eq("weekday"), any()))
                .thenReturn(List.of(schedule("L1", "weekday", LocalTime.of(6, 0), LocalTime.of(23, 0), 5)));

        // 04:00 — до открытия
        StationArrivalsDto dto = serviceAt(Instant.parse("2026-07-06T04:00:00Z"))
                .arrivals("ST-L1-01", "L1", "weekday", 5);

        assertFalse(dto.serviceActive(), "вне часов работы сервис неактивен");
        assertTrue(dto.arrivals().isEmpty(), "вне часов работы — пусто");
        assertNull(dto.headwayMinutes());
        assertTrue(dto.estimated());
    }

    @Test
    void arrivalsWithNoScheduleForDayIsEmptyAndInactive() {
        stationOnLine("ST-L1-01", "L1");
        lineExists("L1");
        when(scheduleRepository.findEffective(eq("L1"), eq("holiday"), any())).thenReturn(List.of());

        StationArrivalsDto dto = serviceAt(MON_NOON).arrivals("ST-L1-01", "L1", "holiday", 5);

        assertFalse(dto.serviceActive());
        assertTrue(dto.arrivals().isEmpty());
    }

    @Test
    void arrivalsDerivesDayTypeFromClockWhenAbsent() {
        stationOnLine("ST-L1-01", "L1");
        lineExists("L1");
        // Суббота 2026-07-04 => weekend
        when(scheduleRepository.findEffective(eq("L1"), eq("weekend"), any()))
                .thenReturn(List.of(schedule("L1", "weekend", LocalTime.of(6, 30), LocalTime.of(22, 30), 7)));

        StationArrivalsDto dto = serviceAt(Instant.parse("2026-07-04T12:00:00Z"))
                .arrivals("ST-L1-01", "L1", null, 3);

        assertEquals("weekend", dto.dayType());
        assertTrue(dto.serviceActive());
    }

    @Test
    void arrivalsUnknownLineIs404() {
        stationExists("ST-L1-01");
        when(lineRepository.findByCodeAndDeletedAtIsNull("NOPE")).thenReturn(Optional.empty());

        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> serviceAt(MON_NOON).arrivals("ST-L1-01", "NOPE", "weekday", 5));
        assertEquals("line.not_found", ex.getCode());
    }

    @Test
    void arrivalsUnknownStationIs404() {
        when(stationRepository.findByCodeAndDeletedAtIsNull("NOPE")).thenReturn(Optional.empty());

        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> serviceAt(MON_NOON).arrivals("NOPE", "L1", "weekday", 5));
        assertEquals("station.not_found", ex.getCode());
    }

    @Test
    void arrivalsStationNotOnLineIs400() {
        stationExists("ST-L2-01");
        lineExists("L1");
        when(stationLineRepository.findLineCodesByStationCode("ST-L2-01")).thenReturn(List.of("L2"));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> serviceAt(MON_NOON).arrivals("ST-L2-01", "L1", "weekday", 5));
        assertEquals("schedule.station_not_on_line", ex.getCode());
    }

    @Test
    void arrivalsMissingLineCodeIs400() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> serviceAt(MON_NOON).arrivals("ST-L1-01", "  ", "weekday", 5));
        assertEquals("schedule.line_code_required", ex.getCode());
    }

    @Test
    void arrivalsInvalidLimitIs400() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> serviceAt(MON_NOON).arrivals("ST-L1-01", "L1", "weekday", 999));
        assertEquals("schedule.limit_invalid", ex.getCode());
    }

    // ---------------------------------------------------------------------
    // lineSchedule — график линии по дню
    // ---------------------------------------------------------------------

    @Test
    void lineScheduleReturnsFormattedDto() {
        lineExists("L1");
        when(scheduleRepository.findEffective(eq("L1"), eq("weekday"), any()))
                .thenReturn(List.of(schedule("L1", "weekday", LocalTime.of(6, 0), LocalTime.of(23, 0), 5)));

        LineScheduleDto dto = serviceAt(MON_NOON).lineSchedule("L1", "weekday");

        assertEquals("L1", dto.lineCode());
        assertEquals("weekday", dto.dayType());
        assertEquals("06:00", dto.firstDeparture());
        assertEquals("23:00", dto.lastDeparture());
        assertEquals(5, dto.headwayMinutes());
        assertEquals(LocalDate.of(2020, 1, 1), dto.effectiveFrom());
        assertNull(dto.effectiveTo());
    }

    @Test
    void lineScheduleUnknownLineIs404() {
        when(lineRepository.findByCodeAndDeletedAtIsNull("NOPE")).thenReturn(Optional.empty());

        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> serviceAt(MON_NOON).lineSchedule("NOPE", "weekday"));
        assertEquals("line.not_found", ex.getCode());
    }

    @Test
    void lineScheduleMissingForDayIs404() {
        lineExists("L1");
        when(scheduleRepository.findEffective(eq("L1"), eq("holiday"), any())).thenReturn(List.of());

        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> serviceAt(MON_NOON).lineSchedule("L1", "holiday"));
        assertEquals("schedule.not_found", ex.getCode());
    }

    @Test
    void lineScheduleInvalidDayTypeIs400() {
        lineExists("L1");

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> serviceAt(MON_NOON).lineSchedule("L1", "bogus"));
        assertEquals("schedule.day_type_invalid", ex.getCode());
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    private void lineExists(String code) {
        when(lineRepository.findByCodeAndDeletedAtIsNull(code)).thenReturn(Optional.of(mock(MetroLine.class)));
    }

    private void stationExists(String code) {
        when(stationRepository.findByCodeAndDeletedAtIsNull(code)).thenReturn(Optional.of(mock(MetroStation.class)));
    }

    private void stationOnLine(String stationCode, String lineCode) {
        stationExists(stationCode);
        when(stationLineRepository.findLineCodesByStationCode(stationCode)).thenReturn(List.of(lineCode));
    }

    private static LineSchedule schedule(String lineCode, String dayType,
                                         LocalTime first, LocalTime last, int headway) {
        return new LineSchedule(UUID.randomUUID(), lineCode, dayType, first, last, headway,
                LocalDate.of(2020, 1, 1), null);
    }
}
