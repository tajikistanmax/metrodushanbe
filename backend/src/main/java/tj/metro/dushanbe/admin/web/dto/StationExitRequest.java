package tj.metro.dushanbe.admin.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

/**
 * Запрос создания выхода станции (ADM-02/NET-03). {@code name} — i18n-объект
 * (tg/ru/en обязательны); {@code coordinates} — [lon, lat] в EPSG:4326.
 */
public record StationExitRequest(
        @NotBlank String code,
        @NotNull Map<String, String> name,
        @NotNull List<Double> coordinates,
        Boolean isAccessible,
        Integer sortOrder) {
}
