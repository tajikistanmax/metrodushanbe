package tj.metro.dushanbe.admin.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tj.metro.dushanbe.admin.web.dto.NotificationCreateRequest;
import tj.metro.dushanbe.admin.web.dto.NotificationStatusRequest;
import tj.metro.dushanbe.admin.web.dto.NotificationTemplateCreateRequest;
import tj.metro.dushanbe.admin.web.dto.NotificationTemplateUpdateRequest;
import tj.metro.dushanbe.admin.web.dto.NotificationUpdateRequest;
import tj.metro.dushanbe.audit.service.AuditService;
import tj.metro.dushanbe.common.error.BadRequestException;
import tj.metro.dushanbe.common.error.NotFoundException;
import tj.metro.dushanbe.common.i18n.I18nValidator;
import tj.metro.dushanbe.notification.domain.DeliveryStatus;
import tj.metro.dushanbe.notification.domain.NotificationChannel;
import tj.metro.dushanbe.notification.domain.NotificationDelivery;
import tj.metro.dushanbe.notification.domain.NotificationMessage;
import tj.metro.dushanbe.notification.domain.NotificationStatus;
import tj.metro.dushanbe.notification.domain.NotificationTemplate;
import tj.metro.dushanbe.notification.domain.NotificationType;
import tj.metro.dushanbe.notification.domain.TargetType;
import tj.metro.dushanbe.notification.repository.NotificationDeliveryRepository;
import tj.metro.dushanbe.notification.repository.NotificationMessageRepository;
import tj.metro.dushanbe.notification.repository.NotificationTemplateRepository;
import tj.metro.dushanbe.notification.service.NotificationService;
import tj.metro.dushanbe.notification.web.dto.NotificationDeliveryDto;
import tj.metro.dushanbe.notification.web.dto.NotificationDeliveryPageDto;
import tj.metro.dushanbe.notification.web.dto.NotificationDto;
import tj.metro.dushanbe.notification.web.dto.NotificationPageDto;
import tj.metro.dushanbe.notification.web.dto.NotificationTargetDto;
import tj.metro.dushanbe.notification.web.dto.NotificationTemplateDto;

/**
 * Управление шаблонами и рассылками (NTF-01…06).
 *
 * <p>Состояние рассылки меняется только переходами из {@link NotificationStatus}:
 * карта допустимых переходов одна и та же для валидации и для подсказок в UI.
 */
@Service
public class AdminNotificationService {

    /**
     * ДЕМО-КОНТУР. Реестра получателей у системы нет и быть пока не может: контакт-центр
     * не подключён, правила обработки ПДн не заданы, внешних доступов (FCM/APNs, SMTP,
     * SMS-агрегатор) нет — см. внешние блокеры в docs/implementation-status.md и шапку V022.
     * Поэтому получатели захардкожены здесь явным demo-списком, а не берутся из БД:
     * лучше видимая заглушка в одном месте, чем правдоподобная пустая таблица
     * «подписчиков», которую примут за настоящую. При появлении реестра этот список
     * заменяется выборкой по таргетам рассылки (NTF-02) — сигнатуры менять не придётся.
     */
    private static final Map<NotificationChannel, List<String>> DEMO_RECIPIENTS = Map.of(
            NotificationChannel.IN_APP, List.of("demo-feed-subscriber"),
            NotificationChannel.PUSH, List.of("demo-push-token-0001"),
            NotificationChannel.EMAIL, List.of("demo-passenger@metro.tj"),
            NotificationChannel.SMS, List.of("+992900000001"));

    /** Что висит в очереди ошибок доставки (OPS-04): ещё не доставлено либо провалено. */
    private static final List<DeliveryStatus> PROBLEM_STATUSES =
            List.of(DeliveryStatus.PENDING, DeliveryStatus.FAILED);

    /**
     * Порядок очередей доставки: свежие сверху. Тай-брейк по id обязателен —
     * доставки одной рассылки создаются одним заходом и делят {@code updated_at}
     * до миллисекунды, а при неуникальном ключе сортировки БД вправе вернуть
     * строки в любом порядке: тогда доставка с границы страниц покажется дважды
     * либо не покажется вовсе.
     */
    private static final Sort DELIVERY_ORDER = Sort.by(Sort.Direction.DESC, "updatedAt")
            .and(Sort.by(Sort.Direction.ASC, "id"));

