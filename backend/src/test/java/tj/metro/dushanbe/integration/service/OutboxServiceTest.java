package tj.metro.dushanbe.integration.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tj.metro.dushanbe.integration.domain.OutboxEvent;
import tj.metro.dushanbe.integration.domain.WebhookDelivery;
import tj.metro.dushanbe.integration.domain.WebhookEventType;
import tj.metro.dushanbe.integration.domain.WebhookSubscription;
import tj.metro.dushanbe.integration.repository.OutboxEventRepository;
import tj.metro.dushanbe.integration.repository.WebhookDeliveryRepository;
import tj.metro.dushanbe.integration.repository.WebhookSubscriptionRepository;

class OutboxServiceTest {

    private final OutboxEventRepository outboxRepository = mock(OutboxEventRepository.class);
    private final WebhookSubscriptionRepository subscriptionRepository =
            mock(WebhookSubscriptionRepository.class);
    private final WebhookDeliveryRepository deliveryRepository = mock(WebhookDeliveryRepository.class);
    private final OutboxService service = new OutboxService(outboxRepository, subscriptionRepository,
            deliveryRepository, Clock.fixed(Instant.parse("2026-07-17T10:00:00Z"), ZoneOffset.UTC),
            new ObjectMapper().findAndRegisterModules());

    @Test
    void fanoutStoresExactImmutablePayloadSnapshot() {
        when(outboxRepository.save(any(OutboxEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(subscriptionRepository.findByActiveTrue()).thenReturn(List.of(subscription()));
        when(deliveryRepository.save(any(WebhookDelivery.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OutboxEvent event = service.publish(WebhookEventType.ALERT_PUBLISHED,
                "alert", "ALERT-1", Map.of("severity", "critical"));

        ArgumentCaptor<WebhookDelivery> captor = ArgumentCaptor.forClass(WebhookDelivery.class);
        verify(deliveryRepository).save(captor.capture());
        WebhookDelivery delivery = captor.getValue();
        assertEquals(event.getEventId(), delivery.getEventId());
        assertEquals("alert_published", delivery.getEventTypeSnapshot());
        assertEquals("alert", delivery.getAggregateTypeSnapshot());
        assertEquals("ALERT-1", delivery.getAggregateCodeSnapshot());
        assertTrue(delivery.getBodySnapshot().contains(event.getEventId().toString()));
        assertTrue(delivery.getBodySnapshot().contains("critical"));
    }

    private static WebhookSubscription subscription() {
        return new WebhookSubscription(UUID.randomUUID(), "city-portal", "City portal",
                "https://portal.example/hook", WebhookSignature.hashSecret("secret"),
                List.of("alert_published"), true, 60, "admin");
    }
}
