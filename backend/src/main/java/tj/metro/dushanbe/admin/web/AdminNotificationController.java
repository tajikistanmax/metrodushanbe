package tj.metro.dushanbe.admin.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tj.metro.dushanbe.admin.service.AdminNotificationService;
import tj.metro.dushanbe.admin.web.dto.NotificationCreateRequest;
import tj.metro.dushanbe.admin.web.dto.NotificationStatusRequest;
import tj.metro.dushanbe.admin.web.dto.NotificationTemplateCreateRequest;
import tj.metro.dushanbe.admin.web.dto.NotificationTemplateUpdateRequest;
import tj.metro.dushanbe.admin.web.dto.NotificationUpdateRequest;
import tj.metro.dushanbe.notification.web.dto.NotificationDeliveryDto;
import tj.metro.dushanbe.notification.web.dto.NotificationDeliveryPageDto;
import tj.metro.dushanbe.notification.web.dto.NotificationDto;
import tj.metro.dushanbe.notification.web.dto.NotificationPageDto;
import tj.metro.dushanbe.notification.web.dto.NotificationTemplateDto;

/**
 * Управление рассылками и шаблонами (NTF-01…06). Публичный аналог — только
 * in-app-фид /v1/notifications, куда попадает лишь отправленное.
 */
@RestController
@RequestMapping("/v1/admin")
@Tag(name = "Admin: Notifications", description = "Рассылки, шаблоны и доставка")
public class AdminNotificationController {

    private final AdminNotificationService service;

    public AdminNotificationController(AdminNotificationService service) {
        this.service = service;
    }

    // --- Рассылки ------------------------------------------------------------

    @GetMapping("/notifications")
    @Operation(summary = "Лента рассылок (NTF-01)",
            description = "Постраничный список, свежие сверху; контракт страницы тот же, что у "
                    + "/v1/admin/imports и очередей доставки (items + page/size/totals). Размер "
                    + "страницы ограничен сверху (200); выходящие за границы page/size зажимаются, "
                    + "а не отвергаются. Фильтр ?status=draft|scheduled|sending|sent|cancelled; "
                    + "иное значение — 400 notification.status_invalid")
    public NotificationPageDto list(
            @RequestParam(name = "status", required = false) String status,
            @Parameter(description = "Номер страницы, начиная с 0")
            @RequestParam(name = "page", defaultValue = "0") int page,
            @Parameter(description = "Размер страницы (1..200)")
            @RequestParam(name = "size", defaultValue = "50") int size) {
        return service.list(status, page, size);
    }

    @GetMapping("/notifications/{code}")
    public NotificationDto get(@PathVariable("code") String code) {
        return service.get(code);
    }