    /**
     * Порядок ленты рассылок: свежие сверху. Тай-брейк по {@code code} обязателен
     * по той же причине, что и у доставок: рассылки, заведённые одним заходом (в
     * частности, сидом), делят {@code created_at}, и при неуникальном ключе
     * сортировки строка с границы страниц покажется дважды либо пропадёт.
     * {@code code} для этого годится — он уникален (V022) и, в отличие от id,
     * даёт оператору воспроизводимый порядок.
     */
    private static final Sort MESSAGE_ORDER = Sort.by(Sort.Direction.DESC, "createdAt")
            .and(Sort.by(Sort.Direction.ASC, "code"));

    private final NotificationMessageRepository repository;
    private final NotificationTemplateRepository templateRepository;
    private final NotificationDeliveryRepository deliveryRepository;
    private final AuditService auditService;
    private final Clock clock;

    public AdminNotificationService(NotificationMessageRepository repository,
                                    NotificationTemplateRepository templateRepository,
                                    NotificationDeliveryRepository deliveryRepository,
                                    AuditService auditService, Clock clock) {
        this.repository = repository;
        this.templateRepository = templateRepository;
        this.deliveryRepository = deliveryRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    // --- Шаблоны -------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<NotificationTemplateDto> listTemplates() {
        return templateRepository.findAllByOrderByCodeAsc().stream()
                .map(template -> NotificationService.toDto(template))
                .toList();
    }

    @Transactional(readOnly = true)
    public NotificationTemplateDto getTemplate(String code) {
        return NotificationService.toDto(findTemplate(code));
    }

    @Transactional
    public NotificationTemplateDto createTemplate(NotificationTemplateCreateRequest request,
                                                  String actor) {
        AdminSupport.requireUnique(templateRepository.existsByCode(request.code()),
                "notification.template_code_exists", "code", request.code());
        validateTexts(request.title(), request.body());
        List<String> channels = validateChannels(request.channels());
        NotificationTemplate template = new NotificationTemplate(UUID.randomUUID(), request.code(),
                request.name(), type(request.type()), request.title(), request.body(),
                channels, request.active());
        NotificationTemplate saved = templateRepository.save(template);
        auditService.record(actor, "notification.template_create", "notification_template",
                request.code(), null, snapshot(saved));
        return NotificationService.toDto(saved);
    }

    @Transactional
    public NotificationTemplateDto updateTemplate(String code,
                                                  NotificationTemplateUpdateRequest request,
                                                  String actor) {
        NotificationTemplate template = findTemplate(code);
        validateTexts(request.title(), request.body());
        List<String> channels = validateChannels(request.channels());
        Map<String, Object> before = snapshot(template);
        template.update(request.name(), type(request.type()), request.title(), request.body(),
                channels, request.active());
        NotificationTemplate saved = templateRepository.save(template);
        auditService.record(actor, "notification.template_update", "notification_template",
                code, before, snapshot(saved));
        return NotificationService.toDto(saved);
    }

    /**
     * Удаление шаблона. Созданные из него рассылки не затрагиваются: FK нет, тексты
     * скопированы в сообщение, и история рассылки обязана пережить шаблон (шапка V022).
     */
    @Transactional
    public void deleteTemplate(String code, String actor) {
        NotificationTemplate template = findTemplate(code);
        Map<String, Object> before = snapshot(template);
        templateRepository.delete(template);
        auditService.record(actor, "notification.template_delete", "notification_template",
                code, before, null);
    }

    // --- Рассылки ------------------------------------------------------------

