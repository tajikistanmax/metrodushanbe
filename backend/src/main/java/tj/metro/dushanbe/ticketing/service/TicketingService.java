package tj.metro.dushanbe.ticketing.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import tj.metro.dushanbe.ticketing.web.dto.PaymentDto;
import tj.metro.dushanbe.ticketing.web.dto.RefundDto;
import tj.metro.dushanbe.ticketing.web.dto.TicketDto;
import tj.metro.dushanbe.ticketing.web.dto.TicketPurchaseRequest;
import tj.metro.dushanbe.ticketing.web.dto.TicketPurchaseResponse;
import tj.metro.dushanbe.ticketing.web.dto.TicketTopUpRequest;
import tj.metro.dushanbe.ticketing.web.dto.TicketTopUpResponse;
import tj.metro.dushanbe.ticketing.web.dto.TicketValidationDto;

/**
 * Сквозной контур билетов: покупка, пополнение, валидация, возврат
 * (TKT-03/04/05/06, U-CIT-08).
 *
 * <p><b>ДЕМО-КОНТУР.</b> Реального эквайринга нет — платежи проходят через
 * {@link PaymentGateway}, единственная реализация которого имитирует исход. Всё
 * выпущенное помечается {@code demo} и в БД, и в ответе. Карточные данные не
 * принимаются и не хранятся; подробности и запреты — в javadoc
 * {@link PaymentGateway} и в шапке V023.
 *
 * <p><b>Цена фиксируется в момент покупки.</b> Из {@code fare_product} берётся
 * сумма на «сейчас» и копируется в билет. Тариф после этого живёт своей жизнью:
 * подорожает, сменит категорию, будет снят с продажи — на выпущенный билет это
 * не влияет. Обратное (читать цену из справочника при показе билета) означало бы,
 * что вчерашняя покупка задним числом дорожает.
 *
 * <p>«Сейчас» — только из инжектируемого {@link Clock}: окна действия билетов и
 * антифрод-правила иначе непроверяемы.
 */
@Service
public class TicketingService {

    /** Актор аудита для действий, инициированных пассажиром из приложения. */
    public static final String PUBLIC_ACTOR = "public";

    /** Пометка demo-контура, доезжающая до пассажира. См. javadoc PaymentGateway. */
    public static final String DEMO_NOTICE =
            "ДЕМО: реального платежа не было, билет недействителен для проезда.";

    /** Флаг покупки/пополнения (TKT-05): выключается при сбое эквайринга. */
    public static final String FLAG_PURCHASE = "ticketing.purchase";

    /** Флаг возвратов (TKT-03): выключается отдельно от покупок. */
    public static final String FLAG_REFUND = "ticketing.refund";

    /**
     * Срок действия билета, если тариф его не задал ({@code validity_minutes IS NULL}).
     *
     * <p>Билета без срока не бывает: chk_ticket_validity_window требует окно, а
     * бессрочный билет на предъявителя — это подарок мошеннику. 90 минут — срок
     * demo-тарифа DEMO-SINGLE; при утверждении тарифов срок обязан прийти из
     * справочника, а не отсюда.
     */
    private static final int DEFAULT_VALIDITY_MINUTES = 90;

    // ---------------------------------------------------------------------
    // Антифрод (TKT-06)
    //
    // ЭТО ЗАГЛУШКА, А НЕ БОЕВОЙ АНТИФРОД. Два простых детерминированных правила
    // ниже — каркас, чтобы контур покупки/валидации имел куда встроить проверки,
    // и чтобы существовал тест, ломающийся при их пропаже. Настоящий антифрод
    // (скоринг, поведенческие профили, графы устройств, чёрные списки эквайера,
    // ручная модерация) — это отдельная система, зависящая от того самого
    // договора с банком, которого нет; см. «Внешние блокеры».
    //
    // Правила намеренно без случайности и без внешних вызовов: правило, которое
    // нельзя воспроизвести в тесте, не правило, а источник мигающих сборок.
    // ---------------------------------------------------------------------

    /** Окно счётчика покупок одного покупателя. */
    private static final Duration PURCHASE_WINDOW = Duration.ofHours(1);

