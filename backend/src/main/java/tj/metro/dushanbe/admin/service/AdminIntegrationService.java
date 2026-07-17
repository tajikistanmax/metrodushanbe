package tj.metro.dushanbe.admin.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tj.metro.dushanbe.admin.web.dto.WebhookCreateRequest;
import tj.metro.dushanbe.admin.web.dto.WebhookSecretDto;
import tj.metro.dushanbe.admin.web.dto.WebhookUpdateRequest;
import tj.metro.dushanbe.audit.service.AuditService;
import tj.metro.dushanbe.common.error.BadRequestException;
import tj.metro.dushanbe.common.error.NotFoundException;
import tj.metro.dushanbe.integration.domain.WebhookDelivery;
import tj.metro.dushanbe.integration.domain.WebhookDeliveryStatus;
import tj.metro.dushanbe.integration.domain.WebhookEventType;
import tj.metro.dushanbe.integration.domain.WebhookSubscription;
import tj.metro.dushanbe.integration.repository.WebhookDeliveryRepository;
import tj.metro.dushanbe.integration.repository.WebhookSubscriptionRepository;
import tj.metro.dushanbe.integration.security.WebhookTargetPolicy;
import tj.metro.dushanbe.integration.service.WebhookSignature;
import tj.metro.dushanbe.integration.web.dto.WebhookDeliveryDto;
import tj.metro.dushanbe.integration.web.dto.WebhookDeliveryPageDto;
import tj.metro.dushanbe.integration.web.dto.WebhookSubscriptionDto;

/**
 * Настройка API-клиентов и вебхуков (ADM-06) плюс операторская очередь ошибок
 * интеграций (U-OPS-04).
 *
 * <p><b>Секрет отдаётся ровно один раз</b> — при создании и при ротации. Дальше в
 * любых выдачах доступен только отпечаток ({@code secretFingerprint}), а сам
 * secret_hash не покидает сервер: он же является ключом подписи (см.
 * {@link WebhookSignature}), и «показать хеш оператору» означало бы отдать
 * возможность подписывать вебхуки от нашего имени.
 */
@Service
public class AdminIntegrationService {

    /** Длина отпечатка ключа: достаточно, чтобы сверить глазами, мало, чтобы перебрать. */
    private static final int FINGERPRINT_LENGTH = 8;

    /** Лимит по умолчанию, если оператор не задал свой (ADM-06). */
    private static final int DEFAULT_RATE_LIMIT = 60;

    /**
     * Порядок очереди доставок: свежие сверху. Тай-брейк по code обязателен —
     * пачка доставок одного события делит {@code updated_at}, а при неуникальном
     * ключе сортировки порядок между страницами не определён, и строка с границы
     * попадёт в обе страницы либо ни в одну.
     */
    private static final Sort DELIVERY_ORDER = Sort.by(Sort.Direction.DESC, "updatedAt")
            .and(Sort.by(Sort.Direction.ASC, "code"));

    private final WebhookSubscriptionRepository subscriptionRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final AuditService auditService;
    private final Clock clock;
    private final WebhookTargetPolicy targetPolicy;

    public AdminIntegrationService(WebhookSubscriptionRepository subscriptionRepository,
                                   WebhookDeliveryRepository deliveryRepository,
                                   AuditService auditService,
                                   Clock clock,
                                   WebhookTargetPolicy targetPolicy) {
        this.subscriptionRepository = subscriptionRepository;
        this.deliveryRepository = deliveryRepository;
        this.auditService = auditService;
        this.clock = clock;
        this.targetPolicy = targetPolicy;
    }

