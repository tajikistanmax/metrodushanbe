package tj.metro.dushanbe.schedule.web.dto;

import java.time.Instant;
import java.util.List;

/**
 * Оценочные ближайшие прибытия на станцию по линии (SCH-03).
 *
 * <p><b>ВНИМАНИЕ: значения оценочные.</b> {@code estimated} всегда {@code true} —
 * прибытия вычисляются из статического графика (headway) и текущего времени,
 * а не из realtime-телеметрии. Реальные прогнозы появятся с realtime-фидом
 * (SCH-03/05/08); текущий контур — MVP статического расписания.
 *
 * <p>{@code serviceActive} = {@code false} и пустой {@code arrivals}, если «сейчас»
 * вне часов работы линии или график на этот тип дня отсутствует
 * (BR-SCH-2: устаревшее/невалидное значение не отдаём).
 */
public record StationArrivalsDto(String stationCode,
                                 String lineCode,
                                 String dayType,
                                 boolean serviceActive,
                                 Integer headwayMinutes,
                                 boolean estimated,
                                 Instant generatedAt,
                                 List<ArrivalDto> arrivals) {
}