    /**
     * Сколько билетов один rider_ref может купить за {@link #PURCHASE_WINDOW}.
     * Порог заведомо щедрый: цель — отсечь скрипт, а не семью с гостями.
     */
    private static final int PURCHASE_LIMIT_PER_WINDOW = 10;

    /**
     * Anti-passback: минимальный интервал между двумя валидациями одного
     * проездного. Ловит передачу QR через турникет следующему человеку — самый
     * дешёвый и самый частый способ обмана. Разовых билетов не касается: они
     * гасятся первой же валидацией.
     */
    private static final Duration ANTI_PASSBACK_INTERVAL = Duration.ofSeconds(30);

    private final TicketRepository ticketRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final TicketBlocklistRepository blocklistRepository;
    private final FareProductRepository fareProductRepository;
    private final PaymentGateway paymentGateway;
    private final FeatureFlagService featureFlagService;
    private final AuditService auditService;
    private final Clock clock;

    public TicketingService(TicketRepository ticketRepository,
                            PaymentRepository paymentRepository,
                            RefundRepository refundRepository,
                            TicketBlocklistRepository blocklistRepository,
                            FareProductRepository fareProductRepository,
                            PaymentGateway paymentGateway,
                            FeatureFlagService featureFlagService,
                            AuditService auditService,
                            Clock clock) {
        this.ticketRepository = ticketRepository;
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.blocklistRepository = blocklistRepository;
        this.fareProductRepository = fareProductRepository;
        this.paymentGateway = paymentGateway;
        this.featureFlagService = featureFlagService;
        this.auditService = auditService;
        this.clock = clock;
    }

    /** Публичный статус билета. Токена не возвращает — его больше не существует. */
    @Transactional(readOnly = true)
    public TicketDto get(String code) {
        return toDto(find(code));
    }

    /**
     * Покупка билета (U-CIT-08).
     *
     * <p>Порядок шагов не произволен: сначала антифрод, затем платёж, и только
     * после успешного списания — выпуск билета. Билет, созданный до ответа
     * провайдера, при отказе пришлось бы «отменять», и любой сбой между шагами
     * оставлял бы в системе неоплаченный, но действующий билет.
     *
     * <p>Отказ провайдера НЕ бросает исключение и возвращает
     * {@link TicketPurchaseResponse} с {@code ticket = null}. Исключение
     * откатило бы транзакцию вместе с записью об отклонённой попытке — а она и
     * есть материал антифрода (TKT-06). HTTP-статус (402) выбирает контроллер.
     */
    @Transactional
    public TicketPurchaseResponse purchase(TicketPurchaseRequest request) {
        requireFlag(FLAG_PURCHASE, "Покупка билетов временно недоступна");

        OffsetDateTime now = OffsetDateTime.now(clock);
        String riderRef = blankToNull(request.riderRef());
        requireRiderNotBlocked(riderRef);
        requirePurchaseRateWithinLimit(riderRef, now);

        FareProduct product = activeFare(request.fareProductCode());
        BigDecimal amount = product.getAmount();
        requirePayableAmount(amount, product.getCode());

        Payment payment = new Payment(UUID.randomUUID(), nextCode("PAY", paymentRepository::existsByCode),
                PaymentKind.PURCHASE, amount, product.getCurrency(),
                paymentGateway.providerCode(), paymentGateway.demo());
        paymentRepository.save(payment);

        PaymentGateway.PaymentOutcome outcome = paymentGateway.charge(
                payment.getCode(), amount, product.getCurrency(), request.demoScenario());
        if (!outcome.approved()) {
            payment.fail(outcome.failureReason());
            Payment failed = paymentRepository.save(payment);
            auditService.record(PUBLIC_ACTOR, "ticket.purchase_declined", "payment",
                    failed.getCode(), null, snapshot(failed));
            return new TicketPurchaseResponse(null, null, toDto(failed),
                    paymentGateway.demo(), DEMO_NOTICE);
        }

        payment.authorize(outcome.providerRef());

        TicketKind kind = TicketKind.fromValidityMinutes(product.getValidityMinutes());
        String token = TicketTokens.newToken();
        Ticket ticket = new Ticket(UUID.randomUUID(), nextCode("TKT", ticketRepository::existsByCode),
                product.getCode(), kind, product.getRiderCategory(), riderRef,
                TicketTokens.hash(token), now, now.plusMinutes(validityMinutes(product)),
                amount, product.getCurrency(), paymentGateway.demo());
        Ticket issued = ticketRepository.save(ticket);

        payment.capture(issued.getCode());
        Payment captured = paymentRepository.save(payment);

        auditService.record(PUBLIC_ACTOR, "ticket.purchase", "ticket", issued.getCode(),
                null, snapshot(issued));
        // Токен уходит вызывающему здесь и больше не появится нигде: в билете
        // остался только его SHA-256.
        return new TicketPurchaseResponse(toDto(issued), token, toDto(captured),
                paymentGateway.demo(), DEMO_NOTICE);
    }

