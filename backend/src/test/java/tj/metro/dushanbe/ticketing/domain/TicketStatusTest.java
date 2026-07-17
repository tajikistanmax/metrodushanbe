package tj.metro.dushanbe.ticketing.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TicketStatusTest {

    private final TicketStatus.Persistence converter = new TicketStatus.Persistence();

    @Test
    void codesAreLowercaseAndMatchMigrationCheckConstraint() {
        // Порядок и состав обязаны совпадать с chk_ticket_status в V023.
        assertEquals(List.of("issued", "active", "used", "expired", "refunded", "blocked"),
                TicketStatus.codes());
    }

    @Test
    void usedTicketCannotReturnToActive() {
        assertFalse(TicketStatus.USED.canMoveTo(TicketStatus.ACTIVE));
        assertFalse(TicketStatus.USED.canMoveTo(TicketStatus.ISSUED));
    }

    @Test
    void refundedIsTerminal() {
        assertTrue(TicketStatus.REFUNDED.terminal());
        assertTrue(TicketStatus.REFUNDED.allowedTransitions().isEmpty());
    }

    @Test
    void expiredAndBlockedAreTerminal() {
        assertTrue(TicketStatus.EXPIRED.terminal());
        assertTrue(TicketStatus.BLOCKED.terminal());
    }

    @Test
    void blockedIsReachableFromEveryNonTerminalStatus() {
        for (TicketStatus status : TicketStatus.values()) {
            if (status.terminal()) {
                continue;
            }
            assertTrue(status.canMoveTo(TicketStatus.BLOCKED),
                    "из " + status.code() + " должна быть возможна блокировка");
        }
    }

    @Test
    void onlyIssuedAndActiveAreValidatable() {
        assertTrue(TicketStatus.ISSUED.validatable());
        assertTrue(TicketStatus.ACTIVE.validatable());
        assertFalse(TicketStatus.USED.validatable());
        assertFalse(TicketStatus.EXPIRED.validatable());
        assertFalse(TicketStatus.REFUNDED.validatable());
        assertFalse(TicketStatus.BLOCKED.validatable());
    }

    @Test
    void issuedTicketCanBeRefundedWithoutBeingValidated() {
        assertTrue(TicketStatus.ISSUED.canMoveTo(TicketStatus.REFUNDED));
        assertTrue(TicketStatus.ACTIVE.canMoveTo(TicketStatus.REFUNDED));
    }

    @Test
    void fromCodeIsCaseInsensitiveAndRejectsUnknown() {
        assertEquals(Optional.of(TicketStatus.ACTIVE), TicketStatus.fromCode("ACTIVE"));
        assertEquals(Optional.of(TicketStatus.ACTIVE), TicketStatus.fromCode("active"));
        assertEquals(Optional.empty(), TicketStatus.fromCode("paid"));
        assertEquals(Optional.empty(), TicketStatus.fromCode(null));
    }

    @Test
    void converterRoundTripsThroughLowercaseCode() {
        assertEquals("blocked", converter.convertToDatabaseColumn(TicketStatus.BLOCKED));
        assertEquals(TicketStatus.BLOCKED, converter.convertToEntityAttribute("blocked"));
        assertEquals(null, converter.convertToDatabaseColumn(null));
        assertEquals(null, converter.convertToEntityAttribute(null));
    }

    @Test
    void converterFailsLoudlyOnUnknownDatabaseValue() {
        // Молчаливый null здесь означал бы билет без статуса — лучше упасть.
        assertThrows(IllegalStateException.class, () -> converter.convertToEntityAttribute("paid"));
    }
}
