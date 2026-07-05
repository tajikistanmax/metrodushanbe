package tj.metro.dushanbe.routing.web.dto;

import java.util.List;

/**
 * Результат построения маршрута станция→станция (RTE-01, RTE-05).
 *
 * <p>{@code found} — построен ли маршрут ({@code false} = пути нет: станции в разных
 * несвязных компонентах сети или одна из них закрыта — BR-RTE-1); при {@code false}
 * {@code legs}/{@code stops} пусты, а числовые оценки равны 0.
 *
 * <p>{@code estimatedMinutes} — ориентировочное время в пути (RTE-05): сумма времени
 * перегонов и штрафов за пересадки; {@code transfers} — число пересадок (BR-RTE-2);
 * {@code segmentCount} — суммарное число перегонов по всем участкам.
 *
 * <p>{@code legs} — участки маршрута по линиям (без пересадки внутри участка),
 * {@code stops} — плоская последовательность станций в порядке следования.
 *
 * <p>Оценки времени — заглушка до появления реального расписания (модуль
 * schedule-realtime, SCH); fare (RTE-08), step-free (RTE-07) и realtime-коррекция
 * (RTE-06) — вне этой итерации.
 */
public record RouteDto(String from,
                       String to,
                       boolean found,
                       int estimatedMinutes,
                       int transfers,
                       int segmentCount,
                       List<RouteLegDto> legs,
                       List<RouteStopDto> stops) {
}