    /**
     * Лента рассылок консоли страницей (NTF-01), фильтр {@code ?status=} — опциональный.
     *
     * <p>Выборка ДВУХШАГОВАЯ, и это не усложнение ради усложнения. Таргеты нужны
     * каждой строке ленты ({@code toDto} разворачивает адресацию), на ленивой
     * коллекции это давало запрос на строку (N+1) — значит нужен fetch join. Но
     * fetch join коллекции вместе с {@link org.springframework.data.domain.Pageable}
     * — это HHH90003004: Hibernate снимает LIMIT и режет страницу в памяти, то есть
     * пагинация становится фиктивной, а стоимость — выше прежней полной выдачи.
     * Поэтому сперва страница id (LIMIT честно в SQL, коллекций в запросе нет),
     * затем fetch join по этим id (набор уже ограничен). Ровно тот рецепт, что
     * описан в javadoc репозитория.
     */
    @Transactional(readOnly = true)
    public NotificationPageDto list(String statusFilter, int page, int size) {
        Pageable pageable = AdminSupport.pageable(page, size, MESSAGE_ORDER);
        Page<UUID> ids = statusFilter == null || statusFilter.isBlank()
                ? repository.findIdPage(pageable)
                : repository.findIdPageByStatus(status(statusFilter), pageable);
        // За последней страницей id нет — второй шаг не делаем: `in ()` пустым
        // списком это либо лишний запрос, либо синтаксическая ошибка.
        List<NotificationMessage> messages = ids.getContent().isEmpty()
                ? List.of()
                : repository.findAllWithTargetsByIdIn(ids.getContent());
        return new NotificationPageDto(
                messages.stream().map(message -> NotificationService.toDto(message)).toList(),
                ids.getNumber(), ids.getSize(), ids.getTotalElements(), ids.getTotalPages());
    }

    @Transactional(readOnly = true)
    public NotificationDto get(String code) {
        return NotificationService.toDto(find(code));
    }

    /**
     * Создание рассылки, при необходимости — из шаблона (NTF-05).
     *
     * <p>Тексты шаблона КОПИРУЮТСЯ в сообщение и дальше от шаблона не зависят:
     * последующая правка или удаление шаблона не имеет права переписать то, что
     * уже создано и тем более отправлено. {@code templateCode} остаётся только
     * следом происхождения.
     */
    @Transactional
    @CacheEvict(value = "notifications", allEntries = true)
    public NotificationDto create(NotificationCreateRequest request, String actor) {
        AdminSupport.requireUnique(repository.existsByCode(request.code()),
                "notification.code_exists", "code", request.code());

        NotificationTemplate template = null;
        if (request.templateCode() != null && !request.templateCode().isBlank()) {
            template = findTemplate(request.templateCode());
            if (!template.isActive()) {
                throw new BadRequestException("notification.template_inactive",
                        "Шаблон '" + template.getCode() + "' выведен из употребления",
                        Map.of("field", "templateCode", "value", template.getCode()));
            }
        }

        // Шаблон задаёт значения по умолчанию, явные поля запроса их перекрывают.
        NotificationType type = request.type() != null
                ? type(request.type())
                : requireFromTemplate(template, "type", NotificationTemplate::getType);
        Map<String, String> title = request.title() != null
                ? request.title()
                : requireFromTemplate(template, "title", NotificationTemplate::getTitleI18n);
        Map<String, String> body = request.body() != null
                ? request.body()
                : requireFromTemplate(template, "body", NotificationTemplate::getBodyI18n);
        List<String> requestedChannels = request.channels() != null
                ? request.channels()
                : requireFromTemplate(template, "channels", NotificationTemplate::getChannels);
        List<String> channels = validateChannels(requestedChannels);
        validateTexts(title, body);

        NotificationMessage message = new NotificationMessage(UUID.randomUUID(), request.code(),
                template != null ? template.getCode() : null, blankToNull(request.alertCode()),
                type, title, body, channels, request.scheduledAt(), actor);
        applyTargets(message, request.targets());

        // Заданное время публикации — это и есть заявка на отложенную отправку
        // (NTF-05): держать такую рассылку в draft значило бы, что планировщик её
        // не увидит и «запланированное» не уйдёт.
        if (request.scheduledAt() != null) {
            message.moveTo(NotificationStatus.SCHEDULED, OffsetDateTime.now(clock));
        }

        NotificationMessage saved = repository.save(message);
        auditService.record(actor, "notification.create", "notification_message",
                request.code(), null, snapshot(saved));
        return NotificationService.toDto(saved);
    }

