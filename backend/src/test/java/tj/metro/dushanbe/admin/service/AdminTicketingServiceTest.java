package tj.metro.dushanbe.admin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tj.metro.dushanbe.admin.web.dto.BlocklistCreateRequest;
import tj.metro.dushanbe.admin.web.dto.TicketRefundCommand;
import tj.metro.dushanbe.audit.service.AuditService;
import tj.metro.dushanbe.common.error.BadRequestException;
import tj.metro.dushanbe.common.error.NotFoundException;
import tj.metro.dushanbe.ticketing.domain.BlocklistSubjectType;
import tj.metro.dushanbe.ticketing.domain.Ticket;
import tj.metro.dushanbe.ticketing.domain.TicketBlocklistEntry;
import tj.metro.dushanbe.ticketing.domain.TicketKind;
import tj.metro.dushanbe.ticketing.domain.TicketStatus;
import tj.metro.dushanbe.ticketing.repository.PaymentRepository;
import tj.metro.dushanbe.ticketing.repository.TicketBlocklistRepository;
import tj.metro.dushanbe.ticketing.repository.TicketRepository;
import tj.metro.dushanbe.ticketing.service.TicketTokens;
import tj.metro.dushanbe.ticketing.service.TicketingService;
import tj.metro.dushanbe.ticketing.web.dto.RefundDto;

class AdminTicketingServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-17T10:00:00Z");

    private final TicketRepository ticketRepository = mock(TicketRepository.class);
    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final TicketBlocklistRepository blocklistRepository = mock(TicketBlocklistRepository.class);
    private final TicketingService ticketingService = mock(TicketingService.class);
    private final AuditService auditService = mock(AuditService.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private final AdminTicketingService service = new AdminTicketingService(ticketRepository,
            paymentRepository, blocklistRepository, ticketingService, auditService, clock);

    @BeforeEach
    void echoSaves() {
        when(blocklistRepository.save(any(TicketBlocklistEntry.class)))
                .thenAnswer(i -> i.getArgument(0));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void manualRefundDelegatesToTicketingServiceAllowingUsedTickets() {
        RefundDto expected = new RefundDto("RFN-2026-0001", "PAY-2026-0001",
                new BigDecimal("3.00"), "TJS", "completed", "сбой турникета", null,
                "op-1", true, NOW);
        when(ticketingService.refundTicket("TKT-1", "сбой турникета", "op-1", true))
                .thenReturn(expected);

        RefundDto result = service.refund("TKT-1", new TicketRefundCommand("сбой турникета"), "op-1");

        assertEquals(expected, result);
        // Правила возврата живут в одном месте: админский контур их не дублирует.
        verify(ticketingService).refundTicket("TKT-1", "сбой турникета", "op-1", true);
    }

    @Test
    void blockingTicketBlocksItImmediatelyAndAudits() {
        Ticket ticket = ticket("TKT-1", "device-1");
        when(ticketRepository.findByCode("TKT-1")).thenReturn(Optional.of(ticket));

        var entry = service.addToBlocklist(
                new BlocklistCreateRequest("ticket", "TKT-1", "подозрение на подделку"), "op-1");

        assertEquals("ticket", entry.subjectType());
        assertEquals("TKT-1", entry.subjectCode());
        assertEquals(TicketStatus.BLOCKED, ticket.getStatus(),
                "блокировка применяется немедленно, а не только к будущим валидациям");
        verify(auditService).record(eq("op-1"), eq("blocklist.create"), eq("ticket_blocklist"),
                eq(entry.code()), isNull(), any());
        verify(auditService).record(eq("op-1"), eq("ticket.block"), eq("ticket"),
                eq("TKT-1"), any(), any());
    }

    @Test
    void blockingRiderBlocksAllOfTheirNonTerminalTickets() {
        Ticket active = ticket("TKT-1", "device-1");
        Ticket alreadyRefunded = ticket("TKT-2", "device-1");
        alreadyRefunded.refund();
        when(ticketRepository.findByRiderRef("device-1"))
                .thenReturn(List.of(active, alreadyRefunded));

        service.addToBlocklist(new BlocklistCreateRequest("rider", "device-1", "фрод"), "op-1");

        assertEquals(TicketStatus.BLOCKED, active.getStatus());
        assertEquals(TicketStatus.REFUNDED, alreadyRefunded.getStatus(),
                "терминальный статус не переписывается блокировкой");
    }

    @Test
    void blockingTokenStoresHashAndNeverThePlainToken() {
        service.addToBlocklist(new BlocklistCreateRequest("token", "secret-token", "утёк"), "op-1");

        var captor = org.mockito.ArgumentCaptor.forClass(TicketBlocklistEntry.class);
        verify(blocklistRepository).save(captor.capture());
        TicketBlocklistEntry saved = captor.getValue();

        assertEquals(TicketTokens.hash("secret-token"), saved.getSubjectCode());
        assertFalse(saved.getSubjectCode().contains("secret-token"),
                "открытый токен не должен попасть ни в БД, ни в аудит");
    }

    @Test
    void blockingTokenDoesNotTouchTicketsBecauseTokenMayNotMatchAny() {
        service.addToBlocklist(new BlocklistCreateRequest("token", "secret-token", "утёк"), "op-1");

        verify(ticketRepository, never()).save(any());
    }

    @Test
    void blockingUnknownTicketReturnsDomainNotFound() {
        when(ticketRepository.findByCode("NOPE")).thenReturn(Optional.empty());

        NotFoundException error = assertThrows(NotFoundException.class, () -> service.addToBlocklist(
                new BlocklistCreateRequest("ticket", "NOPE", "причина"), "op-1"));

        assertEquals("ticket.not_found", error.getCode());
    }

    @Test
    void duplicateBlocklistSubjectIsRejected() {
        when(blocklistRepository.existsBySubjectTypeAndSubjectCode(
                BlocklistSubjectType.RIDER, "device-1")).thenReturn(true);

        BadRequestException error = assertThrows(BadRequestException.class, () -> service.addToBlocklist(
                new BlocklistCreateRequest("rider", "device-1", "фрод"), "op-1"));

        assertEquals("blocklist.subject_exists", error.getCode());
        verify(blocklistRepository, never()).save(any());
    }

    @Test
    void unknownSubjectTypeIsRejected() {
        BadRequestException error = assertThrows(BadRequestException.class, () -> service.addToBlocklist(
                new BlocklistCreateRequest("passport", "AB123", "причина"), "op-1"));

        assertEquals("blocklist.subject_type_invalid", error.getCode());
    }

    @Test
    void removingBlocklistEntryDeletesItAndAuditsWithNullAfter() {
        TicketBlocklistEntry entry = new TicketBlocklistEntry(UUID.randomUUID(), "BLK-2026-0001",
                BlocklistSubjectType.RIDER, "device-1", "фрод", "op-1");
        when(blocklistRepository.findByCode("BLK-2026-0001")).thenReturn(Optional.of(entry));

        service.removeFromBlocklist("BLK-2026-0001", "op-2");

        verify(blocklistRepository).delete(entry);
        verify(auditService).record(eq("op-2"), eq("blocklist.delete"), eq("ticket_blocklist"),
                eq("BLK-2026-0001"), any(), isNull());
    }

    @Test
    void removingUnknownBlocklistEntryReturnsDomainNotFound() {
        when(blocklistRepository.findByCode("NOPE")).thenReturn(Optional.empty());

        NotFoundException error = assertThrows(NotFoundException.class,
                () -> service.removeFromBlocklist("NOPE", "op-1"));

        assertEquals("blocklist.not_found", error.getCode());
    }

    @Test
    void listTicketsRejectsUnknownStatusFilter() {
        BadRequestException error = assertThrows(BadRequestException.class,
                () -> service.listTickets("paid"));

        assertEquals("ticket.status_invalid", error.getCode());
    }

    @Test
    void listPaymentsRejectsUnknownStatusFilter() {
        BadRequestException error = assertThrows(BadRequestException.class,
                () -> service.listPayments("settled"));

        assertEquals("payment.status_invalid", error.getCode());
    }

    @Test
    void listTicketsWithoutFilterReturnsEverythingNewestFirst() {
        when(ticketRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(ticket("TKT-2", null), ticket("TKT-1", null)));

        var result = service.listTickets(null);

        assertEquals(List.of("TKT-2", "TKT-1"), result.stream().map(t -> t.code()).toList());
    }

    private static Ticket ticket(String code, String riderRef) {
        return new Ticket(UUID.randomUUID(), code, "DEMO-SINGLE", TicketKind.SINGLE, "all",
                riderRef, TicketTokens.hash(code), OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC),
                OffsetDateTime.ofInstant(NOW.plusSeconds(3600), ZoneOffset.UTC),
                new BigDecimal("3.00"), "TJS", true);
    }
}
