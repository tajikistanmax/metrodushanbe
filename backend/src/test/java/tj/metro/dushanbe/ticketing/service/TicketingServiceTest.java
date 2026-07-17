package tj.metro.dushanbe.ticketing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tj.metro.dushanbe.audit.service.AuditService;
import tj.metro.dushanbe.common.error.BadRequestException;
import tj.metro.dushanbe.common.error.ForbiddenException;
import tj.metro.dushanbe.common.error.NotFoundException;
import tj.metro.dushanbe.fare.domain.FareProduct;
import tj.metro.dushanbe.fare.repository.FareProductRepository;
import tj.metro.dushanbe.featureflag.service.FeatureFlagService;
import tj.metro.dushanbe.ticketing.domain.BlocklistSubjectType;
import tj.metro.dushanbe.ticketing.domain.Payment;
import tj.metro.dushanbe.ticketing.domain.PaymentKind;
import tj.metro.dushanbe.ticketing.domain.PaymentStatus;
import tj.metro.dushanbe.ticketing.domain.Refund;
import tj.metro.dushanbe.ticketing.domain.Ticket;
import tj.metro.dushanbe.ticketing.domain.TicketKind;
import tj.metro.dushanbe.ticketing.domain.TicketStatus;
import tj.metro.dushanbe.ticketing.repository.PaymentRepository;
import tj.metro.dushanbe.ticketing.repository.RefundRepository;
import tj.metro.dushanbe.ticketing.repository.TicketBlocklistRepository;
import tj.metro.dushanbe.ticketing.repository.TicketRepository;
import tj.metro.dushanbe.ticketing.web.dto.TicketPurchaseRequest;
import tj.metro.dushanbe.ticketing.web.dto.TicketPurchaseResponse;
import tj.metro.dushanbe.ticketing.web.dto.TicketTopUpRequest;
import tj.metro.dushanbe.ticketing.web.dto.TicketValidationDto;

class TicketingServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-17T10:00:00Z");
    private static final Map<String, String> I18N =
            Map.of("tg", "Чипта", "ru", "Билет", "en", "Ticket");

    private final TicketRepository ticketRepository = mock(TicketRepository.class);
    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final RefundRepository refundRepository = mock(RefundRepository.class);
    private final TicketBlocklistRepository blocklistRepository = mock(TicketBlocklistRepository.class);
    private final FareProductRepository fareProductRepository = mock(FareProductRepository.class);
    private final FeatureFlagService featureFlagService = mock(FeatureFlagService.class);
    private final AuditService auditService = mock(AuditService.class);
    private final PaymentGateway gateway = new DemoPaymentGateway();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private final TicketingService service = new TicketingService(ticketRepository, paymentRepository,
            refundRepository, blocklistRepository, fareProductRepository, gateway,
            featureFlagService, auditService, clock);

    @BeforeEach
    void enableFlagsAndEchoSaves() {
        when(featureFlagService.isEnabled(anyString(), anyBoolean())).thenReturn(true);
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(i -> i.getArgument(0));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
        when(refundRepository.save(any(Refund.class))).thenAnswer(i -> i.getArgument(0));
    }

    // ------------------------------- Покупка --------------------------------

    @Test
    void purchaseFixesPriceFromFareAndIssuesTicketAfterCapture() {
        when(fareProductRepository.findByCode("DEMO-SINGLE")).thenReturn(Optional.of(fare(
                "DEMO-SINGLE", new BigDecimal("3.00"), 90, true)));

        TicketPurchaseResponse response = service.purchase(
                new TicketPurchaseRequest("DEMO-SINGLE", "device-1", null));

        assertNotNull(response.ticket());
        assertEquals(0, new BigDecimal("3.00").compareTo(response.ticket().priceAmount()));
        assertEquals("TJS", response.ticket().priceCurrency());
        assertEquals("issued", response.ticket().status());
        assertEquals("single", response.ticket().kind());
        assertEquals("captured", response.payment().status());
        assertEquals("purchase", response.payment().kind());
        assertEquals(response.ticket().code(), response.payment().ticketCode());
        verify(auditService).record(eq("public"), eq("ticket.purchase"), eq("ticket"),
                eq(response.ticket().code()), isNull(), any());
    }

    @Test
    void purchasedTicketKeepsItsPriceWhenFareLaterChanges() {
        FareProduct product = fare("DEMO-SINGLE", new BigDecimal("3.00"), 90, true);
        when(fareProductRepository.findByCode("DEMO-SINGLE")).thenReturn(Optional.of(product));
        service.purchase(new TicketPurchaseRequest("DEMO-SINGLE", "device-1", null));
        Ticket issued = savedTicket();

        // Тариф подорожал вдвое и сменил категорию уже после покупки.
        product.update(I18N, I18N, new BigDecimal("6.00"), "TJS", "adult", 90, true);

        // Перечитываем билет так же, как это сделает GET /v1/tickets/{code}.
        when(ticketRepository.findByCode(issued.getCode())).thenReturn(Optional.of(issued));
        var reread = service.get(issued.getCode());

        assertEquals(0, new BigDecimal("3.00").compareTo(reread.priceAmount()),
                "цена зафиксирована на момент покупки и за тарифом не едет");
        assertEquals("all", reread.riderCategory(), "категория тоже зафиксирована");
        assertEquals("DEMO-SINGLE", reread.fareProductCode());
    }

    @Test
    void purchaseReturnsTokenExactlyOnceAndStoresOnlyItsHash() {
        when(fareProductRepository.findByCode("DEMO-SINGLE")).thenReturn(Optional.of(fare(
                "DEMO-SINGLE", new BigDecimal("3.00"), 90, true)));

        TicketPurchaseResponse response = service.purchase(
                new TicketPurchaseRequest("DEMO-SINGLE", "device-1", null));

        String token = response.token();
        assertNotNull(token);
        // В самом билете токена нет — только SHA-256 от него.
        Ticket saved = savedTicket();
        assertEquals(TicketTokens.hash(token), saved.getTokenHash());
        assertFalse(saved.getTokenHash().equals(token));
        // Повторный просмотр билета токена уже не содержит: в TicketDto его нет
        // структурно, а восстановить из хеша невозможно.
        when(ticketRepository.findByCode(saved.getCode())).thenReturn(Optional.of(saved));
        assertNotNull(service.get(saved.getCode()));
    }

    @Test
    void declinedPaymentRecordsFailedAttemptAndIssuesNoTicket() {
        when(fareProductRepository.findByCode("DEMO-SINGLE")).thenReturn(Optional.of(fare(
                "DEMO-SINGLE", new BigDecimal("3.00"), 90, true)));

        TicketPurchaseResponse response = service.purchase(
                new TicketPurchaseRequest("DEMO-SINGLE", "device-1", "decline"));

        assertNull(response.ticket(), "билет не выпускается без списания");
        assertNull(response.token());
        assertEquals("failed", response.payment().status());
        assertNotNull(response.payment().failureReason(), "chk_payment_error требует причину");
        assertNull(response.payment().ticketCode());
        verify(ticketRepository, never()).save(any());
        verify(auditService).record(eq("public"), eq("ticket.purchase_declined"), eq("payment"),
                anyString(), isNull(), any());
    }

    @Test
    void everyPurchaseIsMarkedAsDemoInResponse() {
        when(fareProductRepository.findByCode("DEMO-SINGLE")).thenReturn(Optional.of(fare(
                "DEMO-SINGLE", new BigDecimal("3.00"), 90, true)));

        TicketPurchaseResponse response = service.purchase(
                new TicketPurchaseRequest("DEMO-SINGLE", null, null));

        assertTrue(response.demo());
        assertTrue(response.ticket().demo());
        assertTrue(response.payment().demo());
        assertEquals(TicketingService.DEMO_NOTICE, response.notice());
    }

    @Test
    void monthlyFareProducesPassKind() {
        when(fareProductRepository.findByCode("DEMO-MONTHLY")).thenReturn(Optional.of(fare(
                "DEMO-MONTHLY", new BigDecimal("50.00"), 43200, true)));

        TicketPurchaseResponse response = service.purchase(
                new TicketPurchaseRequest("DEMO-MONTHLY", "device-1", null));

        assertEquals("pass", response.ticket().kind());
    }

    @Test
    void purchaseRejectsInactiveFare() {
        when(fareProductRepository.findByCode("OLD")).thenReturn(Optional.of(fare(
                "OLD", new BigDecimal("3.00"), 90, false)));

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> service.purchase(new TicketPurchaseRequest("OLD", null, null)));

        assertEquals("ticketing.fare_inactive", error.getCode());
    }

    @Test
    void purchaseRejectsUnknownFare() {
        when(fareProductRepository.findByCode("NOPE")).thenReturn(Optional.empty());

        NotFoundException error = assertThrows(NotFoundException.class,
                () -> service.purchase(new TicketPurchaseRequest("NOPE", null, null)));

        assertEquals("fare.not_found", error.getCode());
    }

    @Test
    void purchaseRejectsZeroPricedFareBecauseBenefitsCannotBeVerified() {
        when(fareProductRepository.findByCode("FREE")).thenReturn(Optional.of(fare(
                "FREE", BigDecimal.ZERO, 90, true)));

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> service.purchase(new TicketPurchaseRequest("FREE", null, null)));

        assertEquals("ticketing.fare_not_payable", error.getCode());
    }

    @Test
    void purchaseIsForbiddenWhenFeatureFlagIsOff() {
        when(featureFlagService.isEnabled(TicketingService.FLAG_PURCHASE, true)).thenReturn(false);

        ForbiddenException error = assertThrows(ForbiddenException.class,
                () -> service.purchase(new TicketPurchaseRequest("DEMO-SINGLE", null, null)));

        assertEquals("ticketing.disabled", error.getCode());
    }

    // ------------------------------ Антифрод --------------------------------

    @Test
    void purchaseIsRejectedForBlocklistedRider() {
        when(blocklistRepository.existsBySubjectTypeAndSubjectCode(
                BlocklistSubjectType.RIDER, "bad-device")).thenReturn(true);

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> service.purchase(new TicketPurchaseRequest("DEMO-SINGLE", "bad-device", null)));

        assertEquals("ticketing.rider_blocked", error.getCode());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void purchaseIsRejectedBeyondHourlyLimitForOneRider() {
        when(ticketRepository.countByRiderRefAndCreatedAtGreaterThanEqual(
                eq("device-1"), any(OffsetDateTime.class))).thenReturn(10L);

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> service.purchase(new TicketPurchaseRequest("DEMO-SINGLE", "device-1", null)));

        assertEquals("ticketing.purchase_limit", error.getCode());
        verify(paymentRepository, never()).save(any());
    }

    // ----------------------------- Валидация --------------------------------

    @Test
    void validateConsumesSingleTicketAndRejectsSecondAttempt() {
        Ticket ticket = single();
        when(ticketRepository.findByTokenHashForUpdate(TicketTokens.hash("tok"))).thenReturn(Optional.of(ticket));

        TicketValidationDto first = service.validate("tok");
        assertTrue(first.valid());
        assertEquals("used", first.status());

        TicketValidationDto second = service.validate("tok");
        assertFalse(second.valid(), "использованный билет повторно не проходит");
        assertEquals("ticket.already_used", second.reason());
    }

    @Test
    void validateRejectsBlocklistedTicketAndBlocksItOnTheSpot() {
        Ticket ticket = single();
        when(ticketRepository.findByTokenHashForUpdate(TicketTokens.hash("tok"))).thenReturn(Optional.of(ticket));
        when(blocklistRepository.existsBySubjectTypeAndSubjectCode(
                BlocklistSubjectType.TICKET, ticket.getCode())).thenReturn(true);

        TicketValidationDto result = service.validate("tok");

        assertFalse(result.valid());
        assertEquals("ticket.blocked", result.reason());
        assertEquals(TicketStatus.BLOCKED, ticket.getStatus());
        verify(auditService).record(eq("antifraud"), eq("ticket.block"), eq("ticket"),
                eq(ticket.getCode()), any(), any());
    }

    @Test
    void validateRejectsTicketOfBlocklistedRider() {
        Ticket ticket = single();
        when(ticketRepository.findByTokenHashForUpdate(TicketTokens.hash("tok"))).thenReturn(Optional.of(ticket));
        when(blocklistRepository.existsBySubjectTypeAndSubjectCode(
                BlocklistSubjectType.RIDER, "device-1")).thenReturn(true);

        assertEquals("ticket.blocked", service.validate("tok").reason());
    }

    @Test
    void validateOfUnknownTokenLooksExactlyLikeAnInvalidTicket() {
        when(ticketRepository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.empty());

        TicketValidationDto result = service.validate("whatever");

        assertFalse(result.valid());
        assertEquals("ticket.token_unknown", result.reason());
        assertNull(result.ticketCode(), "существование токена не раскрывается");
    }

    @Test
    void validateExpiresTicketWhoseWindowHasPassed() {
        Ticket ticket = new Ticket(UUID.randomUUID(), "TKT-2026-EXPIRED", "DEMO-SINGLE",
                TicketKind.SINGLE, "all", null, TicketTokens.hash("tok"),
                OffsetDateTime.ofInstant(NOW.minusSeconds(7200), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(NOW.minusSeconds(3600), ZoneOffset.UTC),
                new BigDecimal("3.00"), "TJS", true);
        when(ticketRepository.findByTokenHashForUpdate(TicketTokens.hash("tok"))).thenReturn(Optional.of(ticket));

        TicketValidationDto result = service.validate("tok");

        assertFalse(result.valid());
        assertEquals("ticket.expired", result.reason());
        assertEquals(TicketStatus.EXPIRED, ticket.getStatus(), "истёкший билет помечается сразу");
    }

    @Test
    void validateRejectsPassRevalidatedWithinAntiPassbackInterval() {
        Ticket ticket = pass();
        when(ticketRepository.findByTokenHashForUpdate(TicketTokens.hash("tok"))).thenReturn(Optional.of(ticket));

        assertTrue(service.validate("tok").valid());
        TicketValidationDto second = service.validate("tok");

        assertFalse(second.valid(), "передача QR через турникет следующему — отсекается");
        assertEquals("ticket.validation_too_soon", second.reason());
        assertEquals(TicketStatus.ACTIVE, ticket.getStatus(), "проездной остаётся действующим");
    }

    // ------------------------------- Возврат --------------------------------

    @Test
    void refundMovesBothPaymentAndTicketToRefunded() {
        Ticket ticket = single();
        Payment payment = capturedPurchase(ticket);
        when(ticketRepository.findByCodeForUpdate(ticket.getCode())).thenReturn(Optional.of(ticket));
        when(paymentRepository.findFirstByTicketCodeAndKindAndStatusOrderByCreatedAtDesc(
                ticket.getCode(), PaymentKind.PURCHASE, PaymentStatus.CAPTURED))
                .thenReturn(Optional.of(payment));

        var refund = service.refund(ticket.getCode(), "передумал");

        assertEquals("completed", refund.status());
        assertEquals("передумал", refund.reason());
        assertTrue(refund.demo());
        assertEquals(TicketStatus.REFUNDED, ticket.getStatus());
        assertEquals(PaymentStatus.REFUNDED, payment.getStatus());
        verify(auditService).record(eq("public"), eq("ticket.refund"), eq("ticket"),
                eq(ticket.getCode()), any(), any());
        verify(auditService).record(eq("public"), eq("payment.refund"), eq("payment"),
                eq(payment.getCode()), any(), any());
    }

    @Test
    void publicRefundOfUsedTicketIsRejectedButOperatorRefundIsAllowed() {
        Ticket ticket = single();
        ticket.markValidated(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
        Payment payment = capturedPurchase(ticket);
        when(ticketRepository.findByCodeForUpdate(ticket.getCode())).thenReturn(Optional.of(ticket));
        when(paymentRepository.findFirstByTicketCodeAndKindAndStatusOrderByCreatedAtDesc(
                ticket.getCode(), PaymentKind.PURCHASE, PaymentStatus.CAPTURED))
                .thenReturn(Optional.of(payment));

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> service.refund(ticket.getCode(), "хочу назад"));
        assertEquals("ticket.not_refundable", error.getCode());

        var operatorRefund = service.refundTicket(ticket.getCode(), "сбой турникета", "op-1", true);
        assertEquals("completed", operatorRefund.status());
        assertEquals("op-1", operatorRefund.createdBy());
        assertEquals(TicketStatus.REFUNDED, ticket.getStatus());
    }

    @Test
    void refundWithoutCapturedPurchaseIsRejected() {
        Ticket ticket = single();
        when(ticketRepository.findByCodeForUpdate(ticket.getCode())).thenReturn(Optional.of(ticket));
        when(paymentRepository.findFirstByTicketCodeAndKindAndStatusOrderByCreatedAtDesc(
                anyString(), any(), any())).thenReturn(Optional.empty());

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> service.refund(ticket.getCode(), "причина"));

        assertEquals("payment.not_refundable", error.getCode());
    }

    @Test
    void refundOfAlreadyRefundedTicketIsRejectedBecauseRefundedIsTerminal() {
        Ticket ticket = single();
        ticket.refund();
        when(ticketRepository.findByCodeForUpdate(ticket.getCode())).thenReturn(Optional.of(ticket));

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> service.refund(ticket.getCode(), "ещё раз"));

        assertEquals("ticket.not_refundable", error.getCode());
    }

    @Test
    void refundOfUnknownTicketReturnsDomainNotFound() {
        when(ticketRepository.findByCode("NOPE")).thenReturn(Optional.empty());

        NotFoundException error = assertThrows(NotFoundException.class,
                () -> service.refund("NOPE", "причина"));

        assertEquals("ticket.not_found", error.getCode());
    }

    // ----------------------------- Пополнение -------------------------------

    @Test
    void topUpExtendsPassFromEndOfCurrentWindowAndAddsToBalance() {
        Ticket ticket = pass();
        OffsetDateTime originalValidUntil = ticket.getValidUntil();
        when(ticketRepository.findByCode(ticket.getCode())).thenReturn(Optional.of(ticket));
        when(fareProductRepository.findByCode("DEMO-MONTHLY")).thenReturn(Optional.of(fare(
                "DEMO-MONTHLY", new BigDecimal("50.00"), 43200, true)));

        var response = service.topUp(ticket.getCode(),
                new TicketTopUpRequest(new BigDecimal("50.00"), null));

        assertEquals("captured", response.payment().status());
        assertEquals("topup", response.payment().kind());
        assertEquals(0, new BigDecimal("50.00").compareTo(response.ticket().balanceAmount()));
        assertEquals(originalValidUntil.plusMinutes(43200).toInstant(),
                response.ticket().validUntil(),
                "оплаченный остаток срока не сгорает");
    }

    @Test
    void topUpOfSingleTicketIsRejected() {
        Ticket ticket = single();
        when(ticketRepository.findByCode(ticket.getCode())).thenReturn(Optional.of(ticket));

        BadRequestException error = assertThrows(BadRequestException.class, () -> service.topUp(
                ticket.getCode(), new TicketTopUpRequest(new BigDecimal("10.00"), null)));

        assertEquals("ticket.topup_not_supported", error.getCode());
    }

    @Test
    void topUpOfRefundedPassIsRejected() {
        Ticket ticket = pass();
        ticket.refund();
        when(ticketRepository.findByCode(ticket.getCode())).thenReturn(Optional.of(ticket));

        BadRequestException error = assertThrows(BadRequestException.class, () -> service.topUp(
                ticket.getCode(), new TicketTopUpRequest(new BigDecimal("10.00"), null)));

        assertEquals("ticket.not_topupable", error.getCode());
    }

    @Test
    void declinedTopUpLeavesTicketUntouched() {
        Ticket ticket = pass();
        OffsetDateTime originalValidUntil = ticket.getValidUntil();
        when(ticketRepository.findByCode(ticket.getCode())).thenReturn(Optional.of(ticket));
        when(fareProductRepository.findByCode("DEMO-MONTHLY")).thenReturn(Optional.of(fare(
                "DEMO-MONTHLY", new BigDecimal("50.00"), 43200, true)));

        var response = service.topUp(ticket.getCode(),
                new TicketTopUpRequest(new BigDecimal("50.00"), "decline"));

        assertEquals("failed", response.payment().status());
        assertEquals(originalValidUntil, ticket.getValidUntil());
        assertEquals(0, BigDecimal.ZERO.compareTo(ticket.getBalanceAmount()));
    }

    // ------------------------------ Фикстуры --------------------------------

    private Ticket savedTicket() {
        var captor = org.mockito.ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).save(captor.capture());
        return captor.getValue();
    }

    private static FareProduct fare(String code, BigDecimal amount, Integer validityMinutes,
                                    boolean active) {
        return new FareProduct(UUID.randomUUID(), code, I18N, I18N, amount, "TJS", "all",
                validityMinutes, active);
    }

    /** Разовый билет в окне действия, статус issued (как сразу после покупки). */
    private static Ticket single() {
        return new Ticket(UUID.randomUUID(), "TKT-2026-SINGLE01", "DEMO-SINGLE",
                TicketKind.SINGLE, "all", "device-1", TicketTokens.hash("tok"),
                OffsetDateTime.ofInstant(NOW.minusSeconds(60), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(NOW.plusSeconds(3600), ZoneOffset.UTC),
                new BigDecimal("3.00"), "TJS", true);
    }

    /** Проездной в окне действия, статус issued. */
    private static Ticket pass() {
        return new Ticket(UUID.randomUUID(), "TKT-2026-PASS0001", "DEMO-MONTHLY",
                TicketKind.PASS, "all", "device-2", TicketTokens.hash("tok"),
                OffsetDateTime.ofInstant(NOW.minusSeconds(60), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(NOW.plusSeconds(86400), ZoneOffset.UTC),
                new BigDecimal("50.00"), "TJS", true);
    }

    private static Payment capturedPurchase(Ticket ticket) {
        Payment payment = new Payment(UUID.randomUUID(), "PAY-2026-AAAA0001", PaymentKind.PURCHASE,
                ticket.getPriceAmount(), ticket.getPriceCurrency(), "demo", true);
        payment.authorize("DEMO-CHG-PAY-2026-AAAA0001");
        payment.capture(ticket.getCode());
        return payment;
    }
}
