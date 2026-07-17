package tj.metro.dushanbe.ticketing.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TicketTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-07-17T10:00:00Z");

    @Test
    void newTicketStartsIssuedWithZeroBalanceAndDemoMark() {
        Ticket ticket = single();

        assertEquals(TicketStatus.ISSUED, ticket.getStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(ticket.getBalanceAmount()));
        assertTrue(ticket.isDemo());
        assertNull(ticket.getUsedAt());
        assertEquals(0, ticket.getValidationCount());
    }

    @Test
    void validatingSingleTicketConsumesItAndStampsUsedAt() {
        Ticket ticket = single();

        ticket.markValidated(NOW.plusMinutes(5));

        assertEquals(TicketStatus.USED, ticket.getStatus());
        assertEquals(NOW.plusMinutes(5), ticket.getUsedAt());
        assertEquals(1, ticket.getValidationCount());
    }

    @Test
    void validatingPassKeepsItActiveAndDoesNotStampUsedAt() {
        Ticket ticket = pass();

        ticket.markValidated(NOW.plusMinutes(5));
        ticket.markValidated(NOW.plusMinutes(400));

        assertEquals(TicketStatus.ACTIVE, ticket.getStatus());
        assertNull(ticket.getUsedAt(), "проездной не гасится");
        assertEquals(2, ticket.getValidationCount());
        assertEquals(NOW.plusMinutes(400), ticket.getLastValidatedAt());
    }

    @Test
    void topUpAddsToBalanceAndMovesValidUntil() {
        Ticket ticket = pass();
        OffsetDateTime extended = ticket.getValidUntil().plusDays(30);

        ticket.applyTopUp(new BigDecimal("50.00"), extended);
        ticket.applyTopUp(new BigDecimal("25.00"), extended.plusDays(30));

        assertEquals(0, new BigDecimal("75.00").compareTo(ticket.getBalanceAmount()),
                "баланс — накопительный итог внесённого");
        assertEquals(extended.plusDays(30), ticket.getValidUntil());
    }

    @Test
    void topUpRejectsNonPositiveAmountAndShorterValidity() {
        Ticket ticket = pass();

        assertThrows(IllegalArgumentException.class,
                () -> ticket.applyTopUp(BigDecimal.ZERO, ticket.getValidUntil().plusDays(1)));
        assertThrows(IllegalArgumentException.class,
                () -> ticket.applyTopUp(BigDecimal.ONE, ticket.getValidUntil()));
        assertThrows(IllegalStateException.class,
                () -> single().applyTopUp(BigDecimal.ONE, NOW.plusDays(1)));
    }

    @Test
    void consumedSingleCannotBeValidatedOrTransitionedAgain() {
        Ticket ticket = single();
        ticket.markValidated(NOW.plusMinutes(1));

        assertThrows(IllegalStateException.class,
                () -> ticket.markValidated(NOW.plusMinutes(2)));
        assertThrows(IllegalStateException.class, ticket::expire);
    }

    @Test
    void priceIsCapturedAtPurchaseAndHasNoSetter() {
        Ticket ticket = single();

        // Цена фиксируется конструктором; изменить её нечем — это и есть гарантия,
        // что билет не поедет за подорожавшим тарифом.
        assertEquals(0, new BigDecimal("3.00").compareTo(ticket.getPriceAmount()));
        assertEquals("TJS", ticket.getPriceCurrency());
        assertEquals("DEMO-SINGLE", ticket.getFareProductCode());
    }

    @Test
    void expiredAtIsInclusiveOfValidUntilBoundary() {
        Ticket ticket = single();

        assertFalse(ticket.expiredAt(ticket.getValidUntil().minusSeconds(1)));
        assertTrue(ticket.expiredAt(ticket.getValidUntil()),
                "в момент valid_until билет уже недействителен");
        assertTrue(ticket.expiredAt(ticket.getValidUntil().plusSeconds(1)));
    }

    @Test
    void startedAtIsInclusiveOfValidFromBoundary() {
        Ticket ticket = single();

        assertFalse(ticket.startedAt(NOW.minusSeconds(1)));
        assertTrue(ticket.startedAt(NOW));
    }

    @Test
    void refundAndBlockAndExpireChangeOnlyStatus() {
        assertEquals(TicketStatus.REFUNDED, refunded().getStatus());

        Ticket blocked = single();
        blocked.block();
        assertEquals(TicketStatus.BLOCKED, blocked.getStatus());

        Ticket expired = single();
        expired.expire();
        assertEquals(TicketStatus.EXPIRED, expired.getStatus());
    }

    @Test
    void onCreateSetsTimestampsAndDoesNotOverrideCreatedAt() {
        Ticket ticket = single();
        assertNull(ticket.getCreatedAt());

        ticket.onCreate();
        OffsetDateTime createdAt = ticket.getCreatedAt();
        assertNotNull(createdAt);
        assertNotNull(ticket.getUpdatedAt());

        ticket.onCreate();
        assertEquals(createdAt, ticket.getCreatedAt());
    }

    @Test
    void onUpdateRefreshesUpdatedAt() {
        Ticket ticket = single();
        ticket.onCreate();
        OffsetDateTime before = ticket.getUpdatedAt();

        ticket.onUpdate();

        assertTrue(!ticket.getUpdatedAt().isBefore(before));
    }

    private static Ticket refunded() {
        Ticket ticket = single();
        ticket.refund();
        return ticket;
    }

    private static Ticket single() {
        return new Ticket(UUID.randomUUID(), "TKT-2026-AAAA0001", "DEMO-SINGLE",
                TicketKind.SINGLE, "all", "device-1", "hash-1", NOW, NOW.plusMinutes(90),
                new BigDecimal("3.00"), "TJS", true);
    }

    private static Ticket pass() {
        return new Ticket(UUID.randomUUID(), "TKT-2026-AAAA0002", "DEMO-MONTHLY",
                TicketKind.PASS, "all", "device-2", "hash-2", NOW, NOW.plusDays(30),
                new BigDecimal("50.00"), "TJS", true);
    }
}
