package tj.metro.dushanbe.integration.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Положение поезда в публичной выдаче (U-INT-04).
 *
 * <p>Координаты отдаются массивом {@code [lon, lat]} — тем же порядком осей, что
 * GeoJSON и {@code AdminSupport.point}: клиент кладёт их на карту без перестановки.
 *
 * @param lagSeconds насколько замер устарел к моменту приёма; клиент по нему
 *                   решает, показывать ли поезд как «данные устарели»
 */
public record TrainPositionDto(
        String trainCode,
        String lineCode,
        String stationCode,
        String nextStationCode,
        List<Double> coordinates,
        Integer heading,
        BigDecimal speedKmh,
        Integer delaySeconds,
        String occupancy,
        Instant reportedAt,
        Instant receivedAt,
        long lagSeconds) {
}