    @Transactional(readOnly = true)
    public List<WebhookSubscriptionDto> listSubscriptions() {
        return subscriptionRepository.findAllByOrderByCodeAsc().stream()
                .map(AdminIntegrationService::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public WebhookSubscriptionDto getSubscription(String code) {
        return toDto(findSubscription(code));
    }

    /** Создание подписчика; секрет в ответе — единственный раз, когда он виден. */
    @Transactional
    public WebhookSecretDto createSubscription(WebhookCreateRequest request, String actor) {
        AdminSupport.requireUnique(subscriptionRepository.existsByCode(request.code()),
                "webhook.code_exists", "code", request.code());
        List<String> eventTypes = requireEventTypes(request.eventTypes());
        String targetUrl = requireTargetUrl(request.targetUrl());
        String secret = WebhookSignature.newSecret();
        WebhookSubscription subscription = new WebhookSubscription(UUID.randomUUID(), request.code(),
                request.name(), targetUrl, WebhookSignature.hashSecret(secret), eventTypes,
                request.active(), rateLimit(request.rateLimitPerMinute()), actor);
        WebhookSubscription saved = subscriptionRepository.save(subscription);
        auditService.record(actor, "webhook.create", "webhook_subscription", saved.getCode(),
                null, snapshot(saved));
        return new WebhookSecretDto(toDto(saved), secret);
    }

    @Transactional
    public WebhookSubscriptionDto updateSubscription(String code, WebhookUpdateRequest request,
                                                     String actor) {
        WebhookSubscription subscription = findSubscription(code);
        List<String> eventTypes = requireEventTypes(request.eventTypes());
        String targetUrl = requireTargetUrl(request.targetUrl());
        Map<String, Object> before = snapshot(subscription);
        subscription.update(request.name(), targetUrl, eventTypes, request.active(),
                rateLimit(request.rateLimitPerMinute()));
        WebhookSubscription saved = subscriptionRepository.save(subscription);
        auditService.record(actor, "webhook.update", "webhook_subscription", code,
                before, snapshot(saved));
        return toDto(saved);
    }

    /**
     * Ротация ключа подписи. Отдельная операция, а не поле в update: смена ключа
     * ломает интеграцию до тех пор, пока подписчик не обновит его у себя, и в
     * аудите это обязано читаться как самостоятельное событие, а не теряться
     * среди правки названия.
     */
    @Transactional
    public WebhookSecretDto rotateSecret(String code, String actor) {
        WebhookSubscription subscription = findSubscription(code);
        Map<String, Object> before = snapshot(subscription);
        String secret = WebhookSignature.newSecret();
        subscription.rotateSecret(WebhookSignature.hashSecret(secret));
        WebhookSubscription saved = subscriptionRepository.save(subscription);
        auditService.record(actor, "webhook.rotate_secret", "webhook_subscription", code,
                before, snapshot(saved));
        return new WebhookSecretDto(toDto(saved), secret);
    }

    /**
     * Удаление подписчика. Доставки не трогаем: их история — часть операционного
     * журнала (U-OPS-04), и «подписчик удалён» не значит «его сбоев не было».
     * Диспетчер такие доставки отправит в DLQ при ближайшем тике.
     */
    @Transactional
    public void deleteSubscription(String code, String actor) {
        WebhookSubscription subscription = findSubscription(code);
        Map<String, Object> before = snapshot(subscription);
        subscriptionRepository.delete(subscription);
        auditService.record(actor, "webhook.delete", "webhook_subscription", code, before, null);
    }

    /**
     * Очередь ошибок интеграций (U-OPS-04).
     *
     * <p>Без фильтра отдаёт failed + dead — то, ради чего оператор сюда и заходит.
     * Явный {@code ?status=} позволяет посмотреть и остальные, но по умолчанию
     * успешные доставки в очереди ошибок только мешали бы: их тысячи, и ни одна
     * не требует действия.
     *
     * <p>Выдача постраничная (контракт {@code /v1/admin/imports}): «их тысячи» —
     * это не оборот речи, очередь растёт как события × подписчики, и фильтр
     * {@code ?status=sent} без границы вычитывал бы всю таблицу.
     */
    @Transactional(readOnly = true)
    public WebhookDeliveryPageDto listDeliveries(String statusFilter, int page, int size) {
        Pageable pageable = AdminSupport.pageable(page, size, DELIVERY_ORDER);
        Page<WebhookDelivery> deliveries = statusFilter == null || statusFilter.isBlank()
                ? deliveryRepository.findByStatusIn(WebhookDeliveryStatus.FAILURES, pageable)
                : deliveryRepository.findByStatus(status(statusFilter), pageable);
        return new WebhookDeliveryPageDto(
                deliveries.getContent().stream().map(this::toDto).toList(),
                deliveries.getNumber(), deliveries.getSize(),
                deliveries.getTotalElements(), deliveries.getTotalPages());
    }

    /**
     * Ручной повтор доставки (U-OPS-04): возвращает failed/dead в pending и
     * сбрасывает next_attempt_at на «сейчас», чтобы ближайший тик диспетчера её
     * забрал — оператор чинит подписчика и ожидает результата сразу, а не через
     * оставшиеся полчаса экспоненциальной паузы.
     *
     * <p>Счётчик attempts НЕ сбрасывается: он показывает, сколько раз мы уже
     * побеспокоили подписчика этим событием, и обнулять его — терять историю.
     * Из-за этого доставка, вернувшаяся из DLQ, уйдёт туда же после первой же
     * неудачи. Это осознанно: повторять стоит после починки, а не вместо неё.
     */
    @Transactional
    public WebhookDeliveryDto retryDelivery(String code, String actor) {
        WebhookDelivery delivery = deliveryRepository.findByCode(code)
                .orElseThrow(() -> new NotFoundException("webhook_delivery.not_found",
                        "Доставка не найдена"));
        WebhookDeliveryStatus current = delivery.getStatus();
        if (!current.canMoveTo(WebhookDeliveryStatus.PENDING)) {
            throw new BadRequestException("webhook_delivery.retry_not_allowed",
                    "Доставка в состоянии '" + current.code() + "' не повторяется",
                    Map.of("status", current.code(),
                            "allowed", current.allowedTransitions().stream()
                                    .map(WebhookDeliveryStatus::code).sorted().toList()));
        }
        Map<String, Object> before = snapshot(delivery);
        delivery.requeue(OffsetDateTime.now(clock));
        WebhookDelivery saved = deliveryRepository.save(delivery);
        auditService.record(actor, "webhook_delivery.retry", "webhook_delivery", code,
                before, snapshot(saved));
        return toDto(saved);
    }

    private WebhookSubscription findSubscription(String code) {
        return subscriptionRepository.findByCode(code)
                .orElseThrow(() -> new NotFoundException("webhook.not_found",
                        "Подписчик вебхуков не найден"));
    }

    /** Подписка на несуществующий тип события — молчаливо мёртвая подписка. */
    private static List<String> requireEventTypes(List<String> eventTypes) {
        for (String type : eventTypes) {
            AdminSupport.requireIn(type, WebhookEventType.codes(),
                    "webhook.event_type_invalid", "eventTypes");
        }
        return eventTypes.stream().distinct().toList();
    }

    private static int rateLimit(Integer requested) {
        return requested == null ? DEFAULT_RATE_LIMIT : requested;
    }

    private String requireTargetUrl(String targetUrl) {
        try {
            return targetPolicy.validateForConfiguration(targetUrl).toASCIIString();
        } catch (WebhookTargetPolicy.UnsafeTargetException exception) {
            throw new BadRequestException("webhook.target_url_unsafe", exception.getMessage(),
                    Map.of("field", "targetUrl"));
        }
    }

    private static WebhookDeliveryStatus status(String code) {
        return WebhookDeliveryStatus.fromCode(code).orElseThrow(() ->
                new BadRequestException("webhook_delivery.status_invalid",
                        "Недопустимый статус доставки: " + code,
                        Map.of("field", "status", "allowed", WebhookDeliveryStatus.codes())));
    }

    private static Map<String, Object> snapshot(WebhookSubscription subscription) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("code", subscription.getCode());
        snapshot.put("name", subscription.getName());
        snapshot.put("targetUrl", subscription.getTargetUrl());
        snapshot.put("eventTypes", subscription.getEventTypes());
        snapshot.put("active", subscription.isActive());
        snapshot.put("rateLimitPerMinute", subscription.getRateLimitPerMinute());
        // Только отпечаток: аудит — это тоже выдача, и ключ подписи в ней
        // хранился бы вечно и в открытом виде.
        snapshot.put("secretFingerprint", fingerprint(subscription.getSecretHash()));
        return snapshot;
    }

    private static Map<String, Object> snapshot(WebhookDelivery delivery) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("code", delivery.getCode());
        snapshot.put("eventId", delivery.getEventId().toString());
        snapshot.put("subscriptionCode", delivery.getSubscriptionCode());
        snapshot.put("status", delivery.getStatus().code());
        snapshot.put("attempts", delivery.getAttempts());
        snapshot.put("lastError", delivery.getLastError());
        return snapshot;
    }

