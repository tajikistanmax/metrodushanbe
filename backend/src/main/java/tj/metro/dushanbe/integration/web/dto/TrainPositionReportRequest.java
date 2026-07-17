package tj.metro.dushanbe.integration.web.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Замер от GPS/диспетчерской платформы (U-INT-04).
 *
 * <p>{@code coordinates} — {@code [lon, lat]}, как везде в проекте. Диапазоны
 * проверяет сервис (там же строится геометрия): jakarta-валидация умеет проверить
 * размер массива, но не то, что первый элемент — долгота.
 *
 * @param reportedAt время замера по часам платформы; обязателен — без него
 *                   невозможно отличить свежий замер от переигранного пакета
 */
public record TrainPositionReportRequest(
        @NotBlank @Size(max = 64) String trainCode,
        @NotBlank @Size(max = 64) String lineCode,
        @Size(max = 64) String stationCode,
        @Size(max = 64) String nextStationCode,
        @NotNull @Size(min = 2, max = 2) List<Double> coordinates,
        @Min(0) @Max(359) Integer heading,
        @DecimalMin("0.0") @DecimalMax("999.9") BigDecimal speedKmh,
        Integer delaySeconds,
        @Size(max = 16) String occupancy,
        @NotNull Instant reportedAt) {
}
