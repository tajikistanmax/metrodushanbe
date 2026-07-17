package tj.metro.dushanbe.admin.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import tj.metro.dushanbe.notification.web.dto.NotificationTargetDto;

/**
 * Создание рассылки (NTF-01…05). Статус не принимается: рассылка всегда стартует
 * черновиком, а планирование/отправка — отдельные операции.
 *
 * <p>Если задан {@code templateCode}, шаблон даёт значения по умолчанию для
 * type/title/body/channels, и они КОПИРУЮТСЯ в сообщение — дальше рассылка от
 * шаблона не зависит. Явно переданные поля перекрывают шаблонные. Без шаблона
 * type/title/body/channels обязательны (проверяет сервис: тут они nullable, иначе
 * создание из шаблона требовало бы дублировать его тексты в запросе).
 *
 * <p>{@code scheduledAt} != null — отложенная публикация (NTF-05): рассылка сразу
 * переводится в {@code scheduled}, и её подхватит NotificationScheduler.
 */
public record NotificationCreateRequest(
        @NotBlank @Size(max = 64) String code,
        @Size(max = 64) String templateCode,
        @Size(max = 64) String alertCode,
        @Pattern(regexp = "info|warning|incident|maintenance|promo") String type,
        Map<String, String> title,
        Map<String, String> body,
        List<@Pattern(regexp = "in_app|push|email|sms") String> channels,
        @Valid List<NotificationTargetDto> targets,
        OffsetDateTime scheduledAt) {
}
