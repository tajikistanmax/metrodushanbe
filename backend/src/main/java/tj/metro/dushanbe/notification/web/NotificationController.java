package tj.metro.dushanbe.notification.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tj.metro.dushanbe.notification.service.NotificationService;
import tj.metro.dushanbe.notification.web.dto.NotificationDto;

/**
 * In-app-фид уведомлений. Итоговый путь с учётом context-path: /api/v1/notifications.
 * Контракт таргет-фильтров — docs/dev-conventions.md §3 (тот же, что у /v1/alerts), ТЗ §6.2.7.
 */
@RestController
@RequestMapping("/v1/notifications")
@Tag(name = "Notifications", description = "Лента уведомлений в приложении (NTF-01…04)")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @Operation(summary = "Лента отправленных уведомлений",
            description = "Возвращает только отправленные (status=sent) рассылки, у которых среди "
                    + "каналов есть in_app; свежие сверху (по sentAt убыв.). "
                    + "Пустой массив targets = рассылка на всю сеть. "
                    + "Поля title/body — полные i18n-объекты {tg, ru, en}.")
    public List<NotificationDto> list(
            @Parameter(description = "Фильтр по линии (стабильный код, например L2): рассылки, "
                    + "таргетированные этой линией (станционные таргеты линию не расширяют); "
                    + "рассылки на всю сеть включаются всегда. Вместе со stationCode — "
                    + "объединение: рассылка попадает, если проходит хотя бы один фильтр")
            @RequestParam(name = "lineCode", required = false) String lineCode,
            @Parameter(description = "Фильтр по станции (стабильный код, например ST-HUB-CENTER): "
                    + "рассылки, таргетированные этой станцией ИЛИ любой линией, которой станция "
                    + "принадлежит; рассылки на всю сеть включаются всегда. Вместе с lineCode — "
                    + "объединение: рассылка попадает, если проходит хотя бы один фильтр")
            @RequestParam(name = "stationCode", required = false) String stationCode) {
        return notificationService.feed(lineCode, stationCode);
    }
}
