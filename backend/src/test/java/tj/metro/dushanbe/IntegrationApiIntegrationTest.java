package tj.metro.dushanbe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tj.metro.dushanbe.integration.domain.OutboxEvent;
import tj.metro.dushanbe.integration.domain.WebhookDelivery;
import tj.metro.dushanbe.integration.domain.WebhookDeliveryStatus;
import tj.metro.dushanbe.integration.domain.WebhookEventType;
import tj.metro.dushanbe.integration.domain.WebhookSubscription;
import tj.metro.dushanbe.integration.repository.OutboxEventRepository;
import tj.metro.dushanbe.integration.repository.WebhookDeliveryRepository;
import tj.metro.dushanbe.integration.repository.WebhookSubscriptionRepository;
import tj.metro.dushanbe.integration.service.OutboxService;
import tj.metro.dushanbe.integration.service.WebhookDispatcher;
import tj.metro.dushanbe.integration.service.WebhookSignature;

/**
 * Проверяет V024 и контур интеграций на реальном PostgreSQL/PostGIS: outbox в
 * одной транзакции с бизнес-изменением (INT-03), retry и DLQ (INT-05), ручной
 * повтор и очередь ошибок (U-OPS-04), приём телеметрии (U-INT-04) и то, что
 * секрет подписчика не утекает наружу (ADM-06).
 *
 * <p>Подписчики во всех сценариях указывают на 127.0.0.1:1 — заведомо закрытый
 * порт loopback: доставка гарантированно и мгновенно проваливается, не выходя в
 * сеть. Флаг {@code integration.webhooks} при этом остаётся выключенным, поэтому
 * фоновый {@code @Scheduled}-тик в тест не вмешивается, а нужные попытки
 * выполняются явным вызовом {@link WebhookDispatcher#deliver}.
 *
 * <p>Пути — без префикса /api: TestRestTemplate сам добавляет context-path.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IntegrationApiIntegrationTest {

    private static final String ADMIN_KEY = "test-admin-key";
    private static final String UNREACHABLE = "http://127.0.0.1:1/hook";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("metro")
            .withUsername("metro")
            .withPassword("metro");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.admin.dev-key", () -> ADMIN_KEY);
        // Попытка к закрытому порту отвергается сразу; таймаут — страховка от
        // зависания теста, если среда молча дропает пакеты.
        registry.add("app.integration.timeout", () -> "500ms");
        registry.add("app.integration.max-attempts", () -> "3");
        registry.add("app.integration.retry-base-delay", () -> "30s");
    }

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OutboxService outboxService;

    @Autowired
    private WebhookDispatcher dispatcher;

    @Autowired
    private OutboxEventRepository outboxRepository;

    @Autowired
    private WebhookDeliveryRepository deliveryRepository;

    @Autowired
    private WebhookSubscriptionRepository subscriptionRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    // ---------------------------------------------------------------- INT-03

    @Test
    void outboxEventIsCommittedTogetherWithTheBusinessChange() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        var event = transaction.execute(status ->
                outboxService.publish(WebhookEventType.ALERT_PUBLISHED, "alert", "ALERT-TX-OK",
                        Map.of("code", "ALERT-TX-OK")));

        assertNotNull(event);
        assertTrue(outboxRepository.existsByEventId(event.getEventId()),
                "закоммиченная транзакция обязана оставить событие в outbox");
        assertFalse(outboxRepository
                        .findByAggregateTypeAndAggregateCodeOrderByOccurredAtDesc("alert", "ALERT-TX-OK")
                        .isEmpty());
    }

    /**
     * Ключевая гарантия INT-03: «отправили вебхук, а транзакция откатилась» не
     * должно случаться. Так как наружу мы не ходим вовсе, а пишем строку в БД,
     * откат бизнес-транзакции обязан унести с собой и событие.
     */
    @Test
    void rolledBackBusinessTransactionLeavesNoOutboxEventBehind() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        UUID[] eventId = new UUID[1];

        assertThrows(IllegalStateException.class, () -> transaction.execute(status -> {
            eventId[0] = outboxService.publish(WebhookEventType.INCIDENT_OPENED, "incident",
                    "INC-TX-FAIL", Map.of("code", "INC-TX-FAIL")).getEventId();
            // Бизнес-операция падает уже после записи события в outbox.
            throw new IllegalStateException("бизнес-правило нарушено после публикации события");
        }));

        assertNotNull(eventId[0]);
        assertFalse(outboxRepository.existsByEventId(eventId[0]),
                "событие обязано откатиться вместе с бизнес-изменением");
        assertTrue(outboxRepository
                        .findByAggregateTypeAndAggregateCodeOrderByOccurredAtDesc("incident", "INC-TX-FAIL")
                        .isEmpty());
    }

    @Test
    void publishFansOutOnlyToActiveSubscribersOfThatEventType() {
        subscriptionRepository.save(subscription("fanout-match", true,
                List.of(WebhookEventType.SCHEDULE_CHANGED.code())));
        subscriptionRepository.save(subscription("fanout-other-type", true,
                List.of(WebhookEventType.TRAIN_DELAYED.code())));
        subscriptionRepository.save(subscription("fanout-inactive", false,
                List.of(WebhookEventType.SCHEDULE_CHANGED.code())));

        var event = publish(WebhookEventType.SCHEDULE_CHANGED, "schedule", "SCH-FANOUT");

        assertTrue(deliveryRepository
                .findByEventIdAndSubscriptionCode(event.getEventId(), "fanout-match").isPresent());
        assertTrue(deliveryRepository
                        .findByEventIdAndSubscriptionCode(event.getEventId(), "fanout-other-type").isEmpty(),
                "подписчик другого типа события не должен получить доставку");
        assertTrue(deliveryRepository
                        .findByEventIdAndSubscriptionCode(event.getEventId(), "fanout-inactive").isEmpty(),
                "отключённый подписчик не должен получить доставку");
    }

    @Test
    void publishedEventCarriesTraceIdForCrossSystemDebugging() {
        var event = publish(WebhookEventType.ALERT_CLEARED, "alert", "ALERT-TRACE");

        assertNotNull(event.getTraceId(), "без трассы событие не связать с его доставками (INT-05)");
        assertFalse(outboxRepository.findByTraceIdOrderByOccurredAtAsc(event.getTraceId()).isEmpty());
    }

    // ---------------------------------------------------------------- INT-05

    @Test
    void failedAttemptIncrementsAttemptsAndPushesNextAttemptIntoTheFuture() {
        subscriptionRepository.save(subscription("retry-sub", true,
                List.of(WebhookEventType.ALERT_PUBLISHED.code())));
        var event = publish(WebhookEventType.ALERT_PUBLISHED, "alert", "ALERT-RETRY");
        WebhookDelivery delivery = deliveryRepository
                .findByEventIdAndSubscriptionCode(event.getEventId(), "retry-sub").orElseThrow();
        OffsetDateTime scheduledBefore = delivery.getNextAttemptAt();
        assertEquals(0, delivery.getAttempts());

        dispatcher.deliver(delivery);

        WebhookDelivery after = deliveryRepository.findByCode(delivery.getCode()).orElseThrow();
        assertEquals(WebhookDeliveryStatus.FAILED, after.getStatus());
        assertEquals(1, after.getAttempts());
        assertTrue(after.getNextAttemptAt().isAfter(scheduledBefore),
                "провал обязан отодвинуть следующую попытку");
        assertNotNull(after.getLastError(), "chk_webhook_delivery_error требует причину провала");
    }

    @Test
    void exhaustedRetriesLandInDeadLetterQueueAndSurfaceInOperatorQueue() {
        subscriptionRepository.save(subscription("dlq-sub", true,
                List.of(WebhookEventType.INCIDENT_OPENED.code())));
        var event = publish(WebhookEventType.INCIDENT_OPENED, "incident", "INC-DLQ");
        WebhookDelivery delivery = deliveryRepository
                .findByEventIdAndSubscriptionCode(event.getEventId(), "dlq-sub").orElseThrow();

        // max-attempts = 3 (см. @DynamicPropertySource)
        for (int attempt = 0; attempt < 3; attempt++) {
            dispatcher.deliver(deliveryRepository.findByCode(delivery.getCode()).orElseThrow());
        }

        WebhookDelivery dead = deliveryRepository.findByCode(delivery.getCode()).orElseThrow();
        assertEquals(WebhookDeliveryStatus.DEAD, dead.getStatus(), "исчерпание попыток даёт DLQ");
        assertEquals(3, dead.getAttempts());
        assertNull(dead.getNextAttemptAt(), "мёртвую доставку диспетчер больше не забирает");
        assertTrue(deliveryQueueCodes(null).contains(dead.getCode()),
                "DLQ обязана быть видна оператору в очереди ошибок (U-OPS-04)");
    }

    // -------------------------------------------------------------- U-OPS-04

    @Test
    void manualRetryReturnsDeadDeliveryToPendingAndResetsSchedule() throws Exception {
        subscriptionRepository.save(subscription("manual-retry-sub", true,
                List.of(WebhookEventType.STATION_STATUS_CHANGED.code())));
        var event = publish(WebhookEventType.STATION_STATUS_CHANGED, "station", "ST-MANUAL");
        WebhookDelivery delivery = deliveryRepository
                .findByEventIdAndSubscriptionCode(event.getEventId(), "manual-retry-sub").orElseThrow();
        for (int attempt = 0; attempt < 3; attempt++) {
            dispatcher.deliver(deliveryRepository.findByCode(delivery.getCode()).orElseThrow());
        }
        assertEquals(WebhookDeliveryStatus.DEAD,
                deliveryRepository.findByCode(delivery.getCode()).orElseThrow().getStatus());

        ResponseEntity<String> response = rest.exchange(
                "/v1/admin/webhooks/deliveries/" + delivery.getCode() + "/retry",
                HttpMethod.POST, new HttpEntity<>(adminHeaders()), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals("pending", body.path("status").asText());
        assertEquals(3, body.path("attempts").asInt(), "счётчик попыток не обнуляется");

        WebhookDelivery requeued = deliveryRepository.findByCode(delivery.getCode()).orElseThrow();
        assertEquals(WebhookDeliveryStatus.PENDING, requeued.getStatus());
        assertNotNull(requeued.getNextAttemptAt(), "повтор обязан вернуть доставку в расписание");
        assertTrue(requeued.getNextAttemptAt().isBefore(OffsetDateTime.now().plusSeconds(5)),
                "ближайший тик обязан её забрать, а не ждать экспоненциальную паузу");
    }

    @Test
    void operatorQueueShowsFailureReasonAndHidesSucceededDeliveries() throws Exception {
        subscriptionRepository.save(subscription("queue-sub", true,
                List.of(WebhookEventType.TRAIN_DELAYED.code())));
        var event = publish(WebhookEventType.TRAIN_DELAYED, "train", "TR-QUEUE");
        WebhookDelivery delivery = deliveryRepository
                .findByEventIdAndSubscriptionCode(event.getEventId(), "queue-sub").orElseThrow();
        dispatcher.deliver(delivery);

        ResponseEntity<String> response = rest.exchange("/v1/admin/webhooks/deliveries",
                HttpMethod.GET, new HttpEntity<>(adminHeaders()), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode queue = objectMapper.readTree(response.getBody()).path("items");
        JsonNode row = null;
        for (JsonNode item : queue) {
            assertTrue(List.of("failed", "dead").contains(item.path("status").asText()),
                    "в очереди ошибок нет места успешным доставкам");
            if (delivery.getCode().equals(item.path("code").asText())) {
                row = item;
            }
        }
        assertNotNull(row, "провалившаяся доставка обязана попасть в очередь");
        assertFalse(row.path("lastError").asText().isBlank(), "без причины строка бесполезна");
        assertEquals("train_delayed", row.path("eventType").asText());
        assertEquals("TR-QUEUE", row.path("aggregateCode").asText());
        assertTrue(row.path("retryable").asBoolean());
    }

    // --------------------------------------------------------------- ADM-06

    @Test
    void webhookSecretIsShownOnceOnCreateAndNeverLeaksInGetResponses() throws Exception {
        Map<String, Object> body = Map.of(
                "code", "secret-sub",
                "name", "Городской портал",
                "targetUrl", UNREACHABLE,
                "eventTypes", List.of("alert_published"),
                "active", true,
                "rateLimitPerMinute", 60);

        ResponseEntity<String> created = rest.exchange("/v1/admin/webhooks", HttpMethod.POST,
                new HttpEntity<>(body, adminHeaders()), String.class);

        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        JsonNode createdBody = objectMapper.readTree(created.getBody());
        String secret = createdBody.path("secret").asText();
        assertFalse(secret.isBlank(), "создание — единственное место, где секрет виден");

        String storedHash = subscriptionRepository.findByCode("secret-sub").orElseThrow().getSecretHash();
        assertEquals(WebhookSignature.hashSecret(secret), storedHash, "в БД лежит только хеш");

        // GET списка и карточки не должны отдавать ни секрет, ни ключ подписи.
        for (String path : List.of("/v1/admin/webhooks", "/v1/admin/webhooks/secret-sub")) {
            ResponseEntity<String> read = rest.exchange(path, HttpMethod.GET,
                    new HttpEntity<>(adminHeaders()), String.class);
            assertEquals(HttpStatus.OK, read.getStatusCode());
            String raw = read.getBody();
            assertNotNull(raw);
            assertFalse(raw.contains(secret), path + " не должен отдавать секрет");
            assertFalse(raw.contains(storedHash), path + " не должен отдавать ключ подписи");
            assertFalse(raw.contains("\"secretHash\""), path + " не должен иметь поля secretHash");
        }
    }

    @Test
    void rotateSecretIssuesNewKeyAndInvalidatesTheOldSignature() throws Exception {
        Map<String, Object> body = Map.of(
                "code", "rotate-sub", "name", "Портал", "targetUrl", UNREACHABLE,
                "eventTypes", List.of("alert_published"), "active", true, "rateLimitPerMinute", 60);
        ResponseEntity<String> created = rest.exchange("/v1/admin/webhooks", HttpMethod.POST,
                new HttpEntity<>(body, adminHeaders()), String.class);
        String firstSecret = objectMapper.readTree(created.getBody()).path("secret").asText();

        ResponseEntity<String> rotated = rest.exchange("/v1/admin/webhooks/rotate-sub/secret",
                HttpMethod.POST, new HttpEntity<>(adminHeaders()), String.class);

        assertEquals(HttpStatus.OK, rotated.getStatusCode());
        String secondSecret = objectMapper.readTree(rotated.getBody()).path("secret").asText();
        assertFalse(secondSecret.equals(firstSecret), "ротация обязана выдать другой ключ");
        String storedHash = subscriptionRepository.findByCode("rotate-sub").orElseThrow().getSecretHash();
        assertEquals(WebhookSignature.hashSecret(secondSecret), storedHash);

        // Подпись старым ключом больше не сходится — ровно то, что увидит подписчик,
        // не обновивший секрет у себя.
        String payload = "{\"eventType\":\"alert_published\"}";
        assertFalse(WebhookSignature.verify(storedHash, payload,
                WebhookSignature.sign(WebhookSignature.hashSecret(firstSecret), payload)));
    }

    @Test
    void hmacSignatureIsStableAndVerifiableWithTheIssuedSecret() throws Exception {
        Map<String, Object> body = Map.of(
                "code", "hmac-sub", "name", "Портал", "targetUrl", UNREACHABLE,
                "eventTypes", List.of("alert_published"), "active", true, "rateLimitPerMinute", 60);
        ResponseEntity<String> created = rest.exchange("/v1/admin/webhooks", HttpMethod.POST,
                new HttpEntity<>(body, adminHeaders()), String.class);
        String secret = objectMapper.readTree(created.getBody()).path("secret").asText();
        String storedHash = subscriptionRepository.findByCode("hmac-sub").orElseThrow().getSecretHash();

        String payload = "{\"eventId\":\"abc\",\"eventType\":\"alert_published\"}";
        // Подписчик выводит ключ из выданного ему секрета сам — и подпись сходится.
        String subscriberSide = WebhookSignature.sign(WebhookSignature.hashSecret(secret), payload);
        String ourSide = WebhookSignature.sign(storedHash, payload);

        assertEquals(ourSide, subscriberSide, "подпись обязана быть проверяемой подписчиком");
        assertEquals(ourSide, WebhookSignature.sign(storedHash, payload), "и стабильной между повторами");
        assertTrue(ourSide.startsWith("sha256="));
        assertTrue(WebhookSignature.verify(storedHash, payload, subscriberSide));
        assertFalse(WebhookSignature.verify(storedHash, payload + " ", subscriberSide),
                "подмена тела обязана ломать подпись");
    }

    @Test
    void webhookEndpointsRejectRequestsWithoutAdminKey() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = rest.exchange("/v1/admin/webhooks", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        JsonNode envelope = objectMapper.readTree(response.getBody());
        assertEquals("admin.unauthorized", envelope.path("error").path("code").asText());
    }

    @Test
    void unknownEventTypeIsRejectedWithDomainCode() throws Exception {
        Map<String, Object> body = Map.of(
                "code", "bad-types", "name", "Портал", "targetUrl", UNREACHABLE,
                "eventTypes", List.of("volcano_erupted"), "active", true, "rateLimitPerMinute", 60);

        ResponseEntity<String> response = rest.exchange("/v1/admin/webhooks", HttpMethod.POST,
                new HttpEntity<>(body, adminHeaders()), String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("webhook.event_type_invalid",
                objectMapper.readTree(response.getBody()).path("error").path("code").asText());
    }

    // -------------------------------------------------------------- U-INT-04

    @Test
    void telemetryPositionIsAcceptedWithMachineKeyAndReadableWithout() throws Exception {
        Map<String, Object> report = Map.of(
                "trainCode", "TR-IT-01",
                "lineCode", "L1",
                "stationCode", "ST-L1-03",
                "coordinates", List.of(68.7860, 38.5737),
                "heading", 45,
                "speedKmh", 42.5,
                "delaySeconds", 60,
                "occupancy", "medium",
                "reportedAt", "2026-07-17T10:00:00Z");

        ResponseEntity<String> accepted = rest.exchange("/v1/telemetry/positions", HttpMethod.POST,
                new HttpEntity<>(report, machineHeaders()), String.class);

        assertEquals(HttpStatus.CREATED, accepted.getStatusCode());
        JsonNode position = objectMapper.readTree(accepted.getBody());
        assertEquals("TR-IT-01", position.path("trainCode").asText());
        assertEquals(68.7860, position.path("coordinates").get(0).asDouble(), 0.0001);
        assertEquals(38.5737, position.path("coordinates").get(1).asDouble(), 0.0001);

        ResponseEntity<String> read = rest.getForEntity("/v1/telemetry/positions?lineCode=L1",
                String.class);

        assertEquals(HttpStatus.OK, read.getStatusCode(), "чтение положения поездов — публичное");
        JsonNode positions = objectMapper.readTree(read.getBody());
        boolean found = false;
        for (JsonNode item : positions) {
            if ("TR-IT-01".equals(item.path("trainCode").asText())) {
                found = true;
                assertEquals(60, item.path("delaySeconds").asInt());
                assertEquals("medium", item.path("occupancy").asText());
            }
        }
        assertTrue(found, "принятый замер обязан попасть в срез позиций линии");
    }

    @Test
    void telemetryPublishWithoutMachineKeyIsRejected() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> report = Map.of("trainCode", "TR-NOKEY", "lineCode", "L1",
                "coordinates", List.of(68.78, 38.57), "reportedAt", "2026-07-17T10:00:00Z");

        ResponseEntity<String> response = rest.exchange("/v1/telemetry/positions", HttpMethod.POST,
                new HttpEntity<>(report, headers), String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("admin.unauthorized",
                objectMapper.readTree(response.getBody()).path("error").path("code").asText());
    }

    @Test
    void telemetryOnlyKeepsTheFreshestReportPerTrain() throws Exception {
        postPosition("TR-IT-02", "2026-07-17T09:00:00Z", 68.7800, 38.5700);
        postPosition("TR-IT-02", "2026-07-17T09:05:00Z", 68.7900, 38.5800);

        ResponseEntity<String> read = rest.getForEntity("/v1/telemetry/positions?lineCode=L1",
                String.class);

        JsonNode positions = objectMapper.readTree(read.getBody());
        int rows = 0;
        for (JsonNode item : positions) {
            if ("TR-IT-02".equals(item.path("trainCode").asText())) {
                rows++;
                assertEquals("2026-07-17T09:05:00Z", item.path("reportedAt").asText(),
                        "срез обязан показывать последний ЗАМЕР");
            }
        }
        assertEquals(1, rows, "по одному поезду — ровно одна строка в срезе");
    }

    @Test
    void telemetryRejectsCoordinatesOutsideEarth() throws Exception {
        Map<String, Object> report = Map.of("trainCode", "TR-BAD", "lineCode", "L1",
                "coordinates", List.of(999.0, 38.57), "reportedAt", "2026-07-17T10:00:00Z");

        ResponseEntity<String> response = rest.exchange("/v1/telemetry/positions", HttpMethod.POST,
                new HttpEntity<>(report, machineHeaders()), String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("telemetry.coordinates_invalid",
                objectMapper.readTree(response.getBody()).path("error").path("code").asText());
    }

    @Test
    void telemetryForUnknownLineIsRejectedRatherThanSilentlyStored() throws Exception {
        Map<String, Object> report = Map.of("trainCode", "TR-GHOST", "lineCode", "L-NOPE",
                "coordinates", List.of(68.78, 38.57), "reportedAt", "2026-07-17T10:00:00Z");

        ResponseEntity<String> response = rest.exchange("/v1/telemetry/positions", HttpMethod.POST,
                new HttpEntity<>(report, machineHeaders()), String.class);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("line.not_found",
                objectMapper.readTree(response.getBody()).path("error").path("code").asText());
    }

    // --------------------------------------------------------------- helpers

    private OutboxEvent publish(WebhookEventType type, String aggregateType, String aggregateCode) {
        return new TransactionTemplate(transactionManager).execute(status ->
                outboxService.publish(type, aggregateType, aggregateCode,
                        Map.of("code", aggregateCode)));
    }

    private void postPosition(String trainCode, String reportedAt, double lon, double lat) {
        Map<String, Object> report = Map.of("trainCode", trainCode, "lineCode", "L1",
                "coordinates", List.of(lon, lat), "reportedAt", reportedAt);
        ResponseEntity<String> response = rest.exchange("/v1/telemetry/positions", HttpMethod.POST,
                new HttpEntity<>(report, machineHeaders()), String.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    private List<String> deliveryQueueCodes(String status) {
        String path = status == null
                ? "/v1/admin/webhooks/deliveries"
                : "/v1/admin/webhooks/deliveries?status=" + status;
        ResponseEntity<String> response = rest.exchange(path, HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        try {
            JsonNode queue = objectMapper.readTree(response.getBody()).path("items");
            return java.util.stream.StreamSupport.stream(queue.spliterator(), false)
                    .map(item -> item.path("code").asText())
                    .toList();
        } catch (Exception exception) {
            throw new IllegalStateException("Не удалось разобрать очередь доставок", exception);
        }
    }

    private static WebhookSubscription subscription(String code, boolean active,
                                                    List<String> eventTypes) {
        return new WebhookSubscription(UUID.randomUUID(), code, "Подписчик " + code, UNREACHABLE,
                WebhookSignature.hashSecret("secret-" + code), eventTypes, active, 1000, "it-setup");
    }

    private static HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Admin-Key", ADMIN_KEY);
        // Первичный суперадмин, создаваемый AdminUserBootstrap на старте.
        headers.set("X-Admin-Actor", "admin");
        return headers;
    }

    /** Машинный контур GPS-платформы: тот же общий секрет, что у admin (временно). */
    private static HttpHeaders machineHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Admin-Key", ADMIN_KEY);
        return headers;
    }
}
