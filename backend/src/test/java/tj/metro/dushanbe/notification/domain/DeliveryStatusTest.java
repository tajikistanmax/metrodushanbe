package tj.metro.dushanbe.notification.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DeliveryStatusTest {

    @Test
    void happyPathGoesPendingSentDelivered() {
        assertTrue(DeliveryStatus.PENDING.canMoveTo(DeliveryStatus.SENT));
        assertTrue(DeliveryStatus.SENT.canMoveTo(DeliveryStatus.DELIVERED));
    }

    @Test
    void anyStateCanFail() {
        // Провалиться доставка может на любом этапе, включая уже подтверждённую:
        // отбойник от канала приходит асинхронно.
        for (DeliveryStatus status : DeliveryStatus.values()) {
            if (status != DeliveryStatus.FAILED) {
                assertTrue(status.canMoveTo(DeliveryStatus.FAILED),
                        status.code() + " должен уметь провалиться");
            }
        }
    }

    @Test
    void retryReturnsFailedToPendingAndNotStraightToSent() {
        // Повтор ставит доставку в очередь, а не выдумывает факт отправки (NTF-06).
        assertTrue(DeliveryStatus.FAILED.canMoveTo(DeliveryStatus.PENDING));
        assertFalse(DeliveryStatus.FAILED.canMoveTo(DeliveryStatus.SENT));
        assertFalse(DeliveryStatus.FAILED.canMoveTo(DeliveryStatus.DELIVERED));
    }

    @Test
    void deliveredCannotBeReturnedToQueue() {
        assertFalse(DeliveryStatus.DELIVERED.canMoveTo(DeliveryStatus.PENDING));
        assertFalse(DeliveryStatus.DELIVERED.canMoveTo(DeliveryStatus.SENT));
    }

    @Test
    void pendingCannotSkipToDelivered() {
        assertFalse(DeliveryStatus.PENDING.canMoveTo(DeliveryStatus.DELIVERED));
    }

    @Test
    void onlyFailedIsRetryable() {
        assertTrue(DeliveryStatus.FAILED.retryable());
        assertFalse(DeliveryStatus.PENDING.retryable());
        assertFalse(DeliveryStatus.SENT.retryable());
        assertFalse(DeliveryStatus.DELIVERED.retryable());
    }

    @Test
    void allowedTransitionsAreExposedForEveryState() {
        assertEquals(2, DeliveryStatus.PENDING.allowedTransitions().size());
        assertEquals(2, DeliveryStatus.SENT.allowedTransitions().size());
        assertEquals(1, DeliveryStatus.DELIVERED.allowedTransitions().size());
        assertEquals(1, DeliveryStatus.FAILED.allowedTransitions().size());
    }

    @Test
    void codesMatchCheckConstraintOfMigration() {
        // Значения обязаны совпадать с chk_notification_delivery_status (V022).
        assertEquals(List.of("pending", "sent", "delivered", "failed"), DeliveryStatus.codes());
    }

    @Test
    void fromCodeIsCaseInsensitiveAndEmptyForUnknown() {
        assertEquals(Optional.of(DeliveryStatus.DELIVERED), DeliveryStatus.fromCode("DELIVERED"));
        assertEquals(Optional.of(DeliveryStatus.PENDING), DeliveryStatus.fromCode("pending"));
        assertEquals(Optional.empty(), DeliveryStatus.fromCode("bounced"));
        assertEquals(Optional.empty(), DeliveryStatus.fromCode(null));
    }

    @Test
    void converterRoundTripsThroughLowercaseCode() {
        var converter = new DeliveryStatus.Persistence();

        assertEquals("failed", converter.convertToDatabaseColumn(DeliveryStatus.FAILED));
        assertEquals(DeliveryStatus.FAILED, converter.convertToEntityAttribute("failed"));
        assertNull(converter.convertToDatabaseColumn(null));
        assertNull(converter.convertToEntityAttribute(null));
    }

    @Test
    void converterRejectsValueOutsideCheckConstraint() {
        var converter = new DeliveryStatus.Persistence();

        assertThrows(IllegalStateException.class, () -> converter.convertToEntityAttribute("bounced"));
    }
}
