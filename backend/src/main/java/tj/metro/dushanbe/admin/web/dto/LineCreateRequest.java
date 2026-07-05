package tj.metro.dushanbe.admin.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.Map;

/**
 * Запрос создания линии (ADM-02). {@code name} — i18n-объект (tg/ru/en обязательны,
 * проверяется сервисом); {@code colorHex} — #RRGGBB; {@code path} — необязательная
 * трасса как список точек [lon, lat] (EPSG:4326).
 */
public record LineCreateRequest(
        @NotBlank String code,
        @NotNull Map<String, String> name,
        @NotBlank @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "цвет должен быть в формате #RRGGBB") String colorHex,
        @NotBlank String status,
        Integer sortOrder,
        List<List<Double>> path) {
}