    /**
     * Редактирование рассылки. Замороженную ({@code status.frozen()}) править нельзя:
     * с началом доставки часть получателей уже получила текст, и правка развела бы
     * две группы по разным текстам под одним кодом (см. javadoc NotificationStatus).
     */
    @Transactional
    @CacheEvict(value = "notifications", allEntries = true)
    public NotificationDto update(String code, NotificationUpdateRequest request, String actor) {
        NotificationMessage message = find(code);
        if (message.getStatus().frozen()) {
            throw new BadRequestException("notification.frozen",
                    "Рассылка в состоянии '" + message.getStatus().code() + "' не редактируется",
                    Map.of("code", code, "status", message.getStatus().code()));
        }
        // Запланированная рассылка обязана знать своё время — иначе запись не примет
        // БД (chk_notification_message_scheduled).
        if (message.getStatus() == NotificationStatus.SCHEDULED && request.scheduledAt() == null) {
            throw new BadRequestException("notification.scheduled_at_required",
                    "Запланированная рассылка не может остаться без scheduledAt",
                    Map.of("field", "scheduledAt", "code", code));
        }
        validateTexts(request.title(), request.body());
        List<String> channels = validateChannels(request.channels());

        Map<String, Object> before = snapshot(message);
        message.update(type(request.type()), request.title(), request.body(), channels,
                request.scheduledAt());
        applyTargets(message, request.targets());
        // Проставленное время публикации переводит черновик в scheduled — та же
        // логика, что при создании: заявка на отложенную отправку.
        if (message.getStatus() == NotificationStatus.DRAFT && request.scheduledAt() != null) {
            message.moveTo(NotificationStatus.SCHEDULED, OffsetDateTime.now(clock));
        }
        NotificationMessage saved = repository.save(message);
        auditService.record(actor, "notification.update", "notification_message", code,
                before, snapshot(saved));
        return NotificationService.toDto(saved);
    }

    /** Перевод рассылки в новое состояние с проверкой допустимости перехода. */
    @Transactional
    @CacheEvict(value = "notifications", allEntries = true)
    public NotificationDto changeStatus(String code, NotificationStatusRequest request, String actor) {
        NotificationMessage message = find(code);
        NotificationStatus target = status(request.status());
        requireTransition(message.getStatus(), target);
        // Отправку через этот путь не пропускаем: она обязана создать доставки,
        // а не просто переписать статус (иначе рассылка числится sent без единой
        // строки в notification_delivery).
        if (target == NotificationStatus.SENT) {
            throw new BadRequestException("notification.send_required",
                    "Отметить рассылку отправленной можно только операцией send",
                    Map.of("code", code, "endpoint", "POST /v1/admin/notifications/" + code + "/send"));
        }
        Map<String, Object> before = snapshot(message);
        message.moveTo(target, OffsetDateTime.now(clock));
        NotificationMessage saved = repository.save(message);
        auditService.record(actor, "notification.status", "notification_message", code,
                before, snapshot(saved));
        return NotificationService.toDto(saved);
    }

    /**
     * Отправка рассылки (NTF-01, NTF-06): draft/scheduled → sending, создание
     * доставки на каждого получателя × канал, затем sent.
     *
     * <p><b>Что здесь реально происходит.</b> Настоящая доставка есть только у
     * {@code in_app} — сообщение попадает в публичный фид, и это проверяемо. Для
     * каналов с {@link NotificationChannel#external()} провайдера нет (см. внешние
     * блокеры), поэтому доставка ИМИТИРУЕТСЯ: строка создаётся и помечается
     * delivered, но признак имитации идёт и в аудит ({@code simulatedChannels}),
     * и в DTO ({@code simulated}) — молчаливое «доставлено» там, где ничего не
     * ушло, хуже, чем отсутствие канала.
     */
    @Transactional
    @CacheEvict(value = "notifications", allEntries = true)
    public NotificationDto send(String code, String actor) {
        NotificationMessage message = repository.findByCodeForUpdate(code)
                .orElseThrow(() -> new NotFoundException("notification.not_found",
                        "Рассылка не найдена"));
        return sendLocked(message, actor);
    }

    /** Забирает и отправляет одну due-рассылку в короткой транзакции со SKIP LOCKED. */
    @Transactional
    @CacheEvict(value = "notifications", allEntries = true)
    public boolean sendNextDue(String actor) {
        return repository.findNextDueForUpdate(OffsetDateTime.now(clock))
                .map(message -> {
                    sendLocked(message, actor);
                    return true;
                })
                .orElse(false);
    }

