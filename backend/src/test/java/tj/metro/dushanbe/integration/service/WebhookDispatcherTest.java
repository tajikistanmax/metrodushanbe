package tj.metro.dushanbe.integration.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tj.metro.dushanbe.featureflag.service.FeatureFlagService;
import tj.metro.dushanbe.integration.config.IntegrationProperties;
import tj.metro.dushanbe.integration.domain.WebhookDelivery;
import tj.metro.dushanbe.integration.domain.WebhookDeliveryStatus;
import tj.metro.dushanbe.integration.domain.WebhookEventType;
import tj.metro.dushanbe.integration.domain.WebhookSubscription;
import tj.metro.dushanbe.integration.repository.WebhookDeliveryRepository;
import tj.metro.dushanbe.integration.repository.WebhookSubscriptionRepository;
import tj.metro.dushanbe.integration.security.WebhookTargetPolicy;

/**
 * Поведение диспетчера доставок (INT-05).
 *
 * <p>HTTP не мокается: подписчик указывает на 127.0.0.1:1, где заведомо никто не
 * слушает, — соединение отвергается мгновенно и локально. Это даёт настоящий
 * путь ошибки RestClient вместо цепочки заглушек, которая проверяла бы только
 * саму себя, и не требует ни сети, ни поднятого сервера.
 */
class WebhookDispatcherTest {

    private static final OffsetDateTime NOW =
            OffsetDateTime.of(2026, 7, 17, 10, 0, 0, 0, ZoneOffset.UTC);

    /** Порт 1 на loopback: connection refused, без ожидания и без сети. */
    private static final String UNREACHABLE = "http://127.0.0.1:1/hook";

    private final WebhookDeliveryRepository deliveryRepository = mock(WebhookDeliveryRepository.class);
    private final WebhookSubscriptionRepository subscriptionRepository =
            mock(WebhookSubscriptionRepository.class);
    private final FeatureFlagService featureFlagService = mock(FeatureFlagService.class);
    private final WebhookTargetPolicy targetPolicy = mock(WebhookTargetPolicy.class);
    private final WebhookDeliveryClaimer claimer = mock(WebhookDeliveryClaimer.class);
    private final IntegrationProperties properties = properties();
    private final Clock clock = Clock.fixed(NOW.toInstant(), ZoneOffset.UTC);

    private final WebhookDispatcher dispatcher;

    WebhookDispatcherTest() {
        when(targetPolicy.validateForDispatch(anyString()))
                .thenAnswer(invocation -> URI.create(invocation.getArgument(0)));
        dispatcher = new WebhookDispatcher(deliveryRepository, subscriptionRepository,
                featureFlagService, properties, RestClient.builder(), clock,
                targetPolicy, claimer);
    }

    @Test
    void disabledFeatureFlagKeepsDispatcherAwayFromTheQueue() {
        when(featureFlagService.isEnabled(eq("integration.webhooks"), anyBoolean())).thenReturn(false);

        dispatcher.dispatchDue();

        verifyNoInteractions(deliveryRepository);
        verifyNoInteractions(subscriptionRepository);
    }

    @Test
    void dispatchClaimsDueDeliveriesWithALease() {
        when(featureFlagService.isEnabled(eq("integration.webhooks"), anyBoolean())).thenReturn(true);
        when(claimer.claimNext(any(), any())).thenReturn(Optional.empty());

        dispatcher.dispatchDue();

        verify(claimer).claimNext(NOW, NOW.plus(properties.getClaimLease()));
    }

    @Test
    void failedAttemptIncrementsAttemptsAndMovesNextAttemptAt() {
        WebhookDelivery delivery = delivery();
        stubSubscriberAndEvent();

        deliver(delivery);

        assertEquals(WebhookDeliveryStatus.FAILED, delivery.getStatus());
        assertEquals(1, delivery.getAttempts());
        // base 30с × 2^0 = 30с от «сейчас»
        assertEquals(NOW.plusSeconds(30), delivery.getNextAttemptAt());
        assertNotNull(delivery.getLastError(), "chk_webhook_delivery_error требует причину");
        assertNull(delivery.getResponseStatus(), "до подписчика не дозвонились — HTTP-статуса нет");
        verify(deliveryRepository).save(delivery);
    }

    @Test
    void retryDelayGrowsExponentiallyUntilItHitsTheCeiling() {
        stubSubscriberAndEvent();

        assertEquals(NOW.plusSeconds(30), retryAfterAttempts(0));
        assertEquals(NOW.plusSeconds(60), retryAfterAttempts(1));
        assertEquals(NOW.plusSeconds(120), retryAfterAttempts(2));
        assertEquals(NOW.plusSeconds(240), retryAfterAttempts(3));
        // 30 × 2^4 = 480с > потолка 300с — дальше растёт только счётчик попыток
        assertEquals(NOW.plusSeconds(300), retryAfterAttempts(4));
    }

