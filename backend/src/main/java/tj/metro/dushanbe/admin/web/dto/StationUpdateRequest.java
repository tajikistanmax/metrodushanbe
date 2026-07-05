package tj.metro.dushanbe.admin.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

/**
 * Запрос обновления станции (ADM-02). Стабильный код неизменен (BR-NET-5) — из пути.
 * {@code coordinates} — [lon, lat]; null — координата не меняется.
 */
public record StationUpdateRequest(
        @NotNull Map<String, String> name,
        @NotBlank String status,
        List<Double> coordinates,
        Boolean isTransfer,
        List<String> accessibility,
        Map<String, String> description) {
}
