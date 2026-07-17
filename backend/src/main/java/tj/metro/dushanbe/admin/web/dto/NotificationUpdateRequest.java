package tj.metro.dushanbe.admin.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import tj.metro.dushanbe.notification.web.dto.NotificationTargetDto;

/**
 * Редактирование рассылки. Код и статус не меняются (статус — отдельной операцией).
 *
 * <p>Здесь, в отличие от создания, тексты обязательны: редактирование — это полная
 * замена содержимого, а происхождение из шаблона на этот момент уже не при чём
 * (тексты скопированы в сообщение при создании).
 *
 * <p>Замороженную рассылку ({@code status.frozen()}) сервис редактировать не даст —
 * 400 {@code notification.frozen}.
 */
public record NotificationUpdateRequest(
        @NotBlank @Pattern(regexp = "info|warning|incident|maintenance|promo") String type,
        @NotEmpty Map<String, String> title,
        @NotEmpty Map<String, String> body,
        @NotEmpty List<@Pattern(regexp = "in_app|push|email|sms") String> channels,
        @Valid List<NotificationTargetDto> targets,
        OffsetDateTime scheduledAt) {
}
