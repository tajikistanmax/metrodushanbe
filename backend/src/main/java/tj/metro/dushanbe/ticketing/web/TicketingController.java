package tj.metro.dushanbe.ticketing.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tj.metro.dushanbe.ticketing.service.TicketingService;
import tj.metro.dushanbe.ticketing.web.dto.RefundDto;
import tj.metro.dushanbe.ticketing.web.dto.TicketDto;
import tj.metro.dushanbe.ticketing.web.dto.TicketPurchaseRequest;
import tj.metro.dushanbe.ticketing.web.dto.TicketPurchaseResponse;
import tj.metro.dushanbe.ticketing.web.dto.TicketRefundRequest;
import tj.metro.dushanbe.ticketing.web.dto.TicketTopUpRequest;
import tj.metro.dushanbe.ticketing.web.dto.TicketTopUpResponse;
import tj.metro.dushanbe.ticketing.web.dto.TicketValidateRequest;
import tj.metro.dushanbe.ticketing.web.dto.TicketValidationDto;

/**
 * Покупка, пополнение, предъявление и возврат билетов (U-CIT-08, TKT-03/04).
 *
 * <p><b>ДЕМО-КОНТУР.</b> Реального эквайринга нет: платежи имитируются, каждый
 * ответ несёт {@code demo: true} и текстовую пометку. Карточные данные ни один
 * эндпоинт не принимает — см. javadoc {@code PaymentGateway}.
 */
@RestController
@RequestMapping("/v1/tickets")
@Tag(name = "Tickets", description = "Билеты: покупка, пополнение, валидация, возврат (демо-контур)")
public class TicketingController {

    private final TicketingService service;

    public TicketingController(TicketingService service) {
        this.service = service;
    }

    @GetMapping("/{code}")
    @Operation(summary = "Публичный статус билета",
            description = "Токен QR не возвращается: он существует только в ответе на покупку. "
                    + "404 ticket.not_found — билета с таким кодом нет.")
    public TicketDto get(@PathVariable("code") String code) {
        return service.get(code);
    }

    @PostMapping("/purchase")
    @Operation(summary = "Купить билет",
            description = """
                    Цена берётся из активного тарифа на сервере и фиксируется в билете.
                    Токен QR возвращается ОДИН РАЗ — восстановить его невозможно.
                    201 — билет выпущен; 402 — платёж отклонён провайдером (билета нет,
                    причина в payment.failureReason).
                    Ошибки: 404 fare.not_found; 400 ticketing.fare_inactive,
                    ticketing.fare_not_payable, ticketing.rider_blocked,
                    ticketing.purchase_limit; 403 ticketing.disabled.""")
    public ResponseEntity<TicketPurchaseResponse> purchase(
            @Valid @RequestBody TicketPurchaseRequest request) {
        TicketPurchaseResponse response = service.purchase(request);
        // 402 вместо исключения: запись об отклонённой попытке должна дожить до
        // коммита — она материал антифрода (TKT-06).
        return response.ticket() == null
                ? ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(response)
                : ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{code}/topup")
    @Operation(summary = "Пополнить проездной",
            description = """
                    Продлевает срок действия на срок исходного тарифа и учитывает внесённую сумму.
                    200 — пополнено; 402 — платёж отклонён.
                    Ошибки: 404 ticket.not_found; 400 ticket.topup_not_supported (разовый билет),
                    ticket.not_topupable (недействующий), ticketing.ticket_blocked,
                    ticketing.fare_inactive; 403 ticketing.disabled.""")
    public ResponseEntity<TicketTopUpResponse> topUp(
            @PathVariable("code") String code,
            @Valid @RequestBody TicketTopUpRequest request) {
        TicketTopUpResponse response = service.topUp(code, request);
        return "failed".equals(response.payment().status())
                ? ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(response)
                : ResponseEntity.ok(response);
    }

    @PostMapping("/validate")
    @Operation(summary = "Предъявить билет (турникет/контролёр)",
            description = """
                    Всегда 200: турникету нужно решение, а не исключение. Недействительный
                    билет — valid=false с машиночитаемой причиной: ticket.token_unknown,
                    ticket.blocked, ticket.already_used, ticket.expired, ticket.refunded,
                    ticket.not_started, ticket.validation_too_soon (anti-passback).
                    Неизвестный токен отвечает так же, как недействительный билет, — чтобы
                    эндпоинт не работал оракулом для перебора токенов.""")
    public TicketValidationDto validate(@Valid @RequestBody TicketValidateRequest request) {
        return service.validate(request.token());
    }

    @PostMapping("/{code}/refund")
    @Operation(summary = "Вернуть деньги за билет",
            description = """
                    Возврат по заявлению пассажира. Использованный билет так не возвращается —
                    это делает оператор через админский контур.
                    Ошибки: 404 ticket.not_found; 400 ticket.not_refundable,
                    payment.not_refundable; 403 ticketing.disabled.""")
    public RefundDto refund(
            @PathVariable("code") String code,
            @Valid @RequestBody TicketRefundRequest request) {
        return service.refund(code, request.reason());
    }
}
