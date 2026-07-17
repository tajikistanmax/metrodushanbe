package tj.metro.dushanbe.ticketing.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentTest {

    @Test
    void followsAuthorizeCaptureRefundStateMachine() {
        Payment payment = payment();

        payment.authorize("provider-charge-1");
        payment.capture("TKT-1");
        payment.markRefunded();

        assertEquals(PaymentStatus.REFUNDED, payment.getStatus());
        assertEquals("provider-charge-1", payment.getProviderRef());
        assertEquals("TKT-1", payment.getTicketCode());
    }

    @Test
    void rejectsInvalidTransitionsAndMissingProviderData() {
        Payment payment = payment();

        assertThrows(IllegalStateException.class, () -> payment.capture("TKT-1"));
        assertThrows(IllegalArgumentException.class, () -> payment.authorize(" "));

        payment.fail("declined");
        assertThrows(IllegalStateException.class, () -> payment.authorize("provider-charge-1"));
    }

    private static Payment payment() {
        return new Payment(UUID.randomUUID(), "PAY-1", PaymentKind.PURCHASE,
                new BigDecimal("3.00"), "TJS", "demo", true);
    }
}