    private NotificationDto sendLocked(NotificationMessage message, String actor) {
        String code = message.getCode();
        requireTransition(message.getStatus(), NotificationStatus.SENDING);

        OffsetDateTime now = OffsetDateTime.now(clock);
        Map<String, Object> before = snapshot(message);
        message.moveTo(NotificationStatus.SENDING, now);

        List<NotificationDelivery> deliveries = new ArrayList<>();
        List<String> simulatedChannels = new ArrayList<>();
        for (NotificationChannel channel : channels(message.getChannels())) {
            if (channel.external()) {
                simulatedChannels.add(channel.code());
            }
            for (String recipient : DEMO_RECIPIENTS.getOrDefault(channel, List.of())) {
                NotificationDelivery delivery = new NotificationDelivery(
                        UUID.randomUUID(), message, channel, recipient);
                delivery.markSent(now);
                delivery.markDelivered(now);
                deliveries.add(delivery);
            }
        }
        deliveryRepository.saveAll(deliveries);

        message.moveTo(NotificationStatus.SENT, now);
        NotificationMessage saved = repository.save(message);

        Map<String, Object> after = snapshot(saved);
        after.put("deliveries", deliveries.size());
        after.put("simulatedChannels", simulatedChannels);
        auditService.record(actor, "notification.send", "notification_message", code, before, after);
        return NotificationService.toDto(saved);
    }

    // --- Доставки ------------------------------------------------------------

    /** Доставки одной рассылки страницей (NTF-06). */
    @Transactional(readOnly = true)
    public NotificationDeliveryPageDto deliveries(String code, int page, int size) {
        return toPage(deliveryRepository.findByMessageIdWithMessage(find(code).getId(),
                AdminSupport.pageable(page, size, DELIVERY_ORDER)));
    }

    /** Очередь ошибок доставки страницей (OPS-04): всё, что не доставлено. */
    @Transactional(readOnly = true)
    public NotificationDeliveryPageDto problemDeliveries(int page, int size) {
        return toPage(deliveryRepository.findByStatusInWithMessage(PROBLEM_STATUSES,
                AdminSupport.pageable(page, size, DELIVERY_ORDER)));
    }

