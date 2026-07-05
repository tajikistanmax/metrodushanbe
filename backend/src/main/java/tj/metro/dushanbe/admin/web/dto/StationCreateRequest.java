package tj.metro.dushanbe.admin.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

/**
 * Запрос создания станции (ADM-02). {@code name} — i18n-объект (tg/ru/en обязательны);
 * {@code coordinates} — [lon, lat] в EPSG:4326 (обязательны); {@code description} —
 * необязательный i18n-объект; {@code accessibility} — теги доступности станции.
 */
public record StationCreateRequest(
        @NotBlank String code,
        @NotNull Map<String, String> name,
        @NotBlank String status,
        @NotNull List<Double> coordinates,
        Boolean isTransfer,
        List<String> accessibility,
        Map<String, String> description) {
}