    /** Первые 8 hex-символов ключа — для сверки после ротации, не для подписи. */
    private static String fingerprint(String secretHash) {
        if (secretHash == null || secretHash.length() < FINGERPRINT_LENGTH) {
            return null;
        }
        return secretHash.substring(0, FINGERPRINT_LENGTH);
    }

    public static WebhookSubscriptionDto toDto(WebhookSubscription subscription) {
        return new WebhookSubscriptionDto(
                subscription.getCode(),
                subscription.getName(),
                subscription.getTargetUrl(),
                subscription.getEventTypes(),
                subscription.isActive(),
                subscription.getRateLimitPerMinute(),
                fingerprint(subscription.getSecretHash()),
                subscription.getCreatedBy(),
                subscription.getCreatedAt() == null ? null : subscription.getCreatedAt().toInstant(),
                subscription.getUpdatedAt() == null ? null : subscription.getUpdatedAt().toInstant());
    }

    /** Доставка несёт неизменяемый snapshot события и не зависит от retention outbox. */
    private WebhookDeliveryDto toDto(WebhookDelivery delivery) {
        return new WebhookDeliveryDto(
                delivery.getCode(),
                delivery.getEventId().toString(),
                delivery.getEventTypeSnapshot(),
                delivery.getAggregateTypeSnapshot(),
                delivery.getAggregateCodeSnapshot(),
                delivery.getSubscriptionCode(),
                delivery.getStatus().code(),
                delivery.getAttempts(),
                delivery.getResponseStatus(),
                delivery.getLastError(),
                delivery.getTraceIdSnapshot(),
                delivery.getNextAttemptAt() == null ? null : delivery.getNextAttemptAt().toInstant(),
                delivery.getCreatedAt() == null ? null : delivery.getCreatedAt().toInstant(),
                delivery.getUpdatedAt() == null ? null : delivery.getUpdatedAt().toInstant(),
                delivery.getStatus().retryable(),
                delivery.getStatus().allowedTransitions().stream()
                        .map(WebhookDeliveryStatus::code).sorted().toList());
    }
}
