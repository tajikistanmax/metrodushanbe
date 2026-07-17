package tj.metro.dushanbe.admin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

class AdminIntegrationServiceTest {

    private static final OffsetDateTime NOW =
            OffsetDateTime.of(2026, 7, 17, 10, 0, 0, 0, ZoneOffset.UTC);
    private static final UUID EVENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final WebhookSubscriptionRepository subscriptionRepository =
            mock(WebhookSubscriptionRepository.class);
    private final WebhookDeliveryRepository deliveryRepository = mock(WebhookDeliveryRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final AdminIntegrationService service = new AdminIntegrationService(subscriptionRepository,
            deliveryRepository, auditService,
            Clock.fixed(NOW.toInstant(), ZoneOffset.UTC), new WebhookTargetPolicy());

    @Test
    void createReturnsSecretOnceAndStoresOnlyItsHash() {
        when(subscriptionRepository.existsByCode("city-portal")).thenReturn(false);
        when(subscriptionRepository.save(any(WebhookSubscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WebhookSecretDto result = service.createSubscription(createRequest(), "integration-admin");

        assertFalse(result.secret().isBlank(), "секрет обязан быть показан при создании");
        ArgumentCaptor<WebhookSubscription> saved = ArgumentCaptor.forClass(WebhookSubscription.class);
        verify(subscriptionRepository).save(saved.capture());
        assertNotEquals(result.secret(), saved.getValue().getSecretHash(),
                "в БД уходит хеш, а не сам секрет");
        assertEquals(WebhookSignature.hashSecret(result.secret()), saved.getValue().getSecretHash());
        verify(auditService).record(eq("integration-admin"), eq("webhook.create"),
                eq("webhook_subscription"), eq("city-portal"), isNull(), any());
    }

    @Test
    void subscriptionDtoNeverCarriesSecretOrItsHash() {
        WebhookSubscription subscription = subscription();
        when(subscriptionRepository.findAllByOrderByCodeAsc()).thenReturn(List.of(subscription));

        var listed = service.listSubscriptions();

        assertEquals(1, listed.size());
        String hash = subscription.getSecretHash();
        assertNotEquals(hash, listed.get(0).secretFingerprint());
        assertEquals(8, listed.get(0).secretFingerprint().length(),
                "наружу уходит только короткий отпечаток для сверки");
        assertTrue(hash.startsWith(listed.get(0).secretFingerprint()));
    }

    @Test
    void auditSnapshotStoresFingerprintInsteadOfSigningKey() {
        WebhookSubscription subscription = subscription();
        when(subscriptionRepository.findByCode("city-portal")).thenReturn(Optional.of(subscription));
        when(subscriptionRepository.save(any(WebhookSubscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.updateSubscription("city-portal", updateRequest(), "integration-admin");

        ArgumentCaptor<Map<String, Object>> after = captureAuditAfter("webhook.update");
        assertFalse(after.getValue().containsKey("secretHash"), "ключ подписи не место в журнале");
        assertEquals(subscription.getSecretHash().substring(0, 8), after.getValue().get("secretFingerprint"));
    }

    @Test
    void rotateSecretIssuesNewKeyAndAuditsItSeparately() {
        WebhookSubscription subscription = subscription();
        String previousHash = subscription.getSecretHash();
        when(subscriptionRepository.findByCode("city-portal")).thenReturn(Optional.of(subscription));
        when(subscriptionRepository.save(any(WebhookSubscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WebhookSecretDto result = service.rotateSecret("city-portal", "integration-admin");

        assertNotEquals(previousHash, subscription.getSecretHash(), "ключ обязан смениться");
        assertEquals(WebhookSignature.hashSecret(result.secret()), subscription.getSecretHash());
        verify(auditService).record(eq("integration-admin"), eq("webhook.rotate_secret"),
                eq("webhook_subscription"), eq("city-portal"), any(), any());
    }

    @Test
    void unknownEventTypeIsRejectedBeforeSaving() {
        when(subscriptionRepository.existsByCode("city-portal")).thenReturn(false);
        WebhookCreateRequest request = new WebhookCreateRequest("city-portal", "Портал",
                "https://portal.example/hook", List.of("alert_published", "volcano_erupted"), true, 60);

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> service.createSubscription(request, "admin"));

        assertEquals("webhook.event_type_invalid", error.getCode());
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void privateWebhookTargetIsRejectedBeforeSaving() {
        when(subscriptionRepository.existsByCode("city-portal")).thenReturn(false);
        WebhookCreateRequest request = new WebhookCreateRequest("city-portal", "Portal",
                "https://127.0.0.1/hook", List.of("alert_published"), true, 60);

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> service.createSubscription(request, "admin"));

        assertEquals("webhook.target_url_unsafe", error.getCode());
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void duplicateCodeIsRejected() {
        when(subscriptionRepository.existsByCode("city-portal")).thenReturn(true);

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> service.createSubscription(createRequest(), "admin"));

        assertEquals("webhook.code_exists", error.getCode());
    }

    @Test
    void deliveryQueueWithoutFilterShowsOnlyFailuresAndDlq() {
        when(deliveryRepository.findByStatusIn(eq(WebhookDeliveryStatus.FAILURES), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(deadDelivery())));

        var queue = service.listDeliveries(null, 0, 50).items();

        assertEquals(1, queue.size());
        assertEquals("dead", queue.get(0).status());
        assertEquals("connection refused", queue.get(0).lastError());
        assertEquals("alert_published", queue.get(0).eventType(), "оператору важно, ЧТО не доехало");
        assertEquals("trace-1", queue.get(0).traceId());
        assertTrue(queue.get(0).retryable());
    }

    @Test
    void deliveryQueueSurvivesEventPurgedByRetention() {
        when(deliveryRepository.findByStatusIn(eq(WebhookDeliveryStatus.FAILURES), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(deadDelivery())));

        var queue = service.listDeliveries(null, 0, 50).items();

        assertEquals(1, queue.size(), "строка очереди обязана остаться видимой");
        assertEquals("alert_published", queue.get(0).eventType(),
                "immutable snapshot не зависит от retention outbox");
        assertEquals("dead", queue.get(0).status());
    }

    /**
     * Границы страницы зажимаются, а не отвергаются: page=-1 и size=100000 —
     * опечатка в адресной строке, и первая страница полезнее 400. Потолок
     * обязателен: без него ?size= вернул бы эндпоинт к полной выдаче.
     */
    @Test
    void deliveryQueueClampsPageAndSizeToSafeBounds() {
        when(deliveryRepository.findByStatusIn(eq(WebhookDeliveryStatus.FAILURES), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.listDeliveries(null, -5, 100_000);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(deliveryRepository).findByStatusIn(eq(WebhookDeliveryStatus.FAILURES), captor.capture());
        assertEquals(0, captor.getValue().getPageNumber());
        assertEquals(AdminSupport.MAX_PAGE_SIZE, captor.getValue().getPageSize());
    }

    @Test
    void deliveryQueueSizeBelowOneFallsBackToOneRow() {
        when(deliveryRepository.findByStatusIn(eq(WebhookDeliveryStatus.FAILURES), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.listDeliveries(null, 0, 0);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(deliveryRepository).findByStatusIn(eq(WebhookDeliveryStatus.FAILURES), captor.capture());
        assertEquals(1, captor.getValue().getPageSize());
    }

    /**
     * Сортировка обязана заканчиваться уникальным полем: у пачки доставок одного
     * события одинаковый updated_at, и без тай-брейка строка с границы страниц
     * попала бы в обе страницы либо ни в одну.
     */
    @Test
    void deliveryQueueSortsFreshFirstWithStableTieBreaker() {
        when(deliveryRepository.findByStatusIn(eq(WebhookDeliveryStatus.FAILURES), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.listDeliveries(null, 0, 50);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(deliveryRepository).findByStatusIn(eq(WebhookDeliveryStatus.FAILURES), captor.capture());
        Sort sort = captor.getValue().getSort();
        assertEquals(Sort.Direction.DESC, sort.getOrderFor("updatedAt").getDirection());
        assertEquals(Sort.Direction.ASC, sort.getOrderFor("code").getDirection());
    }

    /** Пустая страница за концом очереди — это пустой items, а не ошибка. */
    @Test
    void deliveryQueuePastLastPageIsEmptyAndKeepsTotals() {
        when(deliveryRepository.findByStatusIn(eq(WebhookDeliveryStatus.FAILURES), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(9, 50), 12));

        var page = service.listDeliveries(null, 9, 50);

        assertTrue(page.items().isEmpty());
        assertEquals(9, page.page());
        assertEquals(50, page.size());
        assertEquals(12, page.totalElements());
    }

    @Test
    void deliveryQueueWithStatusFilterIsPagedToo() {
        when(deliveryRepository.findByStatus(eq(WebhookDeliveryStatus.DEAD), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(deadDelivery()), PageRequest.of(0, 10), 31));

        var page = service.listDeliveries("dead", 0, 10);

        assertEquals(1, page.items().size());
        assertEquals(31, page.totalElements());
        assertEquals(4, page.totalPages());
    }

    @Test
    void manualRetryReturnsDeadDeliveryToPendingAndResetsSchedule() {
        WebhookDelivery delivery = deadDelivery();
        when(deliveryRepository.findByCode("WHD-1")).thenReturn(Optional.of(delivery));
        when(deliveryRepository.save(delivery)).thenReturn(delivery);
        var result = service.retryDelivery("WHD-1", "duty-operator");

        assertEquals("pending", result.status());
        assertEquals(NOW.toInstant(), result.nextAttemptAt(),
                "оператор ждёт результата сразу, а не после экспоненциальной паузы");
        assertEquals(1, result.attempts(), "счётчик попыток не обнуляется — это история");
        verify(auditService).record(eq("duty-operator"), eq("webhook_delivery.retry"),
                eq("webhook_delivery"), eq("WHD-1"), any(), any());
    }

    @Test
    void manualRetryOfDeliveredWebhookIsRejected() {
        WebhookDelivery delivery = delivery();
        delivery.claim(UUID.randomUUID(), NOW.plusSeconds(30));
        delivery.markSent(200);
        when(deliveryRepository.findByCode("WHD-1")).thenReturn(Optional.of(delivery));

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> service.retryDelivery("WHD-1", "duty-operator"));

        assertEquals("webhook_delivery.retry_not_allowed", error.getCode());
        verify(deliveryRepository, never()).save(any());
    }

    @Test
    void retryOfUnknownDeliveryReturnsDomainNotFound() {
        when(deliveryRepository.findByCode("WHD-404")).thenReturn(Optional.empty());

        NotFoundException error = assertThrows(NotFoundException.class,
                () -> service.retryDelivery("WHD-404", "duty-operator"));

        assertEquals("webhook_delivery.not_found", error.getCode());
    }

    @Test
    void deleteKeepsDeliveryHistoryAndAuditsRemoval() {
        WebhookSubscription subscription = subscription();
        when(subscriptionRepository.findByCode("city-portal")).thenReturn(Optional.of(subscription));

        service.deleteSubscription("city-portal", "integration-admin");

        verify(subscriptionRepository).delete(subscription);
        verify(deliveryRepository, never()).deleteAll(any());
        verify(auditService).record(eq("integration-admin"), eq("webhook.delete"),
                eq("webhook_subscription"), eq("city-portal"), any(), isNull());
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<Map<String, Object>> captureAuditAfter(String action) {
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(auditService).record(any(), eq(action), any(), any(), any(), captor.capture());
        return captor;
    }

    private static WebhookCreateRequest createRequest() {
        return new WebhookCreateRequest("city-portal", "Городской портал",
                "https://portal.example/hook", List.of("alert_published", "incident_opened"), true, 60);
    }

    private static WebhookUpdateRequest updateRequest() {
        return new WebhookUpdateRequest("Городской портал (2)", "https://portal.example/hook2",
                List.of("alert_published"), true, 30);
    }

    private static WebhookSubscription subscription() {
        return new WebhookSubscription(UUID.randomUUID(), "city-portal", "Городской портал",
                "https://portal.example/hook", WebhookSignature.hashSecret("secret-value"),
                List.of(WebhookEventType.ALERT_PUBLISHED.code()), true, 60, "admin");
    }

    private static WebhookDelivery delivery() {
        return new WebhookDelivery(UUID.randomUUID(), "WHD-1", EVENT_ID, "city-portal",
                "alert_published", "alert", "ALERT-1", "trace-1", "{\"code\":\"ALERT-1\"}", NOW);
    }

    private static WebhookDelivery deadDelivery() {
        WebhookDelivery delivery = delivery();
        delivery.claim(UUID.randomUUID(), NOW.plusSeconds(30));
        delivery.markDead("connection refused", null);
        return delivery;
    }

}
