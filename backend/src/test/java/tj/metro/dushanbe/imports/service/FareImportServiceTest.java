package tj.metro.dushanbe.imports.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tj.metro.dushanbe.admin.service.AdminFareService;
import tj.metro.dushanbe.admin.web.dto.FareCreateRequest;
import tj.metro.dushanbe.admin.web.dto.FareUpdateRequest;
import tj.metro.dushanbe.audit.service.AuditService;
import tj.metro.dushanbe.common.error.BadRequestException;
import tj.metro.dushanbe.fare.domain.FareProduct;
import tj.metro.dushanbe.fare.repository.FareProductRepository;
import tj.metro.dushanbe.imports.domain.ImportError;
import tj.metro.dushanbe.imports.domain.ImportFormat;
import tj.metro.dushanbe.imports.domain.ImportJob;
import tj.metro.dushanbe.imports.repository.ImportErrorRepository;
import tj.metro.dushanbe.imports.repository.ImportJobRepository;
import tj.metro.dushanbe.imports.service.parser.FaresGtfsImportParser;
import tj.metro.dushanbe.imports.service.parser.FaresGtfsImportParser.FareImportOptions;

/**
 * Юнит-тесты импорта тарифов: реальный парсер, остальное — mock. Проверяют, что продукты
 * пишутся ТОЛЬКО через {@link AdminFareService} (аудит и инвалидация кэша {@code fares} —
 * его забота), что решения по недостающим в GTFS полям (validity_minutes, is_active)
 * соблюдаются, и что всё достроенное попадает в отчёт предупреждениями (IMP-03).
 */
class FareImportServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-17T10:00:00Z"), ZoneOffset.UTC);
    private static final String ACTOR = "it-admin";

    private final ImportJobRepository jobRepository = mock(ImportJobRepository.class);
    private final ImportErrorRepository errorRepository = mock(ImportErrorRepository.class);
    private final FareProductRepository fareRepository = mock(FareProductRepository.class);
    private final AdminFareService adminFareService = mock(AdminFareService.class);
    private final AuditService auditService = mock(AuditService.class);

    private final FareImportService service = new FareImportService(jobRepository, errorRepository,
            new FaresGtfsImportParser(), fareRepository, adminFareService, auditService, CLOCK);

    private void jobsAreSavedAsIs() {
        when(jobRepository.save(any(ImportJob.class))).thenAnswer(call -> call.getArgument(0));
    }

    // ---- Фикстуры ---------------------------------------------------------

    private static Map<String, String> validFeed() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("agency.txt", """
                agency_id,agency_name,agency_url,agency_timezone,agency_lang
                MD,Метро Душанбе,https://metro.tj,Asia/Dushanbe,ru
                """);
        files.put("rider_categories.txt", """
                rider_category_id,rider_category_name,is_default_fare_category
                adult,Взрослый,1
                """);
        files.put("fare_products.txt", """
                fare_product_id,fare_product_name,rider_category_id,amount,currency
                SINGLE,Разовая поездка,adult,3.00,TJS
                """);
        return files;
    }

    private static byte[] zip(Map<String, String> files) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, String> file : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(file.getKey()));
                zip.write(file.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return buffer.toByteArray();
    }

    private static FareProduct existing(String code, Integer validityMinutes, boolean active) {
        return new FareProduct(UUID.randomUUID(), code,
                Map.of("tg", "Кӯҳна", "ru", "Старое", "en", "Old"),
                Map.of("tg", "Кӯҳна", "ru", "Старое", "en", "Old"),
                new BigDecimal("2.00"), "TJS", "all", validityMinutes, active);
    }

    // ---- Валидный фид -----------------------------------------------------

    @Test
    void validFeedCreatesProductsThroughAdminServiceOnly() {
        jobsAreSavedAsIs();
        when(fareRepository.findByCode("SINGLE")).thenReturn(Optional.empty());

        ImportJob job = service.importFaresGtfs(zip(validFeed()), FareImportOptions.defaults(),
                null, "fares.zip", ACTOR);

        assertEquals(ImportJob.STATUS_SUCCESS, job.getStatus());
        assertEquals(ImportJob.TYPE_FARE_GTFS, job.getType(), "тарифы — отдельный вид джоба");
        assertEquals(ImportFormat.GTFS, job.getFormat(), "контейнер тот же — GTFS");
        assertEquals(1, job.getFeatureCount());
        assertEquals(1, job.getCreatedCount());
        assertEquals(0, job.getFailedCount());

        ArgumentCaptor<FareCreateRequest> created = ArgumentCaptor.forClass(FareCreateRequest.class);
        verify(adminFareService).create(created.capture(), eq(ACTOR));
        assertEquals("SINGLE", created.getValue().code());
        assertEquals(0, new BigDecimal("3.00").compareTo(created.getValue().amount()));
        assertEquals("TJS", created.getValue().currency());
        assertEquals("adult", created.getValue().riderCategory());
        assertEquals("Разовая поездка", created.getValue().name().get("ru"));
    }

    @Test
    void jobIsAudited() {
        jobsAreSavedAsIs();
        when(fareRepository.findByCode(anyString())).thenReturn(Optional.empty());

        ImportJob job = service.importFaresGtfs(zip(validFeed()), FareImportOptions.defaults(),
                null, "fares.zip", ACTOR);

        verify(auditService).record(eq(ACTOR), eq("fare.import"), eq("import_job"),
                eq(job.getId().toString()), isNull(), any());
    }

    @Test
    void sourceHashIsRecordedForIdempotencyTracking() {
        jobsAreSavedAsIs();
        when(fareRepository.findByCode(anyString())).thenReturn(Optional.empty());

        ImportJob job = service.importFaresGtfs(zip(validFeed()), FareImportOptions.defaults(),
                null, "fares.zip", ACTOR);

        assertEquals(64, job.getSourceHash().length(), "SHA-256 в hex — 64 символа");
    }

    // ---- validity_minutes: решение по недостающему полю --------------------

    @Test
    void newProductIsCreatedWithoutValidityBecauseGtfsCannotExpressIt() {
        jobsAreSavedAsIs();
        when(fareRepository.findByCode("SINGLE")).thenReturn(Optional.empty());

        service.importFaresGtfs(zip(validFeed()), FareImportOptions.defaults(), null, null, ACTOR);

        ArgumentCaptor<FareCreateRequest> created = ArgumentCaptor.forClass(FareCreateRequest.class);
        verify(adminFareService).create(created.capture(), eq(ACTOR));
        assertNull(created.getValue().validityMinutes(),
                "срок действия не выдумывается: колонка nullable, значение проставит оператор");
        // ...и это видно в отчёте
        assertTrue(warningsOf().stream().anyMatch(w -> w.contains("validity_minutes")),
                "предупреждение о сроке обязано быть в отчёте: " + warningsOf());
    }

    @Test
    void reimportKeepsValidityThatOperatorSetByHand() {
        jobsAreSavedAsIs();
        when(fareRepository.findByCode("SINGLE")).thenReturn(Optional.of(existing("SINGLE", 90, true)));

        ImportJob job = service.importFaresGtfs(zip(validFeed()), FareImportOptions.defaults(),
                null, null, ACTOR);

        assertEquals(1, job.getUpdatedCount(), "повторный импорт обновляет по коду, а не создаёт");
        assertEquals(0, job.getCreatedCount());
        ArgumentCaptor<FareUpdateRequest> updated = ArgumentCaptor.forClass(FareUpdateRequest.class);
        verify(adminFareService).update(eq("SINGLE"), updated.capture(), eq(ACTOR));
        assertEquals(90, updated.getValue().validityMinutes(),
                "импорт не вправе стереть срок, проставленный оператором");
        // цена при этом обновляется — ради этого импорт и запускают
        assertEquals(0, new BigDecimal("3.00").compareTo(updated.getValue().amount()));
        verify(adminFareService, never()).create(any(), anyString());
    }

    // ---- is_active: решение по недостающему полю --------------------------

    @Test
    void newProductIsInactiveByDefaultAndSaysSoInTheReport() {
        jobsAreSavedAsIs();
        when(fareRepository.findByCode("SINGLE")).thenReturn(Optional.empty());

        service.importFaresGtfs(zip(validFeed()), FareImportOptions.defaults(), null, null, ACTOR);

        ArgumentCaptor<FareCreateRequest> created = ArgumentCaptor.forClass(FareCreateRequest.class);
        verify(adminFareService).create(created.capture(), eq(ACTOR));
        assertEquals(Boolean.FALSE, created.getValue().active(),
                "чужая цена не должна попасть на публичный сайт без сверки");
        assertTrue(warningsOf().stream().anyMatch(w -> w.contains("НЕАКТИВНЫМ")), warningsOf().toString());
    }

    @Test
    void reimportKeepsPublicationFlagOfExistingProduct() {
        jobsAreSavedAsIs();
        when(fareRepository.findByCode("SINGLE")).thenReturn(Optional.of(existing("SINGLE", null, true)));

        service.importFaresGtfs(zip(validFeed()), FareImportOptions.defaults(), null, null, ACTOR);

        ArgumentCaptor<FareUpdateRequest> updated = ArgumentCaptor.forClass(FareUpdateRequest.class);
        verify(adminFareService).update(eq("SINGLE"), updated.capture(), eq(ACTOR));
        assertEquals(Boolean.TRUE, updated.getValue().active(), "импорт не снимает продукт с публикации");
    }

    @Test
    void explicitActiveParameterOverridesTheDefault() {
        jobsAreSavedAsIs();
        when(fareRepository.findByCode("SINGLE")).thenReturn(Optional.empty());

        service.importFaresGtfs(zip(validFeed()), FareImportOptions.defaults(), true, null, ACTOR);

        ArgumentCaptor<FareCreateRequest> created = ArgumentCaptor.forClass(FareCreateRequest.class);
        verify(adminFareService).create(created.capture(), eq(ACTOR));
        assertEquals(Boolean.TRUE, created.getValue().active());
        assertTrue(warningsOf().stream().noneMatch(w -> w.contains("НЕАКТИВНЫМ")),
                "решение принято явно — предупреждать не о чем");
    }

    // ---- Отчёт ------------------------------------------------------------

    @Test
    void translationStubsAreReportedAsWarningsNotErrors() {
        jobsAreSavedAsIs();
        when(fareRepository.findByCode(anyString())).thenReturn(Optional.empty());

        ImportJob job = service.importFaresGtfs(zip(validFeed()), FareImportOptions.defaults(),
                null, null, ACTOR);

        assertEquals(ImportJob.STATUS_SUCCESS, job.getStatus(), "заглушки перевода — не сбой импорта");
        verify(errorRepository, never()).save(argThat(e ->
                ImportError.SEVERITY_ERROR.equals(e.getSeverity())));
        assertTrue(warningsOf().stream().anyMatch(w -> w.contains("требуется перевод")), warningsOf().toString());
    }

    @Test
    void unknownRiderCategoryFailsThatProductAndIsNotWritten() {
        jobsAreSavedAsIs();
        Map<String, String> files = validFeed();
        files.put("rider_categories.txt", """
                rider_category_id,rider_category_name,is_default_fare_category
                RC_KID,Дети,0
                """);
        files.put("fare_products.txt", """
                fare_product_id,fare_product_name,rider_category_id,amount,currency
                KID,Детский билет,RC_KID,1.00,TJS
                """);

        ImportJob job = service.importFaresGtfs(zip(files), FareImportOptions.defaults(), null, null, ACTOR);

        assertEquals(ImportJob.STATUS_FAILED, job.getStatus(), "применить было нечего");
        assertEquals(1, job.getFailedCount());
        verify(adminFareService, never()).create(any(), anyString());
        ArgumentCaptor<ImportError> recorded = ArgumentCaptor.forClass(ImportError.class);
        verify(errorRepository).save(recorded.capture());
        assertEquals(ImportError.SEVERITY_ERROR, recorded.getValue().getSeverity());
        assertTrue(recorded.getValue().getMessage().contains("rider_categories.txt:строка 2"),
                recorded.getValue().getMessage());
    }

    @Test
    void rejectedFeedFailsTheJobWithTopLevelError() {
        jobsAreSavedAsIs();

        ImportJob job = service.importFaresGtfs("не архив".getBytes(StandardCharsets.UTF_8),
                FareImportOptions.defaults(), null, null, ACTOR);

        assertEquals(ImportJob.STATUS_FAILED, job.getStatus());
        ArgumentCaptor<ImportError> recorded = ArgumentCaptor.forClass(ImportError.class);
        verify(errorRepository).save(recorded.capture());
        assertEquals(ImportError.TOP_LEVEL_REF, recorded.getValue().getFeatureRef());
        verify(adminFareService, never()).create(any(), anyString());
    }

    @Test
    void rejectionByAdminServiceBecomesRowErrorAndDoesNotKillTheJob() {
        jobsAreSavedAsIs();
        Map<String, String> files = validFeed();
        files.put("fare_products.txt", """
                fare_product_id,fare_product_name,rider_category_id,amount,currency
                SINGLE,Разовая поездка,adult,3.00,TJS
                DAY-PASS,Дневной проездной,adult,10.00,TJS
                """);
        when(fareRepository.findByCode(anyString())).thenReturn(Optional.empty());
        when(adminFareService.create(argThat(r -> "SINGLE".equals(r.code())), anyString()))
                .thenThrow(new BadRequestException("fare.code_exists", "Код занят", Map.of()));

        ImportJob job = service.importFaresGtfs(zip(files), FareImportOptions.defaults(), null, null, ACTOR);

        assertEquals(ImportJob.STATUS_PARTIAL, job.getStatus(), "второй продукт обязан примениться");
        assertEquals(1, job.getFailedCount());
        assertEquals(1, job.getCreatedCount());
    }

    /** Все предупреждения, попавшие в отчёт джоба. */
    private java.util.List<String> warningsOf() {
        ArgumentCaptor<ImportError> recorded = ArgumentCaptor.forClass(ImportError.class);
        verify(errorRepository, org.mockito.Mockito.atLeastOnce()).save(recorded.capture());
        return recorded.getAllValues().stream()
                .filter(e -> ImportError.SEVERITY_WARNING.equals(e.getSeverity()))
                .map(ImportError::getMessage)
                .toList();
    }
}