    @PostMapping("/notifications")
    @Operation(summary = "Создать рассылку",
            description = "Можно из шаблона (templateCode) — тексты копируются в сообщение и "
                    + "дальше от шаблона не зависят; явные поля запроса перекрывают шаблонные. "
                    + "Заданный scheduledAt сразу переводит рассылку в scheduled (NTF-05). "
                    + "Ошибки: 400 notification.code_exists, notification.field_required, "
                    + "notification.template_inactive, notification.channel_invalid, "
                    + "validation.i18n_incomplete; 404 notification.template_not_found")
    public ResponseEntity<NotificationDto> create(
            @Valid @RequestBody NotificationCreateRequest request,
            @RequestHeader(name = "X-Admin-Actor", defaultValue = "dev-admin") String actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request, actor));
    }

    @PutMapping("/notifications/{code}")
    @Operation(summary = "Изменить рассылку",
            description = "400 notification.frozen, если доставка уже начата (sending/sent/cancelled); "
                    + "400 notification.scheduled_at_required, если у запланированной убрать scheduledAt")
    public NotificationDto update(
            @PathVariable("code") String code,
            @Valid @RequestBody NotificationUpdateRequest request,
            @RequestHeader(name = "X-Admin-Actor", defaultValue = "dev-admin") String actor) {
        return service.update(code, request, actor);
    }

    @PostMapping("/notifications/{code}/status")
    @Operation(summary = "Перевести рассылку в новое состояние",
            description = "400 notification.transition_invalid, если переход не разрешён текущим "
                    + "статусом; 400 notification.send_required — отметить sent можно только через send")
    public NotificationDto changeStatus(
            @PathVariable("code") String code,
            @Valid @RequestBody NotificationStatusRequest request,
            @RequestHeader(name = "X-Admin-Actor", defaultValue = "dev-admin") String actor) {
        return service.changeStatus(code, request, actor);
    }

    @PostMapping("/notifications/{code}/send")
    @Operation(summary = "Отправить рассылку",
            description = "draft/scheduled → sending → sent, создаёт доставку на каждого получателя "
                    + "× канал. Реально доставляется только in_app (публичный фид); каналы "
                    + "push/email/sms имитируются — признак в поле simulated и в аудите. "
                    + "400 notification.transition_invalid, если рассылка уже отправлена/отменена")
    public NotificationDto send(
            @PathVariable("code") String code,
            @RequestHeader(name = "X-Admin-Actor", defaultValue = "dev-admin") String actor) {
        return service.send(code, actor);
    }

    // --- Доставки ------------------------------------------------------------

    @GetMapping("/notifications/{code}/deliveries")
    @Operation(summary = "Доставки одной рассылки (NTF-06)",
            description = "Постраничный список, свежие сверху. Размер страницы ограничен сверху (200); "
                    + "выходящие за границы page/size зажимаются, а не отвергаются")
    public NotificationDeliveryPageDto deliveries(
            @PathVariable("code") String code,
            @Parameter(description = "Номер страницы, начиная с 0")
            @RequestParam(name = "page", defaultValue = "0") int page,
            @Parameter(description = "Размер страницы (1..200)")
            @RequestParam(name = "size", defaultValue = "50") int size) {
        return service.deliveries(code, page, size);
    }

    @GetMapping("/notifications/deliveries/problems")
    @Operation(summary = "Очередь проблемных доставок",
            description = "Всё, что не доставлено: pending и failed (OPS-04). Постраничный список, "
                    + "свежие сверху; размер страницы ограничен сверху (200)")
    public NotificationDeliveryPageDto problemDeliveries(
            @Parameter(description = "Номер страницы, начиная с 0")
            @RequestParam(name = "page", defaultValue = "0") int page,
            @Parameter(description = "Размер страницы (1..200)")
            @RequestParam(name = "size", defaultValue = "50") int size) {
        return service.problemDeliveries(page, size);
    }

    @PostMapping("/notifications/deliveries/{id}/retry")
    @Operation(summary = "Повторить проваленную доставку (NTF-06)",
            description = "failed → pending: повтор ставит доставку обратно в очередь, а не "
                    + "объявляет её отправленной. {id} — uuid доставки (своего code у неё нет). "
                    + "400 notification.retry_not_failed, если доставка не в состоянии failed; "
                    + "400 notification.delivery_id_invalid; 404 notification_delivery.not_found")
    public NotificationDeliveryDto retryDelivery(
            @PathVariable("id") String id,
            @RequestHeader(name = "X-Admin-Actor", defaultValue = "dev-admin") String actor) {
        return service.retryDelivery(id, actor);
    }

    // --- Шаблоны -------------------------------------------------------------

    @GetMapping("/notification-templates")
    public List<NotificationTemplateDto> listTemplates() {
        return service.listTemplates();
    }

    @GetMapping("/notification-templates/{code}")
    public NotificationTemplateDto getTemplate(@PathVariable("code") String code) {
        return service.getTemplate(code);
    }

    @PostMapping("/notification-templates")
    @Operation(summary = "Создать шаблон рассылки (NTF-05)",
            description = "400 notification.template_code_exists, validation.i18n_incomplete")
    public ResponseEntity<NotificationTemplateDto> createTemplate(
            @Valid @RequestBody NotificationTemplateCreateRequest request,
            @RequestHeader(name = "X-Admin-Actor", defaultValue = "dev-admin") String actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createTemplate(request, actor));
    }

    @PutMapping("/notification-templates/{code}")
    @Operation(summary = "Изменить шаблон",
            description = "Уже созданные из шаблона рассылки не затрагиваются: их тексты — копия")
    public NotificationTemplateDto updateTemplate(
            @PathVariable("code") String code,
            @Valid @RequestBody NotificationTemplateUpdateRequest request,
            @RequestHeader(name = "X-Admin-Actor", defaultValue = "dev-admin") String actor) {
        return service.updateTemplate(code, request, actor);
    }

    @DeleteMapping("/notification-templates/{code}")
    @Operation(summary = "Удалить шаблон",
            description = "История созданных из него рассылок сохраняется (FK нет)")
    public ResponseEntity<Void> deleteTemplate(
            @PathVariable("code") String code,
            @RequestHeader(name = "X-Admin-Actor", defaultValue = "dev-admin") String actor) {
        service.deleteTemplate(code, actor);
        return ResponseEntity.noContent().build();
    }
}
