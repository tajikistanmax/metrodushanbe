package tj.metro.dushanbe.ticketing.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tj.metro.dushanbe.admin.security.AdminAuthProperties;
import tj.metro.dushanbe.config.RateLimitProperties;
import tj.metro.dushanbe.identity.repository.AdminUserRepository;
import tj.metro.dushanbe.ticketing.service.TicketingService;
import tj.metro.dushanbe.ticketing.web.dto.PaymentDto;
import tj.metro.dushanbe.ticketing.web.dto.RefundDto;
import tj.metro.dushanbe.ticketing.web.dto.TicketDto;
import tj.metro.dushanbe.ticketing.web.dto.TicketPurchaseRequest;
import tj.metro.dushanbe.ticketing.web.dto.TicketPurchaseResponse;
import tj.metro.dushanbe.ticketing.web.dto.TicketTopUpRequest;
import tj.metro.dushanbe.ticketing.web.dto.TicketTopUpResponse;
import tj.metro.dushanbe.ticketing.web.dto.TicketValidationDto;

@WebMvcTest(TicketingController.class)
@AutoConfigureMockMvc(addFilters = false)
class TicketingControllerTest {

    private static final Instant NOW = Instant.parse("2026-07-17T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TicketingService service;

    @MockitoBean
    private AdminAuthProperties adminAuthProperties;

    @MockitoBean
    private RateLimitProperties rateLimitProperties;

    /**
     * AdminKeyAuthFilter — бин типа Filter, поэтому @WebMvcTest создаёт его даже
     * при addFilters = false. Фильтр резолвит актора через репозиторий, которого
     * в web-срезе нет, — без этой заглушки контекст не поднимется.
     */
    @MockitoBean
    private AdminUserRepository adminUserRepository;

    @Test
    void purchaseReturns201WithTokenAndDemoMark() throws Exception {
        when(service.purchase(any(TicketPurchaseRequest.class))).thenReturn(
                new TicketPurchaseResponse(ticket("issued"), "secret-qr-token",
                        payment("captured", null), true, TicketingService.DEMO_NOTICE));

        mockMvc.perform(post("/v1/tickets/purchase")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fareProductCode":"DEMO-SINGLE","riderRef":"device-1"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ticket.code").value("TKT-2026-A1B2C3D4"))
                .andExpect(jsonPath("$.ticket.priceAmount").value(3.00))
                .andExpect(jsonPath("$.token").value("secret-qr-token"))
                .andExpect(jsonPath("$.demo").value(true))
                .andExpect(jsonPath("$.notice").isNotEmpty())
                // Токена в самом билете нет — он живёт только в корне ответа.
                .andExpect(jsonPath("$.ticket.token").doesNotExist());
    }

    @Test
    void declinedPurchaseReturns402WithoutTicketOrToken() throws Exception {
        when(service.purchase(any(TicketPurchaseRequest.class))).thenReturn(
                new TicketPurchaseResponse(null, null,
                        payment("failed", "ДЕМО: платёж отклонён"), true,
                        TicketingService.DEMO_NOTICE));

        mockMvc.perform(post("/v1/tickets/purchase")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fareProductCode":"DEMO-SINGLE","demoScenario":"decline"}
                                """))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.ticket").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.payment.status").value("failed"))
                .andExpect(jsonPath("$.payment.failureReason").isNotEmpty());
    }

    @Test
    void purchaseRejectsMissingFareProductCode() throws Exception {
        mockMvc.perform(post("/v1/tickets/purchase")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"riderRef":"device-1"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getTicketNeverExposesToken() throws Exception {
        when(service.get("TKT-2026-A1B2C3D4")).thenReturn(ticket("active"));

        mockMvc.perform(get("/v1/tickets/TKT-2026-A1B2C3D4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("active"))
                .andExpect(jsonPath("$.demo").value(true))
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.tokenHash").doesNotExist());
    }

    @Test
    void topUpReturns200AndDeclinedTopUpReturns402() throws Exception {
        when(service.topUp(any(), any(TicketTopUpRequest.class))).thenReturn(
                new TicketTopUpResponse(ticket("active"), payment("captured", null), true,
                        TicketingService.DEMO_NOTICE));

        mockMvc.perform(post("/v1/tickets/TKT-2026-A1B2C3D4/topup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":50.00}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.status").value("captured"));

        when(service.topUp(any(), any(TicketTopUpRequest.class))).thenReturn(
                new TicketTopUpResponse(ticket("active"), payment("failed", "ДЕМО: отклонено"),
                        true, TicketingService.DEMO_NOTICE));

        mockMvc.perform(post("/v1/tickets/TKT-2026-A1B2C3D4/topup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":50.00,"demoScenario":"decline"}
                                """))
                .andExpect(status().isPaymentRequired());
    }

    @Test
    void topUpRejectsNonPositiveAmount() throws Exception {
        mockMvc.perform(post("/v1/tickets/TKT-2026-A1B2C3D4/topup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":0}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validateReturns200EvenWhenTicketIsRejected() throws Exception {
        when(service.validate("bad-token")).thenReturn(new TicketValidationDto(false,
                "ticket.already_used", "TKT-2026-A1B2C3D4", "used", "single", "all", NOW, true));

        // 200, а не 4xx: турникету нужно решение, а не исключение.
        mockMvc.perform(post("/v1/tickets/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"bad-token"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.reason").value("ticket.already_used"));
    }

    @Test
    void validateRejectsBlankToken() throws Exception {
        mockMvc.perform(post("/v1/tickets/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"  "}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refundReturnsRefundDocument() throws Exception {
        when(service.refund("TKT-2026-A1B2C3D4", "передумал")).thenReturn(new RefundDto(
                "RFN-2026-0001", "PAY-2026-0001", new BigDecimal("3.00"), "TJS", "completed",
                "передумал", null, "public", true, NOW));

        mockMvc.perform(post("/v1/tickets/TKT-2026-A1B2C3D4/refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"передумал"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.demo").value(true));
    }

    @Test
    void refundRejectsMissingReason() throws Exception {
        mockMvc.perform(post("/v1/tickets/TKT-2026-A1B2C3D4/refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    private static TicketDto ticket(String status) {
        return new TicketDto("TKT-2026-A1B2C3D4", "DEMO-SINGLE", "single", "all", status,
                NOW, NOW.plusSeconds(5400), new BigDecimal("3.00"), "TJS", BigDecimal.ZERO,
                null, true, NOW, List.of("used", "expired", "refunded", "blocked"));
    }

    private static PaymentDto payment(String status, String failureReason) {
        return new PaymentDto("PAY-2026-0001", "failed".equals(status) ? null : "TKT-2026-A1B2C3D4",
                "purchase", new BigDecimal("3.00"), "TJS", status, "demo",
                "failed".equals(status) ? null : "DEMO-CHG-PAY-2026-0001", failureReason,
                true, NOW, NOW);
    }
}
