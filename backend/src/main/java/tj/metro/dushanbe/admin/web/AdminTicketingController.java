package tj.metro.dushanbe.admin.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tj.metro.dushanbe.admin.service.AdminTicketingService;
import tj.metro.dushanbe.admin.web.dto.BlocklistCreateRequest;
import tj.metro.dushanbe.admin.web.dto.TicketRefundCommand;
import tj.metro.dushanbe.ticketing.web.dto.BlocklistEntryDto;
import tj.metro.dushanbe.ticketing.web.dto.PaymentDto;
import tj.metro.dushanbe.ticketing.web.dto.RefundDto;
import tj.metro.dushanbe.ticketing.web.dto.TicketDto;

/**
 * Операторский разбор билетов и платежей (TKT-03/05/06).
 *
 * <p>Токены QR здесь недоступны — их нет в системе, только хеши. Оператор,
 * которому «нужно посмотреть токен пассажира», не сможет: это и есть смысл
 * хранения хеша.
 */
@RestController
@RequestMapping("/v1/admin")
@Tag(name = "Admin: Ticketing", description = "Билеты, платежи, возвраты и чёрный список")
public class AdminTicketingController {

    private final AdminTicketingService service;

    public AdminTicketingController(AdminTicketingService service) {
        this.service = service;
    }

    @GetMapping("/tickets")
    @Operation(summary = "Список билетов",
            description = "Фильтр ?status=issued|active|used|expired|refunded|blocked. "
                    + "400 ticket.status_invalid при неизвестном статусе.")
    public List<TicketDto> listTickets(@RequestParam(name = "status", required = false) String status) {
        return service.listTickets(status);
    }

    @GetMapping("/tickets/{code}")
    @Operation(summary = "Карточка билета", description = "404 ticket.not_found")
    public TicketDto getTicket(@PathVariable("code") String code) {
        return service.getTicket(code);
    }

    @GetMapping("/tickets/{code}/payments")
    @Operation(summary = "Платежи по билету", description = "Покупка и все пополнения, свежие сверху.")
    public List<PaymentDto> listTicketPayments(@PathVariable("code") String code) {
        return service.listTicketPayments(code);
    }

    @GetMapping("/payments")
    @Operation(summary = "Лента платежей",
            description = "Фильтр ?status=pending|authorized|captured|failed|refunded. "
                    + "400 payment.status_invalid при неизвестном статусе.")
    public List<PaymentDto> listPayments(@RequestParam(name = "status", required = false) String status) {
        return service.listPayments(status);
    }

    @PostMapping("/tickets/{code}/refund")
    @Operation(summary = "Ручной возврат за билет",
            description = """
                    В отличие от публичного возврата допускает уже использованный билет —
                    для случаев, которые правило не покрывает (сбой турникета, решение по
                    жалобе). Операция именная и попадает в аудит.
                    Ошибки: 404 ticket.not_found; 400 ticket.not_refundable,
                    payment.not_refundable; 403 ticketing.disabled.""")
    public RefundDto refund(
            @PathVariable("code") String code,
            @Valid @RequestBody TicketRefundCommand request,
            @RequestHeader(name = "X-Admin-Actor", defaultValue = "dev-admin") String actor) {
        return service.refund(code, request, actor);
    }

    @GetMapping("/blocklist")
    @Operation(summary = "Чёрный список",
            description = "Фильтр ?subjectType=ticket|token|rider. Для token в subjectCode — SHA-256.")
    public List<BlocklistEntryDto> listBlocklist(
            @RequestParam(name = "subjectType", required = false) String subjectType) {
        return service.listBlocklist(subjectType);
    }

    @PostMapping("/blocklist")
    @Operation(summary = "Заблокировать билет / токен / покупателя",
            description = """
                    Применяется немедленно: билеты субъекта переводятся в blocked.
                    Для subjectType=token передаётся сам токен — сервис сохранит только его SHA-256.
                    Ошибки: 404 ticket.not_found; 400 blocklist.subject_type_invalid,
                    blocklist.subject_exists.""")
    public ResponseEntity<BlocklistEntryDto> addToBlocklist(
            @Valid @RequestBody BlocklistCreateRequest request,
            @RequestHeader(name = "X-Admin-Actor", defaultValue = "dev-admin") String actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addToBlocklist(request, actor));
    }

    @DeleteMapping("/blocklist/{code}")
    @Operation(summary = "Снять блокировку",
            description = """
                    Открывает субъекту покупку новых билетов. Уже заблокированные билеты
                    не восстанавливаются: их токены скомпрометированы.
                    404 blocklist.not_found.""")
    public ResponseEntity<Void> removeFromBlocklist(
            @PathVariable("code") String code,
            @RequestHeader(name = "X-Admin-Actor", defaultValue = "dev-admin") String actor) {
        service.removeFromBlocklist(code, actor);
        return ResponseEntity.noContent().build();
    }
}
