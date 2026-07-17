package tj.metro.dushanbe.notification.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationDeliveryTest {

    private static final OffsetDateTime AT = OffsetDateTime.parse("2026-07-17T10:00:00Z");

    @Test
    void newDeliveryStartsPendingWithoutAttempts() {
        var delivery = delivery(NotificationChannel.IN_APP);

        assertEquals(DeliveryStatus.PENDING, delivery.getStatus());
        assertEquals(0, delivery.getAttempts());
        assertNull(delivery.getSentAt());
        assertNull(delivery.getDeliveredAt());
        assertNull(delivery.getLastError());
    }

    @Test
    void markSentCountsTheAttemptAndStampsTime() {
        var delivery = delivery(NotificationChannel.EMAIL);

        delivery.markSent(AT);

        assertEquals(DeliveryStatus.SENT, delivery.getStatus());
        assertEquals(1, delivery.getAttempts());
        assertEquals(AT, delivery.getSentAt());
    }

    @Test
    void markDeliveredStampsConfirmation() {
        var delivery = delivery(NotificationChannel.IN_APP);
        delivery.markSent(AT);

        delivery.markDelivered(AT.plusSeconds(5));

        assertEquals(DeliveryStatus.DELIVERED, delivery.getStatus());
        assertEquals(AT.plusSeconds(5), delivery.getDeliveredAt());
    }

    @Test
    void pendingCannotJumpStraightToDelivered() {
        var delivery = delivery(NotificationChannel.IN_APP);

        assertThrows(IllegalStateException.class, () -> delivery.markDelivered(AT));
    }

    @Test
    void deliveredTimestampCannotPrecedeSentTimestamp() {
        var delivery = delivery(NotificationChannel.IN_APP);
        delivery.markSent(AT);

        assertThrows(IllegalArgumentException.class,
                () -> delivery.markDelivered(AT.minusSeconds(1)));
    }

    @Test
    void markFailedRecordsReasonAndCountsTheAttempt() {
        var delivery = delivery(NotificationChannel.SMS);

        delivery.markFailed("Провайдер вернул 503", AT);

        assertEquals(DeliveryStatus.FAILED, delivery.getStatus());
        assertEquals(1, delivery.getAttempts());
        assertEquals("Провайдер вернул 503", delivery.getLastError());
    }

    @Test
    void markFailedWithoutReasonIsRejected() {
        var delivery = delivery(NotificationChannel.SMS);

        // chk_notification_delivery_error: провал без причины бесполезен для OPS-04
        // и всё равно не пройдёт в БД — ловим здесь.
        assertThrows(IllegalArgumentException.class, () -> delivery.markFailed(null, AT));
        assertThrows(IllegalArgumentException.class, () -> delivery.markFailed("   ", AT));
        assertEquals(DeliveryStatus.PENDING, delivery.getStatus());
        assertEquals(0, delivery.getAttempts());
    }

    @Test
    void repeatedFailuresAccumulateAttemptsAndKeepLatestReason() {
        var delivery = delivery(NotificationChannel.PUSH);

        delivery.markFailed("APNs недоступен", AT);
        delivery.retry(AT.plusMinutes(1));
        delivery.markFailed("APNs всё ещё недоступен", AT.plusMinutes(2));

        assertEquals(2, delivery.getAttempts());
        assertEquals("APNs всё ещё недоступен", delivery.getLastError());
    }

    @Test
    void retryReturnsToQueueAndClearsSendMarks() {
        var delivery = delivery(NotificationChannel.EMAIL);
        delivery.markSent(AT);
        delivery.markDelivered(AT);
        delivery.markFailed("Отбойник: ящик не существует", AT);

        delivery.retry(AT.plusMinutes(5));

        // Ожидающая повтора доставка не может числиться отправленной/доставленной.
        assertEquals(DeliveryStatus.PENDING, delivery.getStatus());
        assertNull(delivery.getSentAt());
        assertNull(delivery.getDeliveredAt());
        // Причина сохраняется: это история того, из-за чего понадобился повтор.
        assertEquals("Отбойник: ящик не существует", delivery.getLastError());
    }

    @Test
    void retryDoesNotInventAnAttempt() {
        var delivery = delivery(NotificationChannel.EMAIL);
        delivery.markFailed("таймаут", AT);

        delivery.retry(AT.plusMinutes(1));

        // Попытка засчитывается по факту обращения к каналу, а не по нажатию кнопки.
        assertEquals(1, delivery.getAttempts());
    }

    @Test
    void simulatedIsTrueForEveryChannelWithoutProvider() {
        // На demo-контуре реально доставляется только in_app — см. NotificationChannel.external().
        assertFalse(delivery(NotificationChannel.IN_APP).simulated());
        for (var channel : List.of(NotificationChannel.PUSH, NotificationChannel.EMAIL,
                NotificationChannel.SMS)) {
            assertTrue(delivery(channel).simulated(), channel.code() + " не имеет провайдера");
        }
    }

    @Test
    void channelIsStoredAsLowercaseCode() {
        // Значение обязано совпадать с chk_notification_delivery_channel (V022).
        assertEquals("in_app", delivery(NotificationChannel.IN_APP).getChannel());
    }

    @Test
    void onCreateSetsTimestamps() {
        var delivery = delivery(NotificationChannel.IN_APP);

        delivery.onCreate();

        assertNotNull(delivery.getCreatedAt());
        assertNotNull(delivery.getUpdatedAt());
    }

    private static NotificationDelivery delivery(NotificationChannel channel) {
        var message = new NotificationMessage(UUID.randomUUID(), "NTF-2026-001", null, null,
                NotificationType.INFO, Map.of("ru", "Привет"), Map.of("ru", "Текст"),
                List.of(channel.code()), null, "operator1");
        return new NotificationDelivery(UUID.randomUUID(), message, channel, "demo@metro.tj");
    }
}