    /**
     * Пополнение проездного (U-CIT-08): продлевает окно действия на срок тарифа.
     *
     * <p>Требует, чтобы исходный тариф всё ещё продавался: продление снятого с
     * продажи проездного означало бы торговлю тарифом, которого больше нет. При
     * этом уже выпущенный билет от исчезновения тарифа не страдает — он просто
     * доживает свой оплаченный срок.
     */
    @Transactional
    public TicketTopUpResponse topUp(String ticketCode, TicketTopUpRequest request) {
        requireFlag(FLAG_PURCHASE, "Пополнение временно недоступно");

        Ticket ticket = find(ticketCode);
        requireTicketNotBlocked(ticket);
        if (!ticket.getKind().topUpAllowed()) {
            throw new BadRequestException("ticket.topup_not_supported",
                    "Разовый билет не пополняется",
                    Map.of("code", ticketCode, "kind", ticket.getKind().code()));
        }
        if (!ticket.getStatus().validatable()) {
            throw new BadRequestException("ticket.not_topupable",
                    "Пополнить можно только действующий проездной",
                    Map.of("code", ticketCode, "status", ticket.getStatus().code()));
        }

        FareProduct product = activeFare(ticket.getFareProductCode());
        Payment payment = new Payment(UUID.randomUUID(), nextCode("PAY", paymentRepository::existsByCode),
                PaymentKind.TOPUP, request.amount(), ticket.getPriceCurrency(),
                paymentGateway.providerCode(), paymentGateway.demo());
        paymentRepository.save(payment);

        PaymentGateway.PaymentOutcome outcome = paymentGateway.charge(
                payment.getCode(), request.amount(), ticket.getPriceCurrency(), request.demoScenario());
        if (!outcome.approved()) {
            payment.fail(outcome.failureReason());
            Payment failed = paymentRepository.save(payment);
            auditService.record(PUBLIC_ACTOR, "ticket.topup_declined", "payment",
                    failed.getCode(), null, snapshot(failed));
            return new TicketTopUpResponse(toDto(ticket), toDto(failed),
                    paymentGateway.demo(), DEMO_NOTICE);
        }

        payment.authorize(outcome.providerRef());
        payment.capture(ticket.getCode());
        Payment captured = paymentRepository.save(payment);

        Map<String, Object> before = snapshot(ticket);
        // Продлеваем от конца текущего окна, а не от «сейчас», — иначе пассажир,
        // пополнившийся заранее, теряет остаток оплаченного срока.
        OffsetDateTime now = OffsetDateTime.now(clock);
        OffsetDateTime extendFrom = ticket.getValidUntil().isAfter(now) ? ticket.getValidUntil() : now;
        ticket.applyTopUp(request.amount(), extendFrom.plusMinutes(validityMinutes(product)));
        Ticket saved = ticketRepository.save(ticket);

        auditService.record(PUBLIC_ACTOR, "ticket.topup", "ticket", saved.getCode(),
                before, snapshot(saved));
        return new TicketTopUpResponse(toDto(saved), toDto(captured),
                paymentGateway.demo(), DEMO_NOTICE);
    }

