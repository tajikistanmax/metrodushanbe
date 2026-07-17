package tj.metro.dushanbe.admin.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/**
 * Создание заготовки текста рассылки (NTF-05). Тексты шаблона публикуемые, поэтому
 * полнота tg/ru/en обязательна уже здесь — иначе неполнота всплывёт только в момент
 * рассылки, когда исправлять поздно.
 */
public record NotificationTemplateCreateRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Pattern(regexp = "info|warning|incident|maintenance|promo") String type,
        @NotEmpty Map<String, String> title,
        @NotEmpty Map<String, String> body,
        @NotEmpty List<@Pattern(regexp = "in_app|push|email|sms") String> channels,
        boolean active) {
}
