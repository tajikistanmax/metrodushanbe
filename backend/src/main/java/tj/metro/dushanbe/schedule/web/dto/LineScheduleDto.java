package tj.metro.dushanbe.schedule.web.dto;

import java.time.LocalDate;

/**
 * График движения линии для одного типа дня в ответах API (SCH-01).
 * {@code firstDeparture}/{@code lastDeparture} — местное время суток «HH:mm»;
 * {@code headwayMinutes} — интервал движения; окно действия —
 * [{@code effectiveFrom}, {@code effectiveTo}] (ISO-даты, {@code effectiveTo} null = бессрочно).
 */
public record LineScheduleDto(String lineCode,
                              String dayType,
                              String firstDeparture,
                              String lastDeparture,
                              int headwayMinutes,
                              LocalDate effectiveFrom,
                              LocalDate effectiveTo) {
}