    @Test
    void exhaustedRetriesSendDeliveryToDeadLetterQueue() {
        stubSubscriberAndEvent();
        WebhookDelivery delivery = delivery();
        // maxAttempts = 6: пять провалов оставляют доставку живой…
        for (int i = 0; i < 5; i++) {
            deliver(delivery);
            assertEquals(WebhookDeliveryStatus.FAILED, delivery.getStatus(),
                    "попытка " + (i + 1) + " из 6 не должна убивать доставку");
        }

        // …а шестой исчерпывает лимит и переводит её в DLQ.
        deliver(delivery);

        assertEquals(WebhookDeliveryStatus.DEAD, delivery.getStatus());
        assertEquals(6, delivery.getAttempts());
        assertNull(delivery.getNextAttemptAt(), "мёртвая доставка больше не планируется");
        assertTrue(delivery.getStatus().retryable(), "из DLQ её обязан вытащить оператор (U-OPS-04)");
    }

    @Test
    void deletedSubscriberSendsDeliveryStraightToDlqWithoutBurningRetries() {
        when(subscriptionRepository.findByCode("city-portal")).thenReturn(Optional.empty());
        WebhookDelivery delivery = delivery();

        deliver(delivery);

        assertEquals(WebhookDeliveryStatus.DEAD, delivery.getStatus(),
                "везти некому — повторять бессмысленно");
        assertEquals(1, delivery.getAttempts());
        assertTrue(delivery.getLastError().contains("city-portal"));
        verify(targetPolicy, never()).validateForDispatch(anyString());
    }

    @Test
    void inactiveSubscriberIsNotCalledAtAll() {
        WebhookSubscription subscription = subscription(false, 60);
        when(subscriptionRepository.findByCode("city-portal")).thenReturn(Optional.of(subscription));
        WebhookDelivery delivery = delivery();

        deliver(delivery);

        assertEquals(WebhookDeliveryStatus.DEAD, delivery.getStatus());
        verify(targetPolicy, never()).validateForDispatch(anyString());
    }

    @Test
    void unsafeTargetIsTerminalAndNeverLoadsOrSendsThePayload() {
        when(subscriptionRepository.findByCode("city-portal"))
                .thenReturn(Optional.of(subscription(true, 60)));
        when(targetPolicy.validateForDispatch(anyString()))
                .thenThrow(new WebhookTargetPolicy.UnsafeTargetException("private address"));
        WebhookDelivery delivery = delivery();

        deliver(delivery);

        assertEquals(WebhookDeliveryStatus.DEAD, delivery.getStatus());
        assertTrue(delivery.getLastError().contains("Unsafe webhook target"));
        verify(deliveryRepository, never()).countBySubscriptionCodeAndUpdatedAtAfter(anyString(), any());
    }

    @Test
    void rateLimitDefersDeliveryWithoutSpendingAnAttempt() {
        when(subscriptionRepository.findByCode("city-portal"))
                .thenReturn(Optional.of(subscription(true, 10)));
        when(deliveryRepository.countBySubscriptionCodeAndUpdatedAtAfter(eq("city-portal"), any()))
                .thenReturn(10L);
        WebhookDelivery delivery = delivery();

        deliver(delivery);

        assertEquals(WebhookDeliveryStatus.PENDING, delivery.getStatus(),
                "выдержка по лимиту подписчика — не провал доставки");
        assertEquals(0, delivery.getAttempts());
        assertEquals(NOW.plusMinutes(1), delivery.getNextAttemptAt());
    }

    /** Прогоняет доставку с уже сделанными {@code attempts} провалами и отдаёт новый срок. */
    private OffsetDateTime retryAfterAttempts(int attempts) {
        WebhookDelivery delivery = delivery();
        for (int i = 0; i <= attempts; i++) {
            deliver(delivery);
        }
        return delivery.getNextAttemptAt();
    }

    private void stubSubscriberAndEvent() {
        when(subscriptionRepository.findByCode("city-portal"))
                .thenReturn(Optional.of(subscription(true, 60)));
        when(deliveryRepository.countBySubscriptionCodeAndUpdatedAtAfter(eq("city-portal"), any()))
                .thenReturn(0L);
    }

    private void deliver(WebhookDelivery delivery) {
        delivery.claim(UUID.randomUUID(), NOW.plus(properties.getClaimLease()));
        dispatcher.deliver(delivery);
    }

    private static WebhookDelivery delivery() {
        return new WebhookDelivery(UUID.randomUUID(), "WHD-1",
                UUID.fromString("11111111-1111-1111-1111-111111111111"), "city-portal",
                "alert_published", "alert", "ALERT-1", "trace-1",
                "{\"eventId\":\"11111111-1111-1111-1111-111111111111\"}", NOW);
    }

    private static WebhookSubscription subscription(boolean active, int rateLimit) {
        return new WebhookSubscription(UUID.randomUUID(), "city-portal", "Городской портал",
                UNREACHABLE, WebhookSignature.hashSecret("secret"),
                List.of(WebhookEventType.ALERT_PUBLISHED.code()), active, rateLimit, "admin");
    }

    private static IntegrationProperties properties() {
        IntegrationProperties properties = new IntegrationProperties();
        properties.setMaxAttempts(6);
        properties.setBatchSize(50);
        properties.setRetryBaseDelay(Duration.ofSeconds(30));
        properties.setRetryMaxDelay(Duration.ofSeconds(300));
        // Соединение к 127.0.0.1:1 отвергается мгновенно, но таймаут держим
        // маленьким, чтобы тест не завис, если среда ведёт себя иначе.
        properties.setTimeout(Duration.ofMillis(500));
        return properties;
    }
}
