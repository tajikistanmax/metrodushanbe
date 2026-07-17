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
import tj.metro.dushanbe.admin.service.AdminIntegrationService;
import tj.metro.dushanbe.admin.web.dto.WebhookCreateRequest;
import tj.metro.dushanbe.admin.web.dto.WebhookSecretDto;
import tj.metro.dushanbe.admin.web.dto.WebhookUpdateRequest;
import tj.metro.dushanbe.integration.web.dto.WebhookDeliveryDto;
import tj.metro.dushanbe.integration.web.dto.WebhookDeliveryPageDto;
import tj.metro.dushanbe.integration.web.dto.WebhookSubscriptionDto;

/**
 * Настройка вебхуков и очередь ошибок интеграций (ADM-06, U-OPS-04).
 *
 * <p>Публичного аналога у раздела нет: список подписчиков и их лимиты — сведения
 * об инфраструктуре интеграций, а не о сети.
 *
 * <p><b>Права.</b> Подписчики и их секреты — уровень суперадмина, очередь
 * доставок и ручной повтор — уровень дежурного оператора: чинить застрявшую
 * доставку должна смена, а не владелец ключей. Порядок проверок в
 * {@code AdminKeyAuthFilter.requiredRole()} обязан быть именно таким —
 * {@code /v1/admin/webhooks/deliveries} проверяется ДО {@code /v1/admin/webhooks},
 * иначе более общий префикс перехватит очередь и потребует суперадмина.
 */
@RestController
@RequestMapping("/v1/admin/webhooks")
@Tag(name = "Admin: Integrations", description = "Подписчики вебхуков, секреты, лимиты и очередь доставок")
public class AdminIntegrationController {

    private final AdminIntegrationService service;

    public AdminIntegrationController(AdminIntegrationService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Список подписчиков вебхуков",
            description = "Секрет не отдаётся никогда — только отпечаток secretFingerprint.")
    public List<WebhookSubscriptionDto> list() {
        return service.listSubscriptions();
    }

    @GetMapping("/deliveries")
    @Operation(summary = "Очередь доставок (U-OPS-04)",
            description = """
                    Без фильтра — только требующие внимания: failed и dead (DLQ), свежие сверху.
                    Фильтр ?status=pending|sent|failed|dead.
                    Выдача постраничная: ?page= (с 0) и ?size= (1..200); выходящие за границы
                    значения зажимаются, а не отвергаются.
                    Коды ошибок: 400 webhook_delivery.status_invalid.
                    """)
    public WebhookDeliveryPageDto deliveries(
            @RequestParam(name = "status", required = false) String status,
            @Parameter(description = "Номер страницы, начиная с 0")
            @RequestParam(name = "page", defaultValue = "0") int page,
            @Parameter(description = "Размер страницы (1..200)")
            @RequestParam(name = "size", defaultValue = "50") int size) {
        return service.listDeliveries(status, page, size);
    }

    @PostMapping("/deliveries/{code}/retry")
    @Operation(summary = "Повторить доставку вручную (U-OPS-04)",
            description = """
                    Возвращает failed/dead в pending и сбрасывает next_attempt_at на «сейчас».
                    Счётчик попыток не обнуляется.
                    Коды ошибок: 404 webhook_delivery.not_found; 400 webhook_delivery.retry_not_allowed
                    (например, для уже доставленной).
                    """)
    public WebhookDeliveryDto retry(
            @PathVariable("code") String code,
            @RequestHeader(name = "X-Admin-Actor", defaultValue = "dev-admin") String actor) {
        return service.retryDelivery(code, actor);
    }

    @GetMapping("/{code}")
    @Operation(summary = "Карточка подписчика")
    public WebhookSubscriptionDto get(@PathVariable("code") String code) {
        return service.getSubscription(code);
    }

    @PostMapping
    @Operation(summary = "Завести подписчика",
            description = """
                    Секрет генерируется сервером и возвращается ОДИН РАЗ — сохранить его
                    может только интегратор. Повторно получить нельзя, только ротировать.
                    Коды ошибок: 400 webhook.code_exists, webhook.event_type_invalid.
                    """)
    public ResponseEntity<WebhookSecretDto> create(
            @Valid @RequestBody WebhookCreateRequest request,
            @RequestHeader(name = "X-Admin-Actor", defaultValue = "dev-admin") String actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createSubscription(request, actor));
    }

    @PutMapping("/{code}")
    @Operation(summary = "Изменить подписчика",
            description = "Секрет этой операцией не меняется. Коды ошибок: 404 webhook.not_found; 400 webhook.event_type_invalid.")
    public WebhookSubscriptionDto update(
            @PathVariable("code") String code,
            @Valid @RequestBody WebhookUpdateRequest request,
            @RequestHeader(name = "X-Admin-Actor", defaultValue = "dev-admin") String actor) {
        return service.updateSubscription(code, request, actor);
    }

    @PostMapping("/{code}/secret")
    @Operation(summary = "Ротировать секрет подписчика",
            description = """
                    Возвращает новый секрет ОДИН РАЗ. Старый перестаёт действовать немедленно:
                    доставки, подписанные им, подписчик отвергнет, пока не обновит ключ у себя.
                    Коды ошибок: 404 webhook.not_found.
                    """)
    public WebhookSecretDto rotateSecret(
            @PathVariable("code") String code,
            @RequestHeader(name = "X-Admin-Actor", defaultValue = "dev-admin") String actor) {
        return service.rotateSecret(code, actor);
    }

    @DeleteMapping("/{code}")
    @Operation(summary = "Удалить подписчика",
            description = "История доставок сохраняется. Коды ошибок: 404 webhook.not_found.")
    public ResponseEntity<Void> delete(
            @PathVariable("code") String code,
            @RequestHeader(name = "X-Admin-Actor", defaultValue = "dev-admin") String actor) {
        service.deleteSubscription(code, actor);
        return ResponseEntity.noContent().build();
    }
}
