package tj.metro.dushanbe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.stream.StreamSupport;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Интеграционный тест конвейера импорта сети поверх реального PostGIS (Testcontainers):
 * применяются миграции Flyway (включая V010 — схема import_job/import_error) и
 * проверяется admin-контур /v1/admin/imports: dev-авторизация (401 без ключа),
 * применение валидного мини-GeoJSON (сущности видны в публичной выдаче) и построчная
 * фиксация ошибок для битого входа.
 *
 * <p>Пути — без префикса /api: TestRestTemplate сам добавляет context-path.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ImportApiIntegrationTest {

    private static final String ADMIN_KEY = "test-admin-key";

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
    }

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String VALID_GEOJSON = """
            {"type":"FeatureCollection","features":[
              {"type":"Feature","properties":{"feature_type":"line","code":"IMP-L1",
                "name":{"tg":"Хати импорт","ru":"Импорт-линия","en":"Import Line"},
                "color_hex":"#0055AA","status":"planned","sort_order":42},
                "geometry":{"type":"LineString","coordinates":[[68.80,38.52],[68.79,38.56]]}},
              {"type":"Feature","properties":{"feature_type":"station","code":"IMP-ST-1",
                "name":{"tg":"Ист1","ru":"Импорт1","en":"Import1"},"status":"planned",
                "lines":["IMP-L1"],"is_transfer":false,"accessibility":["elevator"]},
                "geometry":{"type":"Point","coordinates":[68.80,38.52]}},
              {"type":"Feature","properties":{"feature_type":"station","code":"IMP-ST-2",
                "name":{"tg":"Ист2","ru":"Импорт2","en":"Import2"},"status":"planned",
                "lines":["IMP-L1"]},
                "geometry":{"type":"Point","coordinates":[68.79,38.56]}}]}""";

    @Test
    void importWithoutKeyReturnsEnvelope401() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = rest.exchange("/v1/admin/imports", HttpMethod.POST,
                new HttpEntity<>(VALID_GEOJSON, headers), String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        JsonNode envelope = objectMapper.readTree(response.getBody());
        assertEquals("admin.unauthorized", envelope.path("error").path("code").asText());
    }

    @Test
    void validImportAppliesEntitiesAndTheyAreVisiblePublicly() throws Exception {
        ResponseEntity<String> posted = rest.exchange("/v1/admin/imports", HttpMethod.POST,
                new HttpEntity<>(VALID_GEOJSON, adminHeaders()), String.class);

        assertEquals(HttpStatus.OK, posted.getStatusCode());
        JsonNode summary = objectMapper.readTree(posted.getBody());
        assertEquals("success", summary.path("status").asText());
        assertEquals(3, summary.path("featureCount").asInt());
        assertEquals(3, summary.path("createdCount").asInt());
        assertEquals(0, summary.path("failedCount").asInt());
        assertEquals("network_geojson", summary.path("type").asText());
        assertEquals("geojson", summary.path("format").asText());
        String jobId = summary.path("id").asText();

        // GET /{id} — та же сводка
        ResponseEntity<String> job = rest.exchange("/v1/admin/imports/" + jobId, HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), String.class);
        assertEquals(HttpStatus.OK, job.getStatusCode());
        assertEquals("success", objectMapper.readTree(job.getBody()).path("status").asText());

        // GET /{id}/errors — пусто для чистого импорта
        ResponseEntity<String> errors = rest.exchange("/v1/admin/imports/" + jobId + "/errors", HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), String.class);
        assertEquals(HttpStatus.OK, errors.getStatusCode());
        assertTrue(objectMapper.readTree(errors.getBody()).isEmpty(), "у чистого импорта не должно быть ошибок");

        // GET список — джоб виден в ленте
        ResponseEntity<String> list = rest.exchange("/v1/admin/imports?size=100", HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), String.class);
        assertEquals(HttpStatus.OK, list.getStatusCode());
        JsonNode items = objectMapper.readTree(list.getBody()).path("items");
        assertTrue(StreamSupport.stream(items.spliterator(), false)
                .anyMatch(i -> jobId.equals(i.path("id").asText())), "джоб должен быть в ленте");

        // импортированная линия видна публично
        ResponseEntity<String> lines = rest.getForEntity("/v1/lines", String.class);
        assertTrue(codes(lines.getBody()).contains("IMP-L1"), "импортированная линия видна в GET /v1/lines");

        // станции видны и привязаны к линии (связь станция-линия создана импортом)
        ResponseEntity<String> stations = rest.getForEntity("/v1/stations?lineCode=IMP-L1", String.class);
        assertEquals(HttpStatus.OK, stations.getStatusCode());
        JsonNode stationArr = objectMapper.readTree(stations.getBody());
        assertEquals(2, stationArr.size(), "к IMP-L1 привязаны две импортированные станции");
        assertTrue(codes(stations.getBody()).containsAll(java.util.List.of("IMP-ST-1", "IMP-ST-2")));
    }

    @Test
    void reimportIsIdempotentByCode() throws Exception {
        rest.exchange("/v1/admin/imports", HttpMethod.POST,
                new HttpEntity<>(VALID_GEOJSON, adminHeaders()), String.class);
        // повторный импорт того же тела — те же коды апсертятся (updated), новые сущности не плодятся
        ResponseEntity<String> second = rest.exchange("/v1/admin/imports", HttpMethod.POST,
                new HttpEntity<>(VALID_GEOJSON, adminHeaders()), String.class);
        assertEquals(HttpStatus.OK, second.getStatusCode());
        JsonNode summary = objectMapper.readTree(second.getBody());
        assertEquals("success", summary.path("status").asText());
        assertEquals(3, summary.path("updatedCount").asInt(), "повторный импорт обновляет, а не создаёт");
        assertEquals(0, summary.path("createdCount").asInt());

        // станций у линии по-прежнему две (перепривязка идемпотентна)
        ResponseEntity<String> stations = rest.getForEntity("/v1/stations?lineCode=IMP-L1", String.class);
        assertEquals(2, objectMapper.readTree(stations.getBody()).size());
    }

    @Test
    void brokenFeatureIsRecordedAsError() throws Exception {
        String broken = """
                {"type":"FeatureCollection","features":[
                  {"type":"Feature","properties":{"feature_type":"station","code":"IMP-BAD",
                    "name":{"tg":"Плох","ru":"Плохо"},"status":"nonsense"},
                    "geometry":{"type":"LineString","coordinates":[]}}]}""";

        ResponseEntity<String> posted = rest.exchange("/v1/admin/imports", HttpMethod.POST,
                new HttpEntity<>(broken, adminHeaders()), String.class);
        assertEquals(HttpStatus.OK, posted.getStatusCode());
        JsonNode summary = objectMapper.readTree(posted.getBody());
        assertEquals("failed", summary.path("status").asText());
        assertEquals(1, summary.path("failedCount").asInt());
        assertEquals(0, summary.path("createdCount").asInt());
        String jobId = summary.path("id").asText();

        ResponseEntity<String> errors = rest.exchange("/v1/admin/imports/" + jobId + "/errors", HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), String.class);
        JsonNode errorArr = objectMapper.readTree(errors.getBody());
        assertTrue(errorArr.size() >= 1, "должна быть хотя бы одна построчная ошибка");
        assertEquals("IMP-BAD", errorArr.get(0).path("featureRef").asText());
        assertEquals("error", errorArr.get(0).path("severity").asText());

        // битая станция не попала в публичную выдачу
        ResponseEntity<String> stations = rest.getForEntity("/v1/stations", String.class);
        assertTrue(codes(stations.getBody()).stream().noneMatch("IMP-BAD"::equals),
                "битая станция не должна быть создана");
    }

    /**
     * GTFS-фид (INT-04/MAP-08) сквозь весь конвейер: ZIP → станции/линии в БД, порядок
     * станций из stop_times, и — главное — i18n-компромисс: недостающие переводы видны
     * оператору как предупреждения, а не теряются молча.
     */
    @Test
    void gtfsImportAppliesNetworkAndReportsMissingTranslationsAsWarnings() throws Exception {
        HttpHeaders headers = adminHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.set("X-Import-Source", "feed.zip");

        ResponseEntity<String> posted = rest.exchange("/v1/admin/imports/gtfs?lang=ru&status=planned",
                HttpMethod.POST, new HttpEntity<>(gtfsFeed(), headers), String.class);

        assertEquals(HttpStatus.OK, posted.getStatusCode());
        JsonNode summary = objectMapper.readTree(posted.getBody());
        assertEquals("gtfs", summary.path("format").asText());
        assertEquals("network_gtfs", summary.path("type").asText());
        assertEquals("success", summary.path("status").asText());
        assertEquals(0, summary.path("failedCount").asInt());
        String jobId = summary.path("id").asText();

        // Линия и станции применены, названия — на языке фида
        ResponseEntity<String> lines = rest.getForEntity("/v1/lines", String.class);
        assertTrue(codes(lines.getBody()).contains("GT-L1"), "линия из GTFS видна публично");

        ResponseEntity<String> stations = rest.getForEntity("/v1/stations?lineCode=GT-L1", String.class);
        assertEquals(2, objectMapper.readTree(stations.getBody()).size(), "к GT-L1 привязаны две станции");

        // i18n заполнен полностью (иначе сущность не прошла бы валидацию), значение — язык фида
        JsonNode station = objectMapper.readTree(stations.getBody()).get(0);
        for (String language : java.util.List.of("tg", "ru", "en")) {
            assertTrue(station.path("name").path(language).asText().length() > 0,
                    "язык " + language + " обязан быть заполнен");
        }

        // ...а долг по переводу зафиксирован предупреждениями в отчёте импорта
        ResponseEntity<String> errors = rest.exchange("/v1/admin/imports/" + jobId + "/errors", HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), String.class);
        JsonNode errorArr = objectMapper.readTree(errors.getBody());
        assertTrue(StreamSupport.stream(errorArr.spliterator(), false)
                        .allMatch(e -> "warning".equals(e.path("severity").asText())),
                "перевод-заглушка — это warning, а не ошибка: " + errorArr);
        assertTrue(StreamSupport.stream(errorArr.spliterator(), false)
                        .anyMatch(e -> e.path("message").asText().contains("требуется перевод")),
                "оператор должен видеть, что требует перевода: " + errorArr);
    }

    @Test
    void csvImportAppliesNetworkAndReportsBrokenRowWithLineNumber() throws Exception {
        HttpHeaders headers = adminHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        String csv = """
                entity,code,name_tg,name_ru,name_en,status,color_hex,sort_order,lon,lat,lines,is_transfer,accessibility
                line,CSV-L1,Хати CSV,CSV-линия,CSV Line,planned,#00AA55,7,,,,,
                station,CSV-ST-1,Ист CSV1,CSV-станция 1,CSV Station 1,planned,,,68.81,38.53,CSV-L1,false,
                station,CSV-BAD,Ист CSV2
                """;

        ResponseEntity<String> posted = rest.exchange("/v1/admin/imports/csv", HttpMethod.POST,
                new HttpEntity<>(csv, headers), String.class);

        assertEquals(HttpStatus.OK, posted.getStatusCode());
        JsonNode summary = objectMapper.readTree(posted.getBody());
        assertEquals("csv", summary.path("format").asText());
        assertEquals("network_csv", summary.path("type").asText());
        // часть применена, часть отклонена
        assertEquals("partial", summary.path("status").asText());
        assertEquals(2, summary.path("createdCount").asInt());
        assertEquals(1, summary.path("failedCount").asInt());
        String jobId = summary.path("id").asText();

        ResponseEntity<String> errors = rest.exchange("/v1/admin/imports/" + jobId + "/errors", HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), String.class);
        JsonNode errorArr = objectMapper.readTree(errors.getBody());
        assertTrue(StreamSupport.stream(errorArr.spliterator(), false)
                        .anyMatch(e -> e.path("featureRef").asText().contains("строка 4")),
                "битая строка должна быть названа по номеру: " + errorArr);

        ResponseEntity<String> lines = rest.getForEntity("/v1/lines", String.class);
        assertTrue(codes(lines.getBody()).contains("CSV-L1"), "линия из CSV видна публично");
    }

    /**
     * GTFS Fares v2 сквозь конвейер тарифов: ZIP → fare_product в БД. Проверяется, что это
     * ДРУГОЙ вид джоба при том же формате, что импорт идёт через AdminFareService (продукт
     * виден публично после активации, кэш {@code fares} погашен) и что решения по
     * недостающим в GTFS полям видны оператору предупреждениями, а не молчат.
     */
    @Test
    void gtfsFaresImportAppliesProductsAndReportsWhatWasFilledIn() throws Exception {
        HttpHeaders headers = adminHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.set("X-Import-Source", "fares.zip");

        ResponseEntity<String> posted = rest.exchange("/v1/admin/imports/gtfs-fares?lang=ru&active=true",
                HttpMethod.POST, new HttpEntity<>(faresFeed(), headers), String.class);

        assertEquals(HttpStatus.OK, posted.getStatusCode());
        JsonNode summary = objectMapper.readTree(posted.getBody());
        assertEquals("fare_gtfs", summary.path("type").asText(), "тарифы — не сеть");
        assertEquals("gtfs", summary.path("format").asText(), "контейнер тот же");
        assertEquals("success", summary.path("status").asText());
        assertEquals(2, summary.path("createdCount").asInt());
        assertEquals(0, summary.path("failedCount").asInt());
        String jobId = summary.path("id").asText();

        // Продукты применены и видны публично (active=true задан явно), кэш fares погашен
        ResponseEntity<String> fares = rest.getForEntity("/v1/fares", String.class);
        assertEquals(HttpStatus.OK, fares.getStatusCode());
        assertTrue(codes(fares.getBody()).contains("GTFS-SINGLE"), "тариф из GTFS виден в GET /v1/fares");

        JsonNode product = StreamSupport.stream(objectMapper.readTree(fares.getBody()).spliterator(), false)
                .filter(f -> "GTFS-SINGLE".equals(f.path("code").asText())).findFirst().orElseThrow();
        assertEquals("3.00", product.path("amount").asText());
        assertEquals("TJS", product.path("currency").asText());
        assertEquals("adult", product.path("riderCategory").asText());
        // Срок действия GTFS не несёт — продукт создан без него, а не с выдуманным числом
        assertTrue(product.path("validityMinutes").isNull(),
                "validity_minutes неоткуда взять: " + product.path("validityMinutes"));
        for (String language : java.util.List.of("tg", "ru", "en")) {
            assertTrue(product.path("name").path(language).asText().length() > 0,
                    "язык " + language + " обязан быть заполнен");
        }

        // ...а весь долг зафиксирован предупреждениями — это и есть список «что проверить руками»
        ResponseEntity<String> errors = rest.exchange("/v1/admin/imports/" + jobId + "/errors",
                HttpMethod.GET, new HttpEntity<>(adminHeaders()), String.class);
        JsonNode errorArr = objectMapper.readTree(errors.getBody());
        assertTrue(StreamSupport.stream(errorArr.spliterator(), false)
                        .allMatch(e -> "warning".equals(e.path("severity").asText())),
                "у чистого фида все записи отчёта — предупреждения: " + errorArr);
        assertTrue(StreamSupport.stream(errorArr.spliterator(), false)
                        .anyMatch(e -> e.path("message").asText().contains("validity_minutes")),
                "оператор должен видеть, чему проставить срок: " + errorArr);
        assertTrue(StreamSupport.stream(errorArr.spliterator(), false)
                        .anyMatch(e -> e.path("message").asText().contains("требуется перевод")),
                "оператор должен видеть, что перевести: " + errorArr);
    }

    @Test
    void gtfsFaresReimportIsIdempotentByCode() throws Exception {
        HttpHeaders headers = adminHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);

        rest.exchange("/v1/admin/imports/gtfs-fares?lang=ru&active=true", HttpMethod.POST,
                new HttpEntity<>(faresFeed(), headers), String.class);
        ResponseEntity<String> second = rest.exchange("/v1/admin/imports/gtfs-fares?lang=ru&active=true",
                HttpMethod.POST, new HttpEntity<>(faresFeed(), headers), String.class);

        JsonNode summary = objectMapper.readTree(second.getBody());
        assertEquals("success", summary.path("status").asText());
        assertEquals(2, summary.path("updatedCount").asInt(), "повторный импорт обновляет, а не создаёт");
        assertEquals(0, summary.path("createdCount").asInt());

        // дублей в справочнике не появилось
        ResponseEntity<String> fares = rest.getForEntity("/v1/fares", String.class);
        assertEquals(1, codes(fares.getBody()).stream().filter("GTFS-SINGLE"::equals).count());
    }

    /** Неизвестная категория пассажира — ошибка строки: льгота не тому пассажиру недопустима. */
    @Test
    void gtfsFaresUnknownRiderCategoryIsReportedWithLineNumberAndNotApplied() throws Exception {
        HttpHeaders headers = adminHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        java.util.Map<String, String> files = faresFiles();
        files.put("rider_categories.txt", """
                rider_category_id,rider_category_name,is_default_fare_category
                adult,Взрослый,1
                RC_KID,Дети,0
                """);
        files.put("fare_products.txt", """
                fare_product_id,fare_product_name,rider_category_id,amount,currency
                GTFS-SINGLE,Разовая поездка,adult,3.00,TJS
                GTFS-KID,Детский билет,RC_KID,1.00,TJS
                """);

        ResponseEntity<String> posted = rest.exchange("/v1/admin/imports/gtfs-fares?lang=ru&active=true",
                HttpMethod.POST, new HttpEntity<>(zip(files), headers), String.class);

        JsonNode summary = objectMapper.readTree(posted.getBody());
        assertEquals("partial", summary.path("status").asText(), "хороший продукт применён, битый — нет");
        assertEquals(1, summary.path("failedCount").asInt());
        String jobId = summary.path("id").asText();

        ResponseEntity<String> errors = rest.exchange("/v1/admin/imports/" + jobId + "/errors",
                HttpMethod.GET, new HttpEntity<>(adminHeaders()), String.class);
        JsonNode errorArr = objectMapper.readTree(errors.getBody());
        assertTrue(StreamSupport.stream(errorArr.spliterator(), false)
                        .anyMatch(e -> "error".equals(e.path("severity").asText())
                                && e.path("message").asText().contains("rider_categories.txt:строка 3")),
                "категория обязана быть названа по строке объявления: " + errorArr);

        // битый продукт в справочник не попал
        ResponseEntity<String> fares = rest.getForEntity("/v1/fares", String.class);
        assertTrue(codes(fares.getBody()).stream().noneMatch("GTFS-KID"::equals));
    }

    /** Фид сети, поданный в тарифный эндпоинт, отвергается целиком с подсказкой. */
    @Test
    void networkFeedPostedToFaresEndpointIsRejected() throws Exception {
        HttpHeaders headers = adminHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);

        ResponseEntity<String> posted = rest.exchange("/v1/admin/imports/gtfs-fares", HttpMethod.POST,
                new HttpEntity<>(gtfsFeed(), headers), String.class);

        JsonNode summary = objectMapper.readTree(posted.getBody());
        assertEquals("failed", summary.path("status").asText());
        ResponseEntity<String> errors = rest.exchange(
                "/v1/admin/imports/" + summary.path("id").asText() + "/errors",
                HttpMethod.GET, new HttpEntity<>(adminHeaders()), String.class);
        assertTrue(objectMapper.readTree(errors.getBody()).get(0).path("message").asText()
                .contains("fare_products.txt"));
    }

    /** Мини-фид GTFS Fares v2, собранный программно. */
    private static java.util.Map<String, String> faresFiles() {
        java.util.Map<String, String> files = new java.util.LinkedHashMap<>();
        files.put("agency.txt", """
                agency_id,agency_name,agency_url,agency_timezone,agency_lang
                MD,Метро Душанбе,https://metro.tj,Asia/Dushanbe,ru
                """);
        files.put("rider_categories.txt", """
                rider_category_id,rider_category_name,is_default_fare_category
                adult,Взрослый,1
                student,Студент,0
                """);
        files.put("fare_media.txt", """
                fare_media_id,fare_media_name,fare_media_type
                CARD,Транспортная карта,2
                """);
        files.put("fare_products.txt", """
                fare_product_id,fare_product_name,rider_category_id,amount,currency
                GTFS-SINGLE,Разовая поездка,adult,3.00,TJS
                GTFS-STUDENT,Студенческий проездной,student,25.50,TJS
                """);
        return files;
    }

    private static byte[] faresFeed() throws Exception {
        return zip(faresFiles());
    }

    /** Мини-GTFS, собранный программно: бинарных фикстур в репозитории быть не должно. */
    private static byte[] gtfsFeed() throws Exception {
        java.util.Map<String, String> files = new java.util.LinkedHashMap<>();
        files.put("agency.txt", """
                agency_id,agency_name,agency_url,agency_timezone,agency_lang
                MD,Метро Душанбе,https://metro.tj,Asia/Dushanbe,ru
                """);
        files.put("routes.txt", """
                route_id,route_short_name,route_long_name,route_type,route_color,route_sort_order
                GT-L1,1,ГТФС линия,1,00AAFF,5
                """);
        files.put("stops.txt", """
                stop_id,stop_name,stop_lat,stop_lon,location_type,wheelchair_boarding
                GT-ST-1,ГТФС станция 1,38.54,68.82,1,1
                GT-ST-2,ГТФС станция 2,38.55,68.83,1,0
                """);
        files.put("trips.txt", """
                route_id,service_id,trip_id,shape_id
                GT-L1,WD,GT-T1,GT-SH1
                """);
        files.put("stop_times.txt", """
                trip_id,arrival_time,departure_time,stop_id,stop_sequence
                GT-T1,08:00:00,08:00:00,GT-ST-1,1
                GT-T1,08:04:00,08:04:00,GT-ST-2,2
                """);
        files.put("shapes.txt", """
                shape_id,shape_pt_lat,shape_pt_lon,shape_pt_sequence
                GT-SH1,38.54,68.82,1
                GT-SH1,38.55,68.83,2
                """);
        return zip(files);
    }

    private static byte[] zip(java.util.Map<String, String> files) throws Exception {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(buffer,
                java.nio.charset.StandardCharsets.UTF_8)) {
            for (java.util.Map.Entry<String, String> file : files.entrySet()) {
                zip.putNextEntry(new java.util.zip.ZipEntry(file.getKey()));
                zip.write(file.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return buffer.toByteArray();
    }

    private java.util.List<String> codes(String body) throws Exception {
        JsonNode arr = objectMapper.readTree(body);
        return StreamSupport.stream(arr.spliterator(), false)
                .map(n -> n.path("code").asText())
                .toList();
    }

    private static HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Admin-Key", ADMIN_KEY);
        // Актор обязан существовать в admin_user и быть активным (ADM-01), иначе
        // AdminKeyAuthFilter вернёт 401 auth.session_revoked. Единственный
        // гарантированно существующий пользователь — бутстрап-суперадмин
        // (AdminUserBootstrap из app.admin.bootstrap.username, по умолчанию "admin").
        headers.set("X-Admin-Actor", "admin");
        return headers;
    }
}
