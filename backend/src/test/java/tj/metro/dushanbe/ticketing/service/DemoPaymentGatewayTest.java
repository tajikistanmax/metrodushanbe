package tj.metro.dushanbe.ticketing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DemoPaymentGatewayTest {

    private final DemoPaymentGateway gateway = new DemoPaymentGateway();

    @Test
    void providerCodeMatchesMigrationCheckConstraint() {
        // chk_payment_provider допускает только 'demo'.
        assertEquals("demo", gateway.providerCode());
        assertTrue(gateway.demo());
    }

    @Test
    void chargeIsApprovedByDefaultAndDeterministic() {
        var first = gateway.charge("PAY-1", new BigDecimal("3.00"), "TJS", null);
        var second = gateway.charge("PAY-1", new BigDecimal("3.00"), "TJS", null);

        assertTrue(first.approved());
        assertNotNull(first.providerRef());
        assertNull(first.failureReason());
        assertEquals(first.providerRef(), second.providerRef(),
                "один и тот же вход обязан давать один и тот же ответ");
    }

    @Test
    void chargeIsDeclinedByExplicitScenario() {
        var outcome = gateway.charge("PAY-2", new BigDecimal("3.00"), "TJS", "decline");

        assertFalse(outcome.approved());
        assertNull(outcome.providerRef());
        assertNotNull(outcome.failureReason(), "chk_payment_error требует причину отказа");
    }

    @Test
    void declineScenarioIsCaseInsensitiveAndTrimmed() {
        assertFalse(gateway.charge("PAY-3", BigDecimal.ONE, "TJS", " DECLINE ").approved());
    }

    @Test
    void approveScenarioIsExplicitlyApproved() {
        assertTrue(gateway.charge("PAY-4", BigDecimal.ONE, "TJS", "approve").approved());
    }

    @Test
    void magicAmountIsDeclinedRegardlessOfScale() {
        assertFalse(gateway.charge("PAY-5", new BigDecimal("13.13"), "TJS", null).approved());
        // compareTo, а не equals: 13.130 — та же сумма.
        assertFalse(gateway.charge("PAY-6", new BigDecimal("13.130"), "TJS", null).approved());
        assertTrue(gateway.charge("PAY-7", new BigDecimal("13.14"), "TJS", null).approved());
    }

    @Test
    void refundIsAlwaysApprovedInDemo() {
        var outcome = gateway.refund("RFN-1", "DEMO-CHG-PAY-1", new BigDecimal("3.00"), "TJS");

        assertTrue(outcome.approved());
        assertNotNull(outcome.providerRef());
    }
}