    /**
     * Валидация билета на турникете/у контролёра (TKT-04).
     *
     * <p>Всегда возвращает решение, а не исключение: турникету нужно
     * «пропускать / не пропускать». Неизвестный токен отвечает так же, как
     * недействительный билет, — иначе эндпоинт становится оракулом для перебора
     * токенов (см. {@link TicketValidationDto}).
     *
     * <p>Метод пишущий, а не read-only: он гасит разовый билет, помечает
     * истёкший и блокирует найденного в чёрном списке.
     */
    @Transactional
    public TicketValidationDto validate(String token) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        String tokenHash = TicketTokens.hash(token);

        Optional<Ticket> found = ticketRepository.findByTokenHashForUpdate(tokenHash);
        if (found.isEmpty()) {
            return unknownToken();
        }
        Ticket ticket = found.get();
        // Поиск по хешу в БД уже является сравнением на равенство; эта проверка —
        // защита в глубину на случай, если поиск когда-нибудь станет неточным
        // (регистр, коллация, LIKE). Стоит наносекунды, снимает целый класс ошибок.
        if (!TicketTokens.constantTimeEquals(ticket.getTokenHash(), tokenHash)) {
            return unknownToken();
        }

        // Чёрный список — раньше всех прочих проверок: заблокированный билет не
        // должен пройти даже если он «формально в порядке» (TKT-06).
        if (blocked(ticket, tokenHash)) {
            if (!ticket.getStatus().terminal()) {
                Map<String, Object> before = snapshot(ticket);
                ticket.block();
                Ticket saved = ticketRepository.save(ticket);
                auditService.record("antifraud", "ticket.block", "ticket", saved.getCode(),
                        before, snapshot(saved));
            }
            return invalid(ticket, "ticket.blocked");
        }

        if (!ticket.getStatus().validatable()) {
            return invalid(ticket, statusReason(ticket.getStatus()));
        }
        if (!ticket.startedAt(now)) {
            return invalid(ticket, "ticket.not_started");
        }
        if (ticket.expiredAt(now)) {
            // Помечаем истёкшим прямо здесь: иначе билет останется «active»
            // навсегда, и отчёт по действующим проездным будет врать.
            Map<String, Object> before = snapshot(ticket);
            ticket.expire();
            Ticket saved = ticketRepository.save(ticket);
            auditService.record("turnstile", "ticket.expire", "ticket", saved.getCode(),
                    before, snapshot(saved));
            return invalid(saved, "ticket.expired");
        }
        if (passbackTooSoon(ticket, now)) {
            return invalid(ticket, "ticket.validation_too_soon");
        }

