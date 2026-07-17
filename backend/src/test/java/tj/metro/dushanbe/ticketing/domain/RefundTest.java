package tj.metro.dushanbe.ticketing.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RefundTest {

    @Test
    void completedRefundRequiresReferenceAndIsTerminal() {
        Refund refund = refund();

        assertThrows(IllegalArgumentException.class, () -> refund.complete(null));
        refund.complete("provider-refund-1");

        assertEquals(RefundStatus.COMPLETED, refund.getStatus());
        assertEquals("provider-refund-1", refund.getProviderRef());
        assertThrows(IllegalStateException.class, () -> refund.fail("late failure"));
    }

    @Test
    void failedRefundRequiresReasonAndIsTerminal() {
        Refund refund = refund();

        assertThrows(IllegalArgumentException.class, () -> refund.fail(" "));
        refund.fail("declined");

        assertEquals(RefundStatus.FAILED, refund.getStatus());
        assertThrows(IllegalStateException.class, () -> refund.complete("provider-refund-1"));
    }

    private static Refund refund() {
        return new Refund(UUID.randomUUID(), "RFN-1", "PAY-1", new BigDecimal("3.00"),
                "TJS", "customer request", "public", true);
    }
}
