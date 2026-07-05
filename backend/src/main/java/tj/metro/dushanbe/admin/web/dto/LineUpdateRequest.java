package tj.metro.dushanbe.admin.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.Map;

/**
 * Запрос обновления линии (ADM-02). Стабильный код неизменен (BR-NET-5) — берётся
 * из пути. {@code path} — необязательная новая трасса [lon, lat]; null — геометрия
 * не меняется, пустой список — очистка геометрии.
 */
public record LineUpdateRequest(
        @NotNull Map<String, String> name,
        @NotBlank @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "цвет должен быть в формате #RRGGBB") String colorHex,
        @NotBlank String status,
        Integer sortOrder,
        List<List<Double>> path) {
}
