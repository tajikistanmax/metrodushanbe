package tj.metro.dushanbe.admin.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tj.metro.dushanbe.admin.web.dto.BlocklistCreateRequest;
import tj.metro.dushanbe.admin.web.dto.TicketRefundCommand;
import tj.metro.dushanbe.audit.service.AuditService;
import tj.metro.dushanbe.common.error.BadRequestException;
import tj.metro.dushanbe.common.error.NotFoundException;
import tj.metro.dushanbe.ticketing.domain.BlocklistSubjectType;
import tj.metro.dushanbe.ticketing.domain.Payment;
import tj.metro.dushanbe.ticketing.domain.PaymentStatus;
import tj.metro.dushanbe.ticketing.domain.Ticket;
import tj.metro.dushanbe.ticketing.domain.TicketBlocklistEntry;
import tj.metro.dushanbe.ticketing.domain.TicketStatus;
import tj.metro.dushanbe.ticketing.repository.PaymentRepository;
import tj.metro.dushanbe.ticketing.repository.TicketBlocklistRepository;
import tj.metro.dushanbe.ticketing.repository.TicketRepository;
import tj.metro.dushanbe.ticketing.service.TicketTokens;
import tj.metro.dushanbe.ticketing.service.TicketingService;
import tj.metro.dushanbe.ticketing.web.dto.BlocklistEntryDto;
import tj.metro.dushanbe.ticketing.web.dto.PaymentDto;
import tj.metro.dushanbe.ticketing.web.dto.RefundDto;
import tj.metro.dushanbe.ticketing.web.dto.TicketDto;

/**
 * Операторский контур билетов: просмотр, ручной возврат, чёрный список
 * (TKT-03/05/06).
 *
 * <p>Собственной бизнес-логики выпуска и возврата здесь нет — она в
 * {@link TicketingService}, и вызывается оттуда же, что и публичный контур.
 * Дублировать её ради «операторской версии» значило бы завести второй, тихо
 * расходящийся набор правил: ровно так появляются билеты, которые пассажир
 * вернуть не может, а оператор — может, но с другими последствиями.
 *
 * <p>Аудит обязателен на каждом изменении: возврат — финансовая операция,
 * блокировка — ограничение прав пассажира. Обе должны иметь имя и время.
 */
@Service
public class AdminTicketingService {

    private final TicketRepository ticketRepository;
    private final PaymentRepository paymentRepository;
    private final TicketBlocklistRepository blocklistRepository;
    private final TicketingService ticketingService;
    private final AuditService auditService;
    private final Clock clock;

    public AdminTicketingService(TicketRepository ticketRepository,
                                 PaymentRepository paymentRepository,
                                 TicketBlocklistRepository blocklistRepository,
                                 TicketingService ticketingService,
                                 AuditService auditService,
                                 Clock clock) {
        this.ticketRepository = ticketRepository;
        this.paymentRepository = paymentRepository;
        this.blocklistRepository = blocklistRepository;
        this.ticketingService = ticketingService;
        this.auditService = auditService;
        this.clock = clock;
    }

    // ------------------------------- Билеты ---------------------------------