    private static NotificationDeliveryPageDto toPage(Page<NotificationDelivery> result) {
        return new NotificationDeliveryPageDto(
                result.getContent().stream().map(d -> NotificationService.toDto(d)).toList(),
                result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    /**
     * Повторная отправка проваленной доставки (NTF-06): failed → pending.
     *
     * <p>Возврат именно в pending, а не в sent, — см. javadoc {@link DeliveryStatus}:
     * повтор ставит доставку обратно в очередь, а не выдумывает факт отправки.
     */
    @Transactional
    public NotificationDeliveryDto retryDelivery(String deliveryCode, String actor) {
        NotificationDelivery delivery = deliveryRepository.findById(deliveryId(deliveryCode))
                .orElseThrow(() -> new NotFoundException("notification_delivery.not_found",
                        "Доставка не найдена"));
        DeliveryStatus current = delivery.getStatus();
        if (!current.canMoveTo(DeliveryStatus.PENDING)) {
            throw new BadRequestException("notification.retry_not_failed",
                    "Повтор возможен только для проваленной доставки, текущий статус: "
                            + current.code(),
                    Map.of("id", deliveryCode, "status", current.code(),
                            "allowed", DeliveryStatus.FAILED.code()));
        }
        Map<String, Object> before = snapshot(delivery);
        delivery.retry(OffsetDateTime.now(clock));
        NotificationDelivery saved = deliveryRepository.save(delivery);
        auditService.record(actor, "notification.retry", "notification_delivery", deliveryCode,
                before, snapshot(saved));
        return NotificationService.toDto(saved);
    }

    // --- Хелперы -------------------------------------------------------------

    private NotificationMessage find(String code) {
        return repository.findByCode(code)
                .orElseThrow(() -> new NotFoundException("notification.not_found",
                        "Рассылка не найдена"));
    }

    private NotificationTemplate findTemplate(String code) {
        return templateRepository.findByCode(code)
                .orElseThrow(() -> new NotFoundException("notification.template_not_found",
                        "Шаблон рассылки не найден"));
    }

    private static void requireTransition(NotificationStatus current, NotificationStatus target) {
        if (!current.canMoveTo(target)) {
            throw new BadRequestException("notification.transition_invalid",
                    "Переход " + current.code() + " → " + target.code() + " недопустим",
                    Map.of("from", current.code(), "to", target.code(),
                            "allowed", current.allowedTransitions().stream()
                                    .map(NotificationStatus::code).sorted().toList()));
        }
    }

    /** Полная замена адресации рассылки (NTF-02); пустой список = вся сеть. */
    private static void applyTargets(NotificationMessage message, List<NotificationTargetDto> targets) {
        message.replaceTargets(List.of());
        if (targets == null) {
            return;
        }
        for (NotificationTargetDto target : targets) {
            message.addTarget(targetType(target.type()), target.code());
        }
    }

    private static void validateTexts(Map<String, String> title, Map<String, String> body) {
        I18nValidator.requireAll(title, "title");
        I18nValidator.requireAll(body, "body");
    }

    /**
     * Проверка состава каналов (NTF-01). Валидируется здесь, а не CHECK-констрейнтом:
     * по элементам jsonb-массива проверка нечитаема и не переживёт добавления канала
     * (см. V022).
     */
    private static List<String> validateChannels(List<String> channels) {
        if (channels == null || channels.isEmpty()) {
            throw new BadRequestException("notification.channels_required",
                    "Нужен хотя бы один канал доставки",
                    Map.of("field", "channels", "allowed", NotificationChannel.codes()));
        }
        return channels(channels).stream().map(NotificationChannel::code).distinct().toList();
    }

    private static List<NotificationChannel> channels(List<String> codes) {
        return codes.stream().map(code -> NotificationChannel.fromCode(code).orElseThrow(() ->
                new BadRequestException("notification.channel_invalid",
                        "Недопустимый канал: " + code,
                        Map.of("field", "channels", "value", String.valueOf(code),
                                "allowed", NotificationChannel.codes())))).toList();
    }

    private static NotificationType type(String code) {
        return NotificationType.fromCode(code).orElseThrow(() ->
                new BadRequestException("notification.type_invalid", "Недопустимый тип: " + code,
                        Map.of("field", "type", "allowed", NotificationType.codes())));
    }

    private static NotificationStatus status(String code) {
        return NotificationStatus.fromCode(code).orElseThrow(() ->
                new BadRequestException("notification.status_invalid", "Недопустимый статус: " + code,
                        Map.of("field", "status", "allowed", NotificationStatus.codes())));
    }

    private static TargetType targetType(String code) {
        return TargetType.fromCode(code).orElseThrow(() ->
                new BadRequestException("notification.target_type_invalid",
                        "Недопустимый тип таргета: " + code,
                        Map.of("field", "targets.type", "allowed", TargetType.codes())));
    }

    /** У доставки нет колонки code (V022) — её внешний идентификатор это uuid. */
    private static UUID deliveryId(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("notification.delivery_id_invalid",
                    "Идентификатор доставки должен быть UUID: " + value,
                    Map.of("field", "id", "value", String.valueOf(value)));
        }
    }

    /** Поле не задано ни в запросе, ни в шаблоне — создавать рассылку не из чего. */
    private static <T> T requireFromTemplate(NotificationTemplate template, String field,
                                             java.util.function.Function<NotificationTemplate, T> getter) {
        if (template == null) {
            throw new BadRequestException("notification.field_required",
                    "Поле '" + field + "' обязательно, если рассылка создаётся не из шаблона",
                    Map.of("field", field));
        }
        return getter.apply(template);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Map<String, Object> snapshot(NotificationMessage message) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("code", message.getCode());
        snapshot.put("templateCode", message.getTemplateCode());
        snapshot.put("alertCode", message.getAlertCode());
        snapshot.put("type", message.getType().code());
        snapshot.put("status", message.getStatus().code());
        snapshot.put("channels", message.getChannels());
        snapshot.put("targets", message.getTargets().stream()
                .map(t -> t.getTargetType().code() + ":" + t.getTargetCode()).sorted().toList());
        snapshot.put("scheduledAt", message.getScheduledAt());
        snapshot.put("sentAt", message.getSentAt());
        return snapshot;
    }

    private static Map<String, Object> snapshot(NotificationTemplate template) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("code", template.getCode());
        snapshot.put("name", template.getName());
        snapshot.put("type", template.getType().code());
        snapshot.put("channels", template.getChannels());
        snapshot.put("active", template.isActive());
        return snapshot;
    }

    private static Map<String, Object> snapshot(NotificationDelivery delivery) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", delivery.getId().toString());
        snapshot.put("channel", delivery.getChannel());
        snapshot.put("status", delivery.getStatus().code());
        snapshot.put("attempts", delivery.getAttempts());
        snapshot.put("lastError", delivery.getLastError());
        // Признак имитации обязан быть виден в журнале: иначе по аудиту не отличить
        // реально доставленное от «доставленного» каналом без провайдера.
        snapshot.put("simulated", delivery.simulated());
        return snapshot;
    }
}
