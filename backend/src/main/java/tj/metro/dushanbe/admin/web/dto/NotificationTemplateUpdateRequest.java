package tj.metro.dushanbe.admin.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/**
 * Редактирование шаблона; стабильный код не меняется.
 *
 * <p>Правка шаблона НЕ трогает уже созданные из него рассылки: их тексты — копия,
 * сделанная в момент создания (см. шапку V022). Это осознанно: история того, что
 * реально ушло получателям, не может задним числом поменяться.
 */
public record NotificationTemplateUpdateRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Pattern(regexp = "info|warning|incident|maintenance|promo") String type,
        @NotEmpty Map<String, String> title,
        @NotEmpty Map<String, String> body,
        @NotEmpty List<@Pattern(regexp = "in_app|push|email|sms") String> channels,
        boolean active) {
}