    @Transactional(readOnly = true)
    public List<TicketDto> listTickets(String statusFilter) {
        List<Ticket> tickets = statusFilter == null || statusFilter.isBlank()
                ? ticketRepository.findAllByOrderByCreatedAtDesc()
                : ticketRepository.findByStatusOrderByCreatedAtDesc(ticketStatus(statusFilter));
        return tickets.stream().map(TicketingService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public TicketDto getTicket(String code) {
        return TicketingService.toDto(findTicket(code));
    }

    /** Платежи по билету: покупка и все пополнения, свежие сверху. */
    @Transactional(readOnly = true)
    public List<PaymentDto> listTicketPayments(String code) {
        findTicket(code);
        return paymentRepository.findByTicketCodeOrderByCreatedAtDesc(code).stream()
                .map(TicketingService::toDto)
                .toList();
    }

    // ------------------------------- Платежи --------------------------------

    @Transactional(readOnly = true)
    public List<PaymentDto> listPayments(String statusFilter) {
        List<Payment> payments = statusFilter == null || statusFilter.isBlank()
                ? paymentRepository.findAllByOrderByCreatedAtDesc()
                : paymentRepository.findByStatusOrderByCreatedAtDesc(paymentStatus(statusFilter));
        return payments.stream().map(TicketingService::toDto).toList();
    }

    /**
     * Ручной возврат (TKT-03).
     *
     * <p>{@code allowUsed = true}: ручной возврат существует ровно для случаев,
     * которые автоматическое правило не покрывает, — сбой турникета, решение по
     * жалобе. Ответственность за такой возврат несёт оператор, и его имя остаётся
     * в аудите (запись делает {@link TicketingService}).
     */
    @Transactional
    public RefundDto refund(String code, TicketRefundCommand request, String actor) {
        return ticketingService.refundTicket(code, request.reason(), actor, true);
    }

    // ---------------------------- Чёрный список -----------------------------

    @Transactional(readOnly = true)
    public List<BlocklistEntryDto> listBlocklist(String subjectTypeFilter) {
        List<TicketBlocklistEntry> entries = subjectTypeFilter == null || subjectTypeFilter.isBlank()
                ? blocklistRepository.findAllByOrderByCreatedAtDesc()
                : blocklistRepository.findBySubjectTypeOrderByCreatedAtDesc(
                        subjectType(subjectTypeFilter));
        return entries.stream().map(AdminTicketingService::toDto).toList();
    }

    /**
     * Добавляет субъект в чёрный список (TKT-06).
     *
     * <p>Блокировка применяется немедленно, а не только «на будущее»: при
     * subject_type = ticket сам билет переводится в blocked, при rider — все его
     * билеты. Иначе между записью в список и первой валидацией остаётся окно, в
     * которое билет, признанный мошенническим, ещё возит.
     */
    @Transactional
    public BlocklistEntryDto addToBlocklist(BlocklistCreateRequest request, String actor) {
        BlocklistSubjectType type = subjectType(request.subjectType());
        String value = request.subjectValue().trim();
        // Токен превращается в хеш прямо здесь: дальше по коду открытого токена
        // уже нет — ни в БД, ни в снимке аудита.
        String subjectCode = type.hashed() ? TicketTokens.hash(value) : value;

        if (type == BlocklistSubjectType.TICKET && ticketRepository.findByCode(value).isEmpty()) {
            throw new NotFoundException("ticket.not_found", "Билет не найден");
        }
        AdminSupport.requireUnique(
                blocklistRepository.existsBySubjectTypeAndSubjectCode(type, subjectCode),
                "blocklist.subject_exists", "subjectValue",
                type.hashed() ? "<токен>" : value);

        TicketBlocklistEntry entry = new TicketBlocklistEntry(UUID.randomUUID(), nextCode(),
                type, subjectCode, request.reason().trim(), actor);
        TicketBlocklistEntry saved = blocklistRepository.save(entry);
        auditService.record(actor, "blocklist.create", "ticket_blocklist", saved.getCode(),
                null, snapshot(saved));

        blockAffectedTickets(type, subjectCode, actor);
        return toDto(saved);
    }

    /**
     * Снимает блокировку (TKT-06).
     *
     * <p>Уже заблокированные билеты в строю не восстанавливаются: blocked
     * терминален (см. TicketStatus) — токен скомпрометирован, и возвращать в
     * оборот именно его нельзя. Снятие открывает субъекту покупку новых билетов,
     * а не воскрешает старые.
     */
    @Transactional
    public void removeFromBlocklist(String code, String actor) {
        TicketBlocklistEntry entry = blocklistRepository.findByCode(code)
                .orElseThrow(() -> new NotFoundException("blocklist.not_found",
                        "Запись чёрного списка не найдена"));
        Map<String, Object> before = snapshot(entry);
        blocklistRepository.delete(entry);
        auditService.record(actor, "blocklist.delete", "ticket_blocklist", code, before, null);
    }

    private void blockAffectedTickets(BlocklistSubjectType type, String subjectCode, String actor) {
        List<Ticket> affected = switch (type) {
            case TICKET -> ticketRepository.findByCode(subjectCode).stream().toList();
            case RIDER -> ticketRepository.findByRiderRef(subjectCode);
            // Блокировка по токену не ищет билет намеренно: турникет мог прислать
            // токен, которому билет ещё не соответствует (подделка, перебор).
            // Валидация всё равно сверится со списком по хешу и не пропустит.
            case TOKEN -> List.of();
        };
        for (Ticket ticket : affected) {
            if (ticket.getStatus().terminal()) {
                continue;
            }
            Map<String, Object> before = TicketingService.snapshot(ticket);
            ticket.block();
            Ticket saved = ticketRepository.save(ticket);
            auditService.record(actor, "ticket.block", "ticket", saved.getCode(),
                    before, TicketingService.snapshot(saved));
        }
    }

    // ------------------------------ Служебное -------------------------------

    private Ticket findTicket(String code) {
        return ticketRepository.findByCode(code)
                .orElseThrow(() -> new NotFoundException("ticket.not_found", "Билет не найден"));
    }

    private static TicketStatus ticketStatus(String code) {
        return TicketStatus.fromCode(code).orElseThrow(() ->
                new BadRequestException("ticket.status_invalid", "Недопустимый статус: " + code,
                        Map.of("field", "status", "allowed", TicketStatus.codes())));
    }

    private static PaymentStatus paymentStatus(String code) {
        return PaymentStatus.fromCode(code).orElseThrow(() ->
                new BadRequestException("payment.status_invalid", "Недопустимый статус: " + code,
                        Map.of("field", "status", "allowed", PaymentStatus.codes())));
    }

    private static BlocklistSubjectType subjectType(String code) {
        return BlocklistSubjectType.fromCode(code).orElseThrow(() ->
                new BadRequestException("blocklist.subject_type_invalid",
                        "Недопустимый тип субъекта: " + code,
                        Map.of("field", "subjectType", "allowed", BlocklistSubjectType.codes())));
    }

    /** Код вида BLK-2026-A1B2C3D4 — тем же приёмом, что коды билетов. */
    private String nextCode() {
        int year = OffsetDateTime.now(clock).getYear();
        for (int attempt = 0; attempt < 20; attempt++) {
            String suffix = HexFormat.of().toHexDigits(ThreadLocalRandom.current().nextInt())
                    .toUpperCase(Locale.ROOT);
            String code = "BLK-" + year + "-" + suffix;
            if (!blocklistRepository.existsByCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Не удалось сгенерировать уникальный код записи блокировки");
    }

    private static Map<String, Object> snapshot(TicketBlocklistEntry entry) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("code", entry.getCode());
        snapshot.put("subjectType", entry.getSubjectType().code());
        snapshot.put("subjectCode", entry.getSubjectCode());
        snapshot.put("reason", entry.getReason());
        snapshot.put("createdBy", entry.getCreatedBy());
        return snapshot;
    }

    public static BlocklistEntryDto toDto(TicketBlocklistEntry entry) {
        return new BlocklistEntryDto(
                entry.getCode(),
                entry.getSubjectType().code(),
                entry.getSubjectCode(),
                entry.getReason(),
                entry.getCreatedBy(),
                entry.getCreatedAt() == null ? null : entry.getCreatedAt().toInstant());
    }
}