        ticket.markValidated(now);
        Ticket saved = ticketRepository.save(ticket);
        return new TicketValidationDto(true, null, saved.getCode(), saved.getStatus().code(),
                saved.getKind().code(), saved.getRiderCategory(),
                saved.getValidUntil().toInstant(), saved.isDemo());
    }

    /** Возврат по заявлению пассажира (TKT-03). Погашенный билет так не вернуть. */
    @Transactional
    public RefundDto refund(String ticketCode, String reason) {
        return refundTicket(ticketCode, reason, PUBLIC_ACTOR, false);
    }

    /**
     * Возврат средств за билет (TKT-03).
     *
     * @param allowUsed разрешить возврат уже погашенного билета. Пассажиру —
     *                  {@code false}: услуга оказана, проход состоялся. Оператору
     *                  консоли — {@code true}: ручной возврат существует ровно для
     *                  случаев, которые правило не покрывает (сбой турникета,
     *                  решение по жалобе), и остаётся в аудите под его именем.
     */
    @Transactional
    public RefundDto refundTicket(String ticketCode, String reason, String actor, boolean allowUsed) {
        requireFlag(FLAG_REFUND, "Возвраты временно недоступны");

        // All refund paths lock in a stable Ticket -> Payment order.
        Ticket ticket = findForUpdate(ticketCode);
        if (ticket.getStatus() == TicketStatus.USED && !allowUsed) {
            throw new BadRequestException("ticket.not_refundable",
                    "Использованный билет не возвращается",
                    Map.of("code", ticketCode, "status", ticket.getStatus().code()));
        }
        if (!ticket.getStatus().canMoveTo(TicketStatus.REFUNDED)) {
            throw new BadRequestException("ticket.not_refundable",
                    "Билет в статусе '" + ticket.getStatus().code() + "' не подлежит возврату",
                    Map.of("code", ticketCode, "status", ticket.getStatus().code(),
                            "allowed", ticket.getStatus().allowedTransitions().stream()
                                    .map(TicketStatus::code).sorted().toList()));
        }

        // Возвращается покупка. Пополнения не возвращаются — см. PaymentKind.
        Payment payment = paymentRepository.findFirstByTicketCodeAndKindAndStatusOrderByCreatedAtDesc(
                        ticketCode, PaymentKind.PURCHASE, PaymentStatus.CAPTURED)
                .orElseThrow(() -> new BadRequestException("payment.not_refundable",
                        "По билету нет захваченного платежа-покупки",
                        Map.of("code", ticketCode)));

        Refund refund = new Refund(UUID.randomUUID(), nextCode("RFN", refundRepository::existsByCode),
                payment.getCode(), payment.getAmount(), payment.getCurrency(),
                reason, actor, paymentGateway.demo());
        refundRepository.save(refund);

        PaymentGateway.PaymentOutcome outcome = paymentGateway.refund(refund.getCode(),
                payment.getProviderRef(), payment.getAmount(), payment.getCurrency());
        if (!outcome.approved()) {
            refund.fail(outcome.failureReason());
            Refund failed = refundRepository.save(refund);
            auditService.record(actor, "ticket.refund_declined", "refund", failed.getCode(),
                    null, snapshot(failed));
            return toDto(failed);
        }

        refund.complete(outcome.providerRef());
        Refund completed = refundRepository.save(refund);

        // Три состояния меняются одной транзакцией: возврат проведён, платёж
        // возвращён, билет погашен. Частичное применение оставило бы возвращённый
        // платёж при действующем билете — то есть бесплатный проезд.
        Map<String, Object> paymentBefore = snapshot(payment);
        payment.markRefunded();
        paymentRepository.save(payment);

        Map<String, Object> ticketBefore = snapshot(ticket);
        ticket.refund();
        Ticket refunded = ticketRepository.save(ticket);

        auditService.record(actor, "ticket.refund", "ticket", refunded.getCode(),
                ticketBefore, snapshot(refunded));
        auditService.record(actor, "payment.refund", "payment", payment.getCode(),
                paymentBefore, snapshot(payment));
        return toDto(completed);
    }

    // ------------------------------- Антифрод -------------------------------

    /** Заблокирован ли билет, его токен или его покупатель (TKT-06). */
    private boolean blocked(Ticket ticket, String tokenHash) {
        return blocklistRepository.existsBySubjectTypeAndSubjectCode(
                        BlocklistSubjectType.TICKET, ticket.getCode())
                || blocklistRepository.existsBySubjectTypeAndSubjectCode(
                        BlocklistSubjectType.TOKEN, tokenHash)
                || (ticket.getRiderRef() != null
                        && blocklistRepository.existsBySubjectTypeAndSubjectCode(
                                BlocklistSubjectType.RIDER, ticket.getRiderRef()));
    }

    private boolean passbackTooSoon(Ticket ticket, OffsetDateTime now) {
        if (ticket.getLastValidatedAt() == null) {
            return false;
        }
        return now.isBefore(ticket.getLastValidatedAt().plus(ANTI_PASSBACK_INTERVAL));
    }

    private void requireRiderNotBlocked(String riderRef) {
        if (riderRef == null) {
            return;
        }
        if (blocklistRepository.existsBySubjectTypeAndSubjectCode(
                BlocklistSubjectType.RIDER, riderRef)) {
            throw new BadRequestException("ticketing.rider_blocked",
                    "Покупки для этого покупателя заблокированы",
                    Map.of("riderRef", riderRef));
        }
    }

    private void requirePurchaseRateWithinLimit(String riderRef, OffsetDateTime now) {
        if (riderRef == null) {
            // Покупка без идентификатора правилу не подлежит: считать нечего.
            // Дыра осознанная и оставлена намеренно — затыкать её обязательным
            // riderRef бессмысленно, пока идентификатор ничем не подтверждён.
            // Ограничение частоты анонимных покупок — забота RateLimitingFilter.
            return;
        }
        long recent = ticketRepository.countByRiderRefAndCreatedAtGreaterThanEqual(
                riderRef, now.minus(PURCHASE_WINDOW));
        if (recent >= PURCHASE_LIMIT_PER_WINDOW) {
            throw new BadRequestException("ticketing.purchase_limit",
                    "Превышен лимит покупок за час",
                    Map.of("riderRef", riderRef, "limit", PURCHASE_LIMIT_PER_WINDOW,
                            "windowMinutes", PURCHASE_WINDOW.toMinutes()));
        }
    }

    private void requireTicketNotBlocked(Ticket ticket) {
        if (blocked(ticket, ticket.getTokenHash())) {
            throw new BadRequestException("ticketing.ticket_blocked",
                    "Билет заблокирован",
                    Map.of("code", ticket.getCode()));
        }
    }

    // ------------------------------ Служебное -------------------------------

    private void requireFlag(String flag, String message) {
        // Дефолт true: флага может не быть в feature_flag, и demo-контур обязан
        // работать «из коробки». Выключение — осознанное действие оператора.
        if (!featureFlagService.isEnabled(flag, true)) {
            throw new ForbiddenException("ticketing.disabled", message);
        }
    }

    private Ticket find(String code) {
        return ticketRepository.findByCode(code)
                .orElseThrow(() -> new NotFoundException("ticket.not_found", "Билет не найден"));
    }

    private Ticket findForUpdate(String code) {
        return ticketRepository.findByCodeForUpdate(code)
                .orElseThrow(() -> new NotFoundException("ticket.not_found", "Ticket not found"));
    }

    private FareProduct activeFare(String code) {
        FareProduct product = fareProductRepository.findByCode(code)
                .orElseThrow(() -> new NotFoundException("fare.not_found",
                        "Тариф с кодом '" + code + "' не найден"));
        if (!product.isActive()) {
            throw new BadRequestException("ticketing.fare_inactive",
                    "Тариф '" + code + "' снят с продажи",
                    Map.of("fareProductCode", code));
        }
        return product;
    }

    private static void requirePayableAmount(BigDecimal amount, String fareCode) {
        // payment.amount > 0 по CHECK-у, и это не формальность: бесплатный
        // «платёж» — это выпуск билета без оплаты. Нулевая цена в справочнике
        // означает льготу, а верифицировать льготы нечем (нет реестра
        // льготников — внешний блокер), поэтому такой тариф не продаём вовсе.
        if (amount == null || amount.signum() <= 0) {
            throw new BadRequestException("ticketing.fare_not_payable",
                    "Тариф '" + fareCode + "' имеет нулевую цену и не продаётся в приложении",
                    Map.of("fareProductCode", fareCode));
        }
    }

    private static int validityMinutes(FareProduct product) {
        Integer minutes = product.getValidityMinutes();
        return minutes == null || minutes <= 0 ? DEFAULT_VALIDITY_MINUTES : minutes;
    }

    /**
     * Код вида TKT-2026-A1B2C3D4.
     *
     * <p>Случайный суффикс, а не счётчик: последовательный номер билета —
     * подсказка о том, сколько их выпущено, и приглашение перебирать соседние.
     * Уникальность проверяется до вставки, а гонку отсечёт UNIQUE-индекс.
     */
    private String nextCode(String prefix, java.util.function.Predicate<String> exists) {
        int year = OffsetDateTime.now(clock).getYear();
        for (int attempt = 0; attempt < 20; attempt++) {
            String suffix = HexFormat.of().toHexDigits(ThreadLocalRandom.current().nextInt())
                    .toUpperCase(Locale.ROOT);
            String code = prefix + "-" + year + "-" + suffix;
            if (!exists.test(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Не удалось сгенерировать уникальный код: " + prefix);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static TicketValidationDto unknownToken() {
        return new TicketValidationDto(false, "ticket.token_unknown", null, null, null,
                null, null, false);
    }

    private static TicketValidationDto invalid(Ticket ticket, String reason) {
        return new TicketValidationDto(false, reason, ticket.getCode(),
                ticket.getStatus().code(), ticket.getKind().code(), ticket.getRiderCategory(),
                ticket.getValidUntil().toInstant(), ticket.isDemo());
    }

    private static String statusReason(TicketStatus status) {
        return switch (status) {
            case USED -> "ticket.already_used";
            case EXPIRED -> "ticket.expired";
            case REFUNDED -> "ticket.refunded";
            case BLOCKED -> "ticket.blocked";
            case ISSUED, ACTIVE -> throw new IllegalStateException(
                    "Действующий статус не может быть причиной отказа: " + status);
        };
    }

    // ------------------------------- Снимки ---------------------------------

    /** Снимок билета для аудита. Хеша токена в нём нет — секрету не место в журнале. */
    public static Map<String, Object> snapshot(Ticket ticket) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("code", ticket.getCode());
        snapshot.put("fareProductCode", ticket.getFareProductCode());
        snapshot.put("kind", ticket.getKind().code());
        snapshot.put("status", ticket.getStatus().code());
        snapshot.put("riderCategory", ticket.getRiderCategory());
        snapshot.put("riderRef", ticket.getRiderRef());
        snapshot.put("priceAmount", ticket.getPriceAmount());
        snapshot.put("priceCurrency", ticket.getPriceCurrency());
        snapshot.put("balanceAmount", ticket.getBalanceAmount());
        snapshot.put("validUntil", ticket.getValidUntil().toInstant().toString());
        snapshot.put("demo", ticket.isDemo());
        return snapshot;
    }

    public static Map<String, Object> snapshot(Payment payment) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("code", payment.getCode());
        snapshot.put("ticketCode", payment.getTicketCode());
        snapshot.put("kind", payment.getKind().code());
        snapshot.put("status", payment.getStatus().code());
        snapshot.put("amount", payment.getAmount());
        snapshot.put("currency", payment.getCurrency());
        snapshot.put("provider", payment.getProvider());
        snapshot.put("failureReason", payment.getFailureReason());
        snapshot.put("demo", payment.isDemo());
        return snapshot;
    }

    public static Map<String, Object> snapshot(Refund refund) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("code", refund.getCode());
        snapshot.put("paymentCode", refund.getPaymentCode());
        snapshot.put("status", refund.getStatus().code());
        snapshot.put("amount", refund.getAmount());
        snapshot.put("currency", refund.getCurrency());
        snapshot.put("reason", refund.getReason());
        snapshot.put("createdBy", refund.getCreatedBy());
        snapshot.put("demo", refund.isDemo());
        return snapshot;
    }

    // ------------------------------- Маппинг --------------------------------

    public static TicketDto toDto(Ticket ticket) {
        return new TicketDto(
                ticket.getCode(),
                ticket.getFareProductCode(),
                ticket.getKind().code(),
                ticket.getRiderCategory(),
                ticket.getStatus().code(),
                ticket.getValidFrom().toInstant(),
                ticket.getValidUntil().toInstant(),
                ticket.getPriceAmount(),
                ticket.getPriceCurrency(),
                ticket.getBalanceAmount(),
                ticket.getUsedAt() == null ? null : ticket.getUsedAt().toInstant(),
                ticket.isDemo(),
                ticket.getUpdatedAt() == null ? null : ticket.getUpdatedAt().toInstant(),
                ticket.getStatus().allowedTransitions().stream()
                        .map(TicketStatus::code).sorted().toList());
    }

    public static PaymentDto toDto(Payment payment) {
        return new PaymentDto(
                payment.getCode(),
                payment.getTicketCode(),
                payment.getKind().code(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus().code(),
                payment.getProvider(),
                payment.getProviderRef(),
                payment.getFailureReason(),
                payment.isDemo(),
                payment.getCreatedAt() == null ? null : payment.getCreatedAt().toInstant(),
                payment.getUpdatedAt() == null ? null : payment.getUpdatedAt().toInstant());
    }

    public static RefundDto toDto(Refund refund) {
        return new RefundDto(
                refund.getCode(),
                refund.getPaymentCode(),
                refund.getAmount(),
                refund.getCurrency(),
                refund.getStatus().code(),
                refund.getReason(),
                refund.getFailureReason(),
                refund.getCreatedBy(),
                refund.isDemo(),
                refund.getCreatedAt() == null ? null : refund.getCreatedAt().toInstant());
    }
}
