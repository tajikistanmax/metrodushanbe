package tj.metro.dushanbe.admin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import tj.metro.dushanbe.admin.web.dto.NotificationCreateRequest;
import tj.metro.dushanbe.admin.web.dto.NotificationStatusRequest;
import tj.metro.dushanbe.admin.web.dto.NotificationTemplateCreateRequest;
import tj.metro.dushanbe.admin.web.dto.NotificationUpdateRequest;
import tj.metro.dushanbe.audit.service.AuditService;
import tj.metro.dushanbe.common.error.BadRequestException;
import tj.metro.dushanbe.common.error.NotFoundException;
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
import tj.metro.dushanbe.notification.web.dto.NotificationTargetDto;

class AdminNotificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-17T10:15:30Z");
    private static final OffsetDateTime NOW_AT = NOW.atOffset(ZoneOffset.UTC);
    private static final Map<String, String> I18N =
            Map.of("tg", "Огоҳӣ", "ru", "Внимание", "en", "Warning");

    private final NotificationMessageRepository repository = mock(NotificationMessageRepository.class);
    private final NotificationTemplateRepository templateRepository =
            mock(NotificationTemplateRepository.class);
    private final NotificationDeliveryRepository deliveryRepository =
            mock(NotificationDeliveryRepository.class);
    private final AuditService auditService = mock(AuditService.class);

    private final AdminNotificationService service = new AdminNotificationService(
            repository, templateRepository, deliveryRepository, auditService,
            Clock.fixed(NOW, ZoneOffset.UTC));

    // --- Создание ------------------------------------------------------------

    @Test
    void createStartsAsDraftAndRecordsAudit() {
        stubSave();

        var result = service.create(request(null, null), "operator1");

        assertEquals("NTF-1", result.code());
        assertEquals("draft", result.status());
        assertEquals("info", result.type());
        verify(auditService).record(eq("operator1"), eq("notification.create"),
                eq("notification_message"), eq("NTF-1"), isNull(), any());
    }

    @Test
    void createRejectsDuplicateCode() {
        when(repository.existsByCode("NTF-1")).thenReturn(true);

        var error = assertThrows(BadRequestException.class,
                () -> service.create(request(null, null), "operator1"));

        assertEquals("notification.code_exists", error.getCode());
        verify(repository, never()).save(any());
    }

    @Test
    void createFromTemplateCopiesTextsIntoTheMessage() {
        stubSave();
        stubTemplate(true);

        var result = service.create(new NotificationCreateRequest("NTF-1", "TPL-1", null,
                null, null, null, null, null, null), "operator1");

        // Тексты скопированы, шаблон остался только следом происхождения.
        assertEquals("TPL-1", result.templateCode());
        assertEquals("maintenance", result.type());
        assertEquals(I18N, result.title());
        assertEquals(I18N, result.body());
        assertEquals(List.of("in_app", "email"), result.channels());
    }

    @Test
    void createFromTemplateLetsExplicitFieldsWin() {
        stubSave();
        stubTemplate(true);
        var custom = Map.of("tg", "Матни худӣ", "ru", "Свой текст", "en", "Custom");

        var result = service.create(new NotificationCreateRequest("NTF-1", "TPL-1", null,
                "promo", custom, custom, List.of("push"), null, null), "operator1");

        assertEquals("promo", result.type());
        assertEquals(custom, result.title());
        assertEquals(List.of("push"), result.channels());
    }

    @Test
    void createFromInactiveTemplateIsRejected() {
        stubTemplate(false);

        var error = assertThrows(BadRequestException.class, () ->
                service.create(new NotificationCreateRequest("NTF-1", "TPL-1", null,
                        null, null, null, null, null, null), "operator1"));

        assertEquals("notification.template_inactive", error.getCode());
    }

    @Test
    void createFromUnknownTemplateIsNotFound() {
        when(templateRepository.findByCode("TPL-GHOST")).thenReturn(Optional.empty());

        var error = assertThrows(NotFoundException.class, () ->
                service.create(new NotificationCreateRequest("NTF-1", "TPL-GHOST", null,
                        null, null, null, null, null, null), "operator1"));

        assertEquals("notification.template_not_found", error.getCode());
    }

    @Test
    void createWithoutTemplateRequiresTexts() {
        var error = assertThrows(BadRequestException.class, () ->
                service.create(new NotificationCreateRequest("NTF-1", null, null,
                        "info", null, null, null, null, null), "operator1"));

        assertEquals("notification.field_required", error.getCode());
    }

    @Test
    void createRejectsIncompleteI18n() {
        var error = assertThrows(BadRequestException.class, () ->
                service.create(new NotificationCreateRequest("NTF-1", null, null, "info",
                        Map.of("ru", "Только русский"), I18N, List.of("in_app"), null, null),
                        "operator1"));

        assertEquals("validation.i18n_incomplete", error.getCode());
    }

    @Test
    void createRejectsUnknownChannel() {
        var error = assertThrows(BadRequestException.class, () ->
                service.create(new NotificationCreateRequest("NTF-1", null, null, "info",
                        I18N, I18N, List.of("telegram"), null, null), "operator1"));

        assertEquals("notification.channel_invalid", error.getCode());
    }

    @Test
    void createRejectsEmptyChannels() {
        var error = assertThrows(BadRequestException.class, () ->
                service.create(new NotificationCreateRequest("NTF-1", null, null, "info",
                        I18N, I18N, List.of(), null, null), "operator1"));

        assertEquals("notification.channels_required", error.getCode());
    }

    @Test
    void createWithScheduledAtGoesStraightToScheduled() {
        stubSave();
        var scheduledAt = NOW_AT.plusHours(2);

        var result = service.create(request(scheduledAt, null), "operator1");

        // Иначе планировщик не увидит рассылку и «отложенная публикация» не уйдёт.
        assertEquals("scheduled", result.status());
        assertEquals(scheduledAt.toInstant(), result.scheduledAt());
    }

    @Test
    void createStoresTargets() {
        stubSave();

        var result = service.create(request(null,
                List.of(new NotificationTargetDto("line", "L1"),
                        new NotificationTargetDto("station", "ST-1"))), "operator1");

        assertEquals(2, result.targets().size());
        assertEquals("line", result.targets().get(0).type());
        assertEquals("station", result.targets().get(1).type());
    }

    @Test
    void createRejectsUnknownTargetType() {
        when(repository.existsByCode("NTF-1")).thenReturn(false);

        var error = assertThrows(BadRequestException.class, () ->
                service.create(request(null, List.of(new NotificationTargetDto("district", "D1"))),
                        "operator1"));

        assertEquals("notification.target_type_invalid", error.getCode());
    }

    // --- Редактирование ------------------------------------------------------

    @Test
    void updateChangesContentOfADraft() {
        var message = stubFind(NotificationStatus.DRAFT);

        var result = service.update("NTF-1", new NotificationUpdateRequest("promo", I18N, I18N,
                List.of("push"), null, null), "operator1");

        assertEquals("promo", result.type());
        assertEquals(List.of("push"), result.channels());
        assertEquals(NotificationType.PROMO, message.getType());
        verify(auditService).record(eq("operator1"), eq("notification.update"),
                eq("notification_message"), eq("NTF-1"), any(), any());
    }

    @Test
    void updateOfFrozenMessageIsRejected() {
        // С началом доставки часть получателей уже получила текст — правка развела бы
        // две группы по разным текстам под одним кодом.
        for (NotificationStatus status : List.of(NotificationStatus.SENDING,
                NotificationStatus.SENT, NotificationStatus.CANCELLED)) {
            var message = stubFind(status);

            var error = assertThrows(BadRequestException.class, () ->
                    service.update("NTF-1", new NotificationUpdateRequest("info", I18N, I18N,
                            List.of("in_app"), null, null), "operator1"));

            assertEquals("notification.frozen", error.getCode());
            assertEquals(status, message.getStatus());
        }
    }

    @Test
    void updateCannotStripScheduledAtFromScheduledMessage() {
        stubFind(NotificationStatus.SCHEDULED);

        var error = assertThrows(BadRequestException.class, () ->
                service.update("NTF-1", new NotificationUpdateRequest("info", I18N, I18N,
                        List.of("in_app"), null, null), "operator1"));

        assertEquals("notification.scheduled_at_required", error.getCode());
    }

    @Test
    void updateWithScheduledAtSchedulesADraft() {
        stubFind(NotificationStatus.DRAFT);

        var result = service.update("NTF-1", new NotificationUpdateRequest("info", I18N, I18N,
                List.of("in_app"), null, NOW_AT.plusHours(1)), "operator1");

        assertEquals("scheduled", result.status());
    }

    @Test
    void getUnknownMessageReturnsDomainNotFound() {
        when(repository.findByCode("NTF-404")).thenReturn(Optional.empty());

        var error = assertThrows(NotFoundException.class, () -> service.get("NTF-404"));

        assertEquals("notification.not_found", error.getCode());
    }

    // --- Переходы ------------------------------------------------------------

    @Test
    void changeStatusRejectsIllegalTransition() {
        var message = stubFind(NotificationStatus.SENT);

        var error = assertThrows(BadRequestException.class, () ->
                service.changeStatus("NTF-1", new NotificationStatusRequest("draft"), "operator1"));

        assertEquals("notification.transition_invalid", error.getCode());
        assertEquals(NotificationStatus.SENT, message.getStatus());
    }

    @Test
    void changeStatusCancelsADraft() {
        stubFind(NotificationStatus.DRAFT);

        var result = service.changeStatus("NTF-1", new NotificationStatusRequest("cancelled"),
                "operator1");

        assertEquals("cancelled", result.status());
        verify(auditService).record(eq("operator1"), eq("notification.status"),
                eq("notification_message"), eq("NTF-1"), any(), any());
    }

    @Test
    void changeStatusCannotFakeSentWithoutDeliveries() {
        stubFind(NotificationStatus.SENDING);

        var error = assertThrows(BadRequestException.class, () ->
                service.changeStatus("NTF-1", new NotificationStatusRequest("sent"), "operator1"));

        // Иначе рассылка числилась бы отправленной без единой строки доставки.
        assertEquals("notification.send_required", error.getCode());
    }

    @Test
    void changeStatusRejectsUnknownStatus() {
        stubFind(NotificationStatus.DRAFT);

        var error = assertThrows(BadRequestException.class, () ->
                service.changeStatus("NTF-1", new NotificationStatusRequest("archived"), "operator1"));

        assertEquals("notification.status_invalid", error.getCode());
    }

    // --- Отправка ------------------------------------------------------------

    @Test
    void sendCreatesDeliveryPerRecipientAndChannelThenMarksSent() {
        var message = stubFind(NotificationStatus.DRAFT);

        var result = service.send("NTF-1", "operator1");

        assertEquals("sent", result.status());
        assertEquals(NOW_AT, message.getSentAt());

        var deliveries = capturedDeliveries();
        // Каналы рассылки — in_app и email, по одному demo-получателю на канал.
        assertEquals(2, deliveries.size());
        assertTrue(deliveries.stream().allMatch(d -> d.getStatus() == DeliveryStatus.DELIVERED));
        assertTrue(deliveries.stream().allMatch(d -> d.getAttempts() == 1));
        assertEquals(NOW_AT, deliveries.get(0).getSentAt());
    }

    @Test
    void sendMarksExternalChannelsAsSimulatedAndInAppAsReal() {
        stubFind(NotificationStatus.DRAFT);

        service.send("NTF-1", "operator1");

        var deliveries = capturedDeliveries();
        var inApp = deliveries.stream()
                .filter(d -> NotificationChannel.IN_APP.code().equals(d.getChannel()))
                .findFirst().orElseThrow();
        var email = deliveries.stream()
                .filter(d -> NotificationChannel.EMAIL.code().equals(d.getChannel()))
                .findFirst().orElseThrow();

        // in_app доставляется по-настоящему (попадает в публичный фид), email — нет.
        assertFalse(inApp.simulated());
        assertTrue(email.simulated());
    }

    @Test
    void sendPutsSimulationFlagIntoAudit() {
        stubFind(NotificationStatus.DRAFT);

        service.send("NTF-1", "operator1");

        ArgumentCaptor<Map<String, Object>> after = captor();
        verify(auditService).record(eq("operator1"), eq("notification.send"),
                eq("notification_message"), eq("NTF-1"), any(), after.capture());

        // По журналу обязано быть видно, что провайдера у канала нет.
        assertEquals(List.of("email"), after.getValue().get("simulatedChannels"));
        assertEquals(2, after.getValue().get("deliveries"));
        assertEquals("sent", after.getValue().get("status"));
    }

    @Test
    void sendOfScheduledMessageIsAllowed() {
        stubFind(NotificationStatus.SCHEDULED);

        assertEquals("sent", service.send("NTF-1", "scheduler").status());
    }

    @Test
    void sendOfAlreadySentMessageIsRejected() {
        stubFind(NotificationStatus.SENT);

        var error = assertThrows(BadRequestException.class, () -> service.send("NTF-1", "operator1"));

        assertEquals("notification.transition_invalid", error.getCode());
        verify(deliveryRepository, never()).saveAll(any());
    }

    @Test
    void sendOfCancelledMessageIsRejected() {
        stubFind(NotificationStatus.CANCELLED);

        var error = assertThrows(BadRequestException.class, () -> service.send("NTF-1", "operator1"));

        assertEquals("notification.transition_invalid", error.getCode());
    }

    // --- Лента консоли -------------------------------------------------------

    /**
     * Лента обязана брать таргеты fetch join-ом: DTO разворачивает адресацию
     * каждой рассылки, и на ленивой коллекции это был запрос на строку ленты.
     * Ленивых finder-ов без таргетов у репозитория больше нет — если кто-то
     * вернёт их, этот тест и компилятор поймают возврат к N+1.
     */
    @Test
    void listLoadsTargetsWithFetchJoinInsteadOfLazyCollection() {
        var message = message(NotificationStatus.SENT);
        message.addTarget(TargetType.LINE, "L1");
        message.addTarget(TargetType.STATION, "ST-1");
        stubIdPage(List.of(message), PageRequest.of(0, 50), 1);

        var result = service.list(null, 0, 50);

        assertEquals(1, result.items().size());
        assertEquals(2, result.items().get(0).targets().size(), "таргеты пришли тем же запросом");
        verify(repository).findAllWithTargetsByIdIn(List.of(message.getId()));
    }

    /**
     * Ключевое: LIMIT обязан уходить в SQL. Достижимо только двухшаговой выборкой —
     * страница id (без коллекций, LIMIT честный), затем fetch join ровно по этим id.
     * Если кто-то «упростит» до одного fetch join с Pageable, Hibernate снимет LIMIT
     * и порежет страницу в памяти (HHH90003004) — второй шаг исчезнет, и тест упадёт.
     */
    @Test
    void listPagesByIdsFirstSoTheLimitReachesSqlInsteadOfMemory() {
        var message = message(NotificationStatus.SENT);
        stubIdPage(List.of(message), PageRequest.of(2, 10), 25);

        service.list(null, 2, 10);

        var pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findIdPage(pageable.capture());
        assertEquals(2, pageable.getValue().getPageNumber());
        assertEquals(10, pageable.getValue().getPageSize());
        // Шаг 2 идёт по конкретным id, а не по всей таблице.
        verify(repository).findAllWithTargetsByIdIn(List.of(message.getId()));
    }

    /** Порядок ленты обязан заканчиваться уникальным полем — иначе строка с границы страниц двоится. */
    @Test
    void listSortsNewestFirstWithAUniqueTiebreaker() {
        stubIdPage(List.of(), PageRequest.of(0, 50), 0);

        service.list(null, 0, 50);

        var pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findIdPage(pageable.capture());
        var orders = pageable.getValue().getSort().toList();
        assertEquals(2, orders.size(), "ключ сортировки обязан быть составным");
        assertEquals("createdAt", orders.get(0).getProperty());
        assertEquals(Sort.Direction.DESC, orders.get(0).getDirection());
        assertEquals("code", orders.get(1).getProperty(),
                "тай-брейк по уникальному code: created_at у рассылок одного захода совпадает");
    }

    /** Контракт страницы — тот же, что у импортов: items + page/size/totals. */
    @Test
    void listReturnsTheImportsPageContract() {
        var message = message(NotificationStatus.SENT);
        stubIdPage(List.of(message), PageRequest.of(1, 20), 41);

        var result = service.list(null, 1, 20);

        assertEquals(1, result.page());
        assertEquals(20, result.size());
        assertEquals(41, result.totalElements());
        assertEquals(3, result.totalPages());
    }

    /** За последней страницей — пустой items и живые итоги, а не ошибка и не лишний запрос. */
    @Test
    void listPageBeyondTheLastOneIsEmptyButKeepsTotals() {
        stubIdPage(List.of(), PageRequest.of(50, 10), 3);

        var result = service.list(null, 50, 10);

        assertTrue(result.items().isEmpty());
        assertEquals(50, result.page());
        assertEquals(3, result.totalElements());
        // Пустой список id — второй шаг бессмыслен: `in ()` это либо лишний
        // запрос, либо синтаксическая ошибка.
        verify(repository, never()).findAllWithTargetsByIdIn(any());
    }

    /** Кривые page/size зажимаются, а не отвергаются, — как в ленте импортов и очередях. */
    @Test
    void listClampsPageAndSizeInsteadOfFailing() {
        stubIdPage(List.of(), PageRequest.of(0, 200), 0);

        service.list(null, -1, 100_000);

        var pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findIdPage(pageable.capture());
        assertEquals(0, pageable.getValue().getPageNumber(), "отрицательная страница зажимается в первую");
        assertEquals(200, pageable.getValue().getPageSize(), "размер страницы зажимается потолком");
    }

    @Test
    void listWithStatusFilterAlsoFetchJoinsTargets() {
        var message = message(NotificationStatus.DRAFT);
        message.addTarget(TargetType.LINE, "L2");
        when(repository.findIdPageByStatus(eq(NotificationStatus.DRAFT), any()))
                .thenReturn(new PageImpl<>(List.of(message.getId()), PageRequest.of(0, 50), 1));
        when(repository.findAllWithTargetsByIdIn(List.of(message.getId())))
                .thenReturn(List.of(message));

        var result = service.list("draft", 0, 50);

        assertEquals(1, result.items().size());
        assertEquals("L2", result.items().get(0).targets().get(0).code());
        verify(repository, never()).findIdPage(any());
    }

    /** Фильтр по статусу обязан пагинироваться тем же путём, а не вырождаться в полную выдачу. */
    @Test
    void listWithStatusFilterIsPagedToo() {
        when(repository.findIdPageByStatus(eq(NotificationStatus.SENT), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 5), 12));

        var result = service.list("sent", 1, 5);

        var pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findIdPageByStatus(eq(NotificationStatus.SENT), pageable.capture());
        assertEquals(1, pageable.getValue().getPageNumber());
        assertEquals(5, pageable.getValue().getPageSize());
        assertEquals(12, result.totalElements(), "итоги считаются по отфильтрованной ленте");
    }

    @Test
    void listRejectsUnknownStatusFilter() {
        var error = assertThrows(BadRequestException.class, () -> service.list("delivered", 0, 50));

        assertEquals("notification.status_invalid", error.getCode());
        verify(repository, never()).findIdPage(any());
        verify(repository, never()).findIdPageByStatus(any(), any());
    }

    /** Обе ступени выборки: страница id → fetch join ровно по её содержимому. */
    private void stubIdPage(List<NotificationMessage> messages, Pageable pageable, long total) {
        var ids = messages.stream().map(NotificationMessage::getId).toList();
        when(repository.findIdPage(any())).thenReturn(new PageImpl<>(ids, pageable, total));
        if (!ids.isEmpty()) {
            when(repository.findAllWithTargetsByIdIn(ids)).thenReturn(messages);
        }
    }

    // --- Повтор доставки (NTF-06) --------------------------------------------

    @Test
    void retryReturnsFailedDeliveryToPending() {
        var delivery = delivery(NotificationChannel.EMAIL);
        delivery.markSent(NOW_AT);
        delivery.markFailed("SMTP 550", NOW_AT);
        stubDelivery(delivery);

        var result = service.retryDelivery(delivery.getId().toString(), "operator1");

        // Не sent: повтор ставит доставку в очередь, а не выдумывает факт отправки.
        assertEquals("pending", result.status());
        assertEquals("SMTP 550", result.lastError());
        assertEquals(1, result.attempts());
        verify(auditService).record(eq("operator1"), eq("notification.retry"),
                eq("notification_delivery"), eq(delivery.getId().toString()), any(), any());
    }

    @Test
    void retryOfDeliveredIsRejected() {
        var delivery = delivery(NotificationChannel.IN_APP);
        delivery.markSent(NOW_AT);
        delivery.markDelivered(NOW_AT);
        stubDelivery(delivery);

        var error = assertThrows(BadRequestException.class,
                () -> service.retryDelivery(delivery.getId().toString(), "operator1"));

        assertEquals("notification.retry_not_failed", error.getCode());
        assertEquals(DeliveryStatus.DELIVERED, delivery.getStatus());
    }

    @Test
    void retryOfUnknownDeliveryIsNotFound() {
        var id = UUID.randomUUID();
        when(deliveryRepository.findById(id)).thenReturn(Optional.empty());

        var error = assertThrows(NotFoundException.class,
                () -> service.retryDelivery(id.toString(), "operator1"));

        assertEquals("notification_delivery.not_found", error.getCode());
    }

    @Test
    void retryWithNonUuidIdentifierIsRejected() {
        var error = assertThrows(BadRequestException.class,
                () -> service.retryDelivery("NTF-1", "operator1"));

        assertEquals("notification.delivery_id_invalid", error.getCode());
    }

    @Test
    void problemDeliveriesReturnsPendingAndFailed() {
        var failed = delivery(NotificationChannel.SMS);
        failed.markFailed("нет агрегатора", NOW_AT);
        when(deliveryRepository.findByStatusInWithMessage(
                eq(List.of(DeliveryStatus.PENDING, DeliveryStatus.FAILED)), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(failed)));

        var result = service.problemDeliveries(0, 50).items();

        assertEquals(1, result.size());
        assertEquals("failed", result.get(0).status());
        assertEquals("нет агрегатора", result.get(0).lastError());
    }

    /**
     * Границы страницы зажимаются, а не отвергаются 400: page=-1 и size за
     * потолком — опечатка в адресной строке. Потолок обязателен: без него
     * ?size= вернул бы очередь к полной выдаче, от которой пагинация и спасает.
     */
    @Test
    void problemDeliveriesClampPageAndSizeToSafeBounds() {
        when(deliveryRepository.findByStatusInWithMessage(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.problemDeliveries(-3, AdminSupport.MAX_PAGE_SIZE + 1);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(deliveryRepository).findByStatusInWithMessage(any(), captor.capture());
        assertEquals(0, captor.getValue().getPageNumber());
        assertEquals(AdminSupport.MAX_PAGE_SIZE, captor.getValue().getPageSize());
    }

    @Test
    void problemDeliveriesSizeBelowOneFallsBackToOneRow() {
        when(deliveryRepository.findByStatusInWithMessage(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.problemDeliveries(0, -1);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(deliveryRepository).findByStatusInWithMessage(any(), captor.capture());
        assertEquals(1, captor.getValue().getPageSize());
    }

    /**
     * Доставки одной рассылки создаются одним заходом и делят updated_at до
     * миллисекунды — без тай-брейка по id порядок между страницами не определён.
     */
    @Test
    void deliveryQueueSortsFreshFirstWithStableTieBreaker() {
        when(deliveryRepository.findByStatusInWithMessage(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.problemDeliveries(0, 50);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(deliveryRepository).findByStatusInWithMessage(any(), captor.capture());
        Sort sort = captor.getValue().getSort();
        assertEquals(Sort.Direction.DESC, sort.getOrderFor("updatedAt").getDirection());
        assertEquals(Sort.Direction.ASC, sort.getOrderFor("id").getDirection());
    }

    /** Пустая страница за концом очереди — пустой items и сохранённые итоги, не ошибка. */
    @Test
    void problemDeliveriesPastLastPageIsEmptyAndKeepsTotals() {
        when(deliveryRepository.findByStatusInWithMessage(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(7, 20), 15));

        var page = service.problemDeliveries(7, 20);

        assertTrue(page.items().isEmpty());
        assertEquals(7, page.page());
        assertEquals(20, page.size());
        assertEquals(15, page.totalElements());
        assertEquals(1, page.totalPages());
    }

    @Test
    void deliveriesOfOneMessageArePagedByMessageId() {
        var message = message(NotificationStatus.SENT);
        when(repository.findByCode("NTF-1")).thenReturn(Optional.of(message));
        when(deliveryRepository.findByMessageIdWithMessage(eq(message.getId()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(delivery(NotificationChannel.IN_APP)),
                        PageRequest.of(1, 2), 5));

        var page = service.deliveries("NTF-1", 1, 2);

        assertEquals(1, page.items().size());
        assertEquals(1, page.page());
        assertEquals(2, page.size());
        assertEquals(5, page.totalElements());
        assertEquals(3, page.totalPages());
    }

    // --- Шаблоны -------------------------------------------------------------

    @Test
    void createTemplateValidatesI18nAndRecordsAudit() {
        when(templateRepository.existsByCode("TPL-1")).thenReturn(false);
        when(templateRepository.save(any(NotificationTemplate.class)))
                .thenAnswer(i -> i.getArgument(0));

        var result = service.createTemplate(new NotificationTemplateCreateRequest("TPL-1",
                "Задержка поезда", "maintenance", I18N, I18N, List.of("in_app"), true), "operator1");

        assertEquals("TPL-1", result.code());
        assertTrue(result.active());
        verify(auditService).record(eq("operator1"), eq("notification.template_create"),
                eq("notification_template"), eq("TPL-1"), isNull(), any());
    }

    @Test
    void createTemplateRejectsDuplicateCode() {
        when(templateRepository.existsByCode("TPL-1")).thenReturn(true);

        var error = assertThrows(BadRequestException.class, () ->
                service.createTemplate(new NotificationTemplateCreateRequest("TPL-1", "N",
                        "info", I18N, I18N, List.of("in_app"), true), "operator1"));

        assertEquals("notification.template_code_exists", error.getCode());
    }

    @Test
    void createTemplateRejectsIncompleteI18n() {
        when(templateRepository.existsByCode("TPL-1")).thenReturn(false);

        var error = assertThrows(BadRequestException.class, () ->
                service.createTemplate(new NotificationTemplateCreateRequest("TPL-1", "N",
                        "info", Map.of("ru", "Только русский"), I18N, List.of("in_app"), true),
                        "operator1"));

        assertEquals("validation.i18n_incomplete", error.getCode());
    }

    @Test
    void deleteTemplateKeepsHistoryAndRecordsAudit() {
        stubTemplate(true);

        service.deleteTemplate("TPL-1", "operator1");

        verify(templateRepository).delete(any(NotificationTemplate.class));
        verify(auditService).record(eq("operator1"), eq("notification.template_delete"),
                eq("notification_template"), eq("TPL-1"), any(), isNull());
    }

    @Test
    void sendNextDueLocksAndSendsOneScheduledMessage() {
        var message = message(NotificationStatus.SCHEDULED);
        when(repository.findNextDueForUpdate(NOW_AT)).thenReturn(Optional.of(message));
        when(repository.save(message)).thenReturn(message);

        assertTrue(service.sendNextDue("scheduler"));
        assertEquals(NotificationStatus.SENT, message.getStatus());
    }

    // --- Хелперы -------------------------------------------------------------

    private void stubSave() {
        when(repository.existsByCode("NTF-1")).thenReturn(false);
        when(repository.save(any(NotificationMessage.class))).thenAnswer(i -> i.getArgument(0));
    }

    private NotificationMessage stubFind(NotificationStatus status) {
        NotificationMessage message = message(status);
        when(repository.findByCode("NTF-1")).thenReturn(Optional.of(message));
        when(repository.findByCodeForUpdate("NTF-1")).thenReturn(Optional.of(message));
        when(repository.save(any(NotificationMessage.class))).thenAnswer(i -> i.getArgument(0));
        return message;
    }

    private void stubDelivery(NotificationDelivery delivery) {
        when(deliveryRepository.findById(delivery.getId())).thenReturn(Optional.of(delivery));
        when(deliveryRepository.save(any(NotificationDelivery.class)))
                .thenAnswer(i -> i.getArgument(0));
    }

    private void stubTemplate(boolean active) {
        var template = new NotificationTemplate(UUID.randomUUID(), "TPL-1", "Шаблон",
                NotificationType.MAINTENANCE, I18N, I18N, List.of("in_app", "email"), active);
        when(templateRepository.findByCode("TPL-1")).thenReturn(Optional.of(template));
    }

    @SuppressWarnings("unchecked")
    private List<NotificationDelivery> capturedDeliveries() {
        ArgumentCaptor<List<NotificationDelivery>> captor = ArgumentCaptor.forClass(List.class);
        verify(deliveryRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<Map<String, Object>> captor() {
        return ArgumentCaptor.forClass(Map.class);
    }

    private static NotificationMessage message(NotificationStatus status) {
        var message = new NotificationMessage(UUID.randomUUID(), "NTF-1", null, null,
                NotificationType.INFO, I18N, I18N, List.of("in_app", "email"),
                status == NotificationStatus.SCHEDULED ? NOW_AT.plusHours(1) : null, "operator1");
        // Проводим через легальную цепочку, чтобы состояние было достижимым.
        switch (status) {
            case DRAFT -> { }
            case SCHEDULED -> message.moveTo(NotificationStatus.SCHEDULED, NOW_AT);
            case CANCELLED -> message.moveTo(NotificationStatus.CANCELLED, NOW_AT);
            case SENDING -> message.moveTo(NotificationStatus.SENDING, NOW_AT);
            case SENT -> {
                message.moveTo(NotificationStatus.SENDING, NOW_AT);
                message.moveTo(NotificationStatus.SENT, NOW_AT);
            }
            default -> throw new IllegalArgumentException("Необработанный статус: " + status);
        }
        return message;
    }

    private static NotificationDelivery delivery(NotificationChannel channel) {
        return new NotificationDelivery(UUID.randomUUID(), message(NotificationStatus.DRAFT),
                channel, "demo@metro.tj");
    }

    private static NotificationCreateRequest request(OffsetDateTime scheduledAt,
                                                     List<NotificationTargetDto> targets) {
        return new NotificationCreateRequest("NTF-1", null, null, "info", I18N, I18N,
                List.of("in_app", "email"), targets, scheduledAt);
    }
}
