package tj.metro.dushanbe.integration.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WebhookDeliveryTest {

    private static final OffsetDateTime NOW =
            OffsetDateTime.of(2026, 7, 17, 10, 0, 0, 0, ZoneOffset.UTC);

    @Test
    void newDeliveryStartsPendingWithoutAttempts() {
        WebhookDelivery delivery = delivery();

        assertEquals(WebhookDeliveryStatus.PENDING, delivery.getStatus());
        assertEquals(0, delivery.getAttempts());
        assertEquals(NOW, delivery.getNextAttemptAt());
        assertNull(delivery.getLastError());
    }

    @Test
    void claimMovesDeliveryToProcessingAndRemovesDueSchedule() {
        WebhookDelivery delivery = delivery();
        UUID token = UUID.randomUUID();

        delivery.claim(token, NOW.plusSeconds(30));

        assertEquals(WebhookDeliveryStatus.PROCESSING, delivery.getStatus());
        assertEquals(token, delivery.getClaimToken());
        assertEquals(NOW.plusSeconds(30), delivery.getClaimUntil());
        assertNull(delivery.getNextAttemptAt());
    }

    @Test
    void pendingDeliveryCannotBeCompletedWithoutClaim() {
        WebhookDelivery delivery = delivery();

        assertThrows(IllegalStateException.class, () -> delivery.markSent(200));
    }

    @Test
    void markFailedCountsAttemptAndSchedulesRetry() {
        WebhookDelivery delivery = delivery();
        claim(delivery);

        delivery.markFailed("503 Service Unavailable", 503, NOW.plusSeconds(30));

        assertEquals(WebhookDeliveryStatus.FAILED, delivery.getStatus());
        assertEquals(1, delivery.getAttempts());
        assertEquals(NOW.plusSeconds(30), delivery.getNextAttemptAt());
        assertEquals(503, delivery.getResponseStatus());
        assertEquals("503 Service Unavailable", delivery.getLastError());
    }

    @Test
    void markDeadClearsScheduleSoDispatcherStopsPickingItUp() {
        WebhookDelivery delivery = delivery();
        claim(delivery);
        delivery.markFailed("timeout", null, NOW.plusSeconds(30));
        claim(delivery);

        delivery.markDead("timeout", null);

        assertEquals(WebhookDeliveryStatus.DEAD, delivery.getStatus());
        assertEquals(2, delivery.getAttempts());
        assertNull(delivery.getNextAttemptAt(), "мёртвая доставка не должна попадать в выборку due");
    }

    @Test
    void markSentDropsScheduleButKeepsFailureHistory() {
        WebhookDelivery delivery = delivery();
        claim(delivery);
        delivery.markFailed("502 Bad Gateway", 502, NOW.plusSeconds(30));
        claim(delivery);

        delivery.markSent(200);

        assertEquals(WebhookDeliveryStatus.SENT, delivery.getStatus());
        assertEquals(2, delivery.getAttempts());
        assertEquals(200, delivery.getResponseStatus());
        assertNull(delivery.getNextAttemptAt());
        assertEquals("502 Bad Gateway", delivery.getLastError(),
                "история провалов нужна оператору и после успеха");
    }

    @Test
    void requeueReturnsDeadDeliveryToQueueWithoutSpendingAttempt() {
        WebhookDelivery delivery = delivery();
        claim(delivery);
        delivery.markDead("connection refused", null);

        delivery.requeue(NOW.plusHours(1));

        assertEquals(WebhookDeliveryStatus.PENDING, delivery.getStatus());
        assertEquals(1, delivery.getAttempts(), "ручной повтор — не попытка доставки");
        assertEquals(NOW.plusHours(1), delivery.getNextAttemptAt());
        assertEquals("connection refused", delivery.getLastError(),
                "причина повтора должна остаться видимой");
    }

    @Test
    void deferToMovesScheduleWithoutTouchingStatusOrAttempts() {
        WebhookDelivery delivery = delivery();
        claim(delivery);

        delivery.deferTo(NOW.plusMinutes(1));

        assertEquals(WebhookDeliveryStatus.PENDING, delivery.getStatus());
        assertEquals(0, delivery.getAttempts(), "выдержка по rate limit не тратит попытку");
        assertEquals(NOW.plusMinutes(1), delivery.getNextAttemptAt());
    }

    @Test
    void deliveredDeliveryIsTerminalAndNotRetryable() {
        WebhookDelivery delivery = delivery();
        claim(delivery);
        delivery.markSent(204);

        assertFalse(delivery.getStatus().retryable());
        assertTrue(delivery.getStatus().allowedTransitions().isEmpty());
    }

    @Test
    void failedAndDeadStatusesDemandErrorTextMatchingDbConstraint() {
        assertTrue(WebhookDeliveryStatus.FAILED.requiresError());
        assertTrue(WebhookDeliveryStatus.DEAD.requiresError());
        assertFalse(WebhookDeliveryStatus.PENDING.requiresError());
        assertFalse(WebhookDeliveryStatus.SENT.requiresError());
    }

    @Test
    void statusCodesMatchDatabaseCheckConstraint() {
        assertEquals(java.util.List.of("pending", "processing", "sent", "failed", "dead"),
                WebhookDeliveryStatus.codes());
        assertEquals(WebhookDeliveryStatus.DEAD, WebhookDeliveryStatus.fromCode("dead").orElseThrow());
        assertTrue(WebhookDeliveryStatus.fromCode("archived").isEmpty());
    }

    @Test
    void eventTypeCodesMatchDatabaseCheckConstraint() {
        assertTrue(WebhookEventType.codes().contains("alert_published"));
        assertTrue(WebhookEventType.codes().contains("incident_resolved"));
        assertEquals(WebhookEventType.TRAIN_DELAYED,
                WebhookEventType.fromCode("train_delayed").orElseThrow());
        assertTrue(WebhookEventType.fromCode("unknown_event").isEmpty());
    }

    private static WebhookDelivery delivery() {
        return new WebhookDelivery(UUID.randomUUID(), "WHD-1", UUID.randomUUID(), "city-portal",
                "alert_published", "alert", "ALERT-1", "trace-1", "{}", NOW);
    }

    private static void claim(WebhookDelivery delivery) {
        delivery.claim(UUID.randomUUID(), NOW.plusSeconds(30));
    }
}
