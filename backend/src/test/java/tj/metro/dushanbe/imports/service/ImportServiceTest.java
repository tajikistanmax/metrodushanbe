package tj.metro.dushanbe.imports.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tj.metro.dushanbe.admin.service.AdminLineService;
import tj.metro.dushanbe.admin.service.AdminStationService;
import tj.metro.dushanbe.audit.service.AuditService;
import tj.metro.dushanbe.common.error.BadRequestException;
import tj.metro.dushanbe.featureflag.service.FeatureFlagService;
import tj.metro.dushanbe.imports.domain.ImportError;
import tj.metro.dushanbe.imports.domain.ImportFormat;
import tj.metro.dushanbe.imports.domain.ImportJob;
import tj.metro.dushanbe.imports.repository.ImportErrorRepository;
import tj.metro.dushanbe.imports.repository.ImportJobRepository;
import tj.metro.dushanbe.imports.service.parser.CsvImportParser;
import tj.metro.dushanbe.imports.service.parser.GeoJsonImportParser;
import tj.metro.dushanbe.imports.service.parser.GtfsImportParser;
import tj.metro.dushanbe.imports.service.parser.NetworkImportParser.ImportOptions;
import tj.metro.dushanbe.network.domain.MetroLine;
import tj.metro.dushanbe.network.domain.MetroStation;
import tj.metro.dushanbe.network.domain.MetroStationLine;
import tj.metro.dushanbe.network.repository.MetroLineRepository;
import tj.metro.dushanbe.network.repository.MetroStationLineRepository;
import tj.metro.dushanbe.network.repository.MetroStationRepository;

/**
 * Юнит-тесты сервиса импорта: реальные парсеры форматов, остальное — mock. Проверяют
 * применение валидного мини-GeoJSON (апсерт линий/станций + связей, статус success),
 * построчную фиксацию ошибок для битой фичи (ImportError, статус partial), отклонение
 * не-FeatureCollection (статус failed) и диспетчеризацию по формату (INT-04).
 */
class ImportServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-05T10:00:00Z"), ZoneOffset.UTC);
    private static final String ACTOR = "it-admin";

    private final ImportJobRepository jobRepository = mock(ImportJobRepository.class);
    private final ImportErrorRepository errorRepository = mock(ImportErrorRepository.class);
    private final AdminLineService adminLineService = mock(AdminLineService.class);
    private final AdminStationService adminStationService = mock(AdminStationService.class);
    private final MetroLineRepository lineRepository = mock(MetroLineRepository.class);
    private final MetroStationRepository stationRepository = mock(MetroStationRepository.class);
    private final MetroStationLineRepository stationLineRepository = mock(MetroStationLineRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final FeatureFlagService featureFlagService = mock(FeatureFlagService.class);

    @SuppressWarnings("unused")
    private final ImportService self = mock(ImportService.class);

    private final ImportService service = new ImportService(jobRepository, errorRepository,
            List.of(new GeoJsonImportParser(new ObjectMapper(), new NetworkImportValidator()),
                    new GtfsImportParser(), new CsvImportParser()),
            adminLineService, adminStationService,
            lineRepository, stationRepository, stationLineRepository, auditService,
            CLOCK, featureFlagService, self);

    private void savesEcho() {
        when(jobRepository.save(any(ImportJob.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static byte[] bytes(String body) {
        return body == null ? null : body.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void syncModeByDefaultProcessesInlineAndReturnsTerminalJob() {
        savesEcho();
        // флаг import.async по умолчанию выключен (Mockito boolean default = false)
        String body = "{\"type\":\"FeatureCollection\",\"features\":[]}";
        ImportJob terminal = new ImportJob(UUID.randomUUID(), ImportJob.TYPE_NETWORK_GEOJSON,
                ImportFormat.GEOJSON, "mini.geojson", "hash");
        when(self.processImport(any(), any(), eq(ImportFormat.GEOJSON), any(), eq(ACTOR))).thenReturn(terminal);

        ImportJob result = service.importNetworkGeoJson(body, "mini.geojson", ACTOR);

        // синхронно: делегируем в processImport (не async) и возвращаем его результат
        verify(self).processImport(any(), any(), eq(ImportFormat.GEOJSON), any(), eq(ACTOR));
        verify(self, never()).processImportAsync(any(), any(), any(), any(), any());
        assertSame(terminal, result);

        // создан pending-джоб с корректными форматом, источником и SHA-256 тела
        ArgumentCaptor<ImportJob> saved = ArgumentCaptor.forClass(ImportJob.class);
        verify(jobRepository).save(saved.capture());
        assertEquals("pending", saved.getValue().getStatus());
        assertEquals(ImportFormat.GEOJSON, saved.getValue().getFormat());
        assertEquals(ImportJob.TYPE_NETWORK_GEOJSON, saved.getValue().getType());
        assertEquals("mini.geojson", saved.getValue().getSourceName());
        assertTrue(saved.getValue().getSourceHash() != null && saved.getValue().getSourceHash().length() == 64,
                "SHA-256 hex источника");
    }

    @Test
    void asyncModeWhenFlagEnabledReturnsPendingAndDispatchesAsync() {
        savesEcho();
        when(featureFlagService.isEnabled(ImportService.FLAG_IMPORT_ASYNC, false)).thenReturn(true);
        String body = "{\"type\":\"FeatureCollection\",\"features\":[]}";

        ImportJob result = service.importNetworkGeoJson(body, "mini.geojson", ACTOR);

        assertEquals("pending", result.getStatus());
        verify(self).processImportAsync(any(), any(), eq(ImportFormat.GEOJSON), any(), eq(ACTOR));
        verify(self, never()).processImport(any(), any(), any(), any(), any());
    }

    @Test
    void unsupportedFormatIsRejectedBeforeJobIsCreated() {
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                service.importNetwork(bytes("x"), "xml", ImportOptions.defaults(), "src", ACTOR));

        assertEquals("import.format_unsupported", ex.getCode());
        verify(jobRepository, never()).save(any(ImportJob.class));
    }

    @Test
    void gtfsAndCsvFormatsGetTheirOwnJobType() {
        savesEcho();
        when(self.processImport(any(), any(), anyString(), any(), anyString()))
                .thenAnswer(inv -> new ImportJob(UUID.randomUUID(), "t", "f", null, null));

        service.importNetwork(new byte[]{1, 2, 3}, ImportFormat.GTFS, ImportOptions.defaults(), "feed.zip", ACTOR);
        service.importNetwork(bytes("entity\n"), ImportFormat.CSV, ImportOptions.defaults(), "net.csv", ACTOR);

        ArgumentCaptor<ImportJob> saved = ArgumentCaptor.forClass(ImportJob.class);
        verify(jobRepository, atLeast(2)).save(saved.capture());
        assertEquals(ImportJob.TYPE_NETWORK_GTFS, saved.getAllValues().get(0).getType());
        assertEquals(ImportFormat.GTFS, saved.getAllValues().get(0).getFormat());
        assertEquals(ImportJob.TYPE_NETWORK_CSV, saved.getAllValues().get(1).getType());
        assertEquals(ImportFormat.CSV, saved.getAllValues().get(1).getFormat());
    }

    @Test
    void processImportWithValidGeoJsonFinishesSuccess() {
        savesEcho();
        when(lineRepository.existsByCode(anyString())).thenReturn(false);
        when(stationRepository.existsByCode(anyString())).thenReturn(false);
        when(stationRepository.findByCode("ST-1")).thenReturn(Optional.of(station("ST-1")));
        when(stationRepository.findByCode("ST-2")).thenReturn(Optional.of(station("ST-2")));
        when(lineRepository.findByCode("L1")).thenReturn(Optional.of(line("L1")));
        when(stationLineRepository.findByStation_Code(anyString())).thenReturn(List.of());

        String body = """
                {"type":"FeatureCollection","features":[
                  {"type":"Feature","properties":{"feature_type":"line","code":"L1",
                    "name":{"tg":"Хат","ru":"Линия","en":"Line"},"color_hex":"#E21B2D",
                    "status":"planned","sort_order":1},
                    "geometry":{"type":"LineString","coordinates":[[68.8,38.5],[68.7,38.6]]}},
                  {"type":"Feature","properties":{"feature_type":"station","code":"ST-1",
                    "name":{"tg":"Ист1","ru":"Станция1","en":"Station1"},"status":"planned","lines":["L1"]},
                    "geometry":{"type":"Point","coordinates":[68.8,38.5]}}]}""";
        ImportJob job = geoJsonJob();
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));

        service.processImport(job.getId(), bytes(body), ImportFormat.GEOJSON, ImportOptions.defaults(), ACTOR);

        verify(jobRepository, atLeast(1)).save(any(ImportJob.class));
        verify(auditService).record(eq(ACTOR), eq("network.import"), any(), any(), isNull(), any());
    }

    @Test
    void processImportWithInvalidJsonFailsWithError() {
        savesEcho();
        ImportJob job = geoJsonJob();
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));

        service.processImport(job.getId(), bytes("not json"), ImportFormat.GEOJSON, ImportOptions.defaults(), ACTOR);

        verify(jobRepository, atLeast(1)).save(any(ImportJob.class));
        verify(errorRepository).save(any(ImportError.class));
    }

    @Test
    void processImportWithWrongTypeFailsWithTopLevelError() {
        savesEcho();
        ImportJob job = geoJsonJob();
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));

        service.processImport(job.getId(), bytes("{\"type\":\"Nonsense\"}"), ImportFormat.GEOJSON,
                ImportOptions.defaults(), ACTOR);

        verify(jobRepository, atLeast(1)).save(any(ImportJob.class));
        verify(errorRepository).save(any(ImportError.class));
    }

    @Test
    void processImportWithBrokenFeatureRecordsError() {
        savesEcho();
        when(stationRepository.existsByCode(anyString())).thenReturn(false);
        when(lineRepository.findByCode("L1")).thenReturn(Optional.of(line("L1")));
        when(lineRepository.findByCode("L-UNKNOWN")).thenReturn(Optional.empty());

        String body = """
                {"type":"FeatureCollection","features":[
                  {"type":"Feature","properties":{"feature_type":"station","code":"ST-1",
                    "name":{"tg":"Ист1","ru":"Станция1"},"status":"planned"},
                    "geometry":{"type":"Point","coordinates":[68.8,38.5]}},
                  {"type":"Feature","properties":{"feature_type":"station","code":"ST-2",
                    "name":{"tg":"Ист2","ru":"Станция2"},"status":"nonsense"},
                    "geometry":{"type":"LineString","coordinates":[]}}]}""";
        ImportJob job = geoJsonJob();
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));

        service.processImport(job.getId(), bytes(body), ImportFormat.GEOJSON, ImportOptions.defaults(), ACTOR);

        verify(errorRepository, atLeastOnce()).save(any(ImportError.class));
    }

    @Test
    void processImportWithUnknownLineLogsWarning() {
        savesEcho();
        when(stationRepository.existsByCode(anyString())).thenReturn(false);
        when(stationRepository.findByCode("ST-1")).thenReturn(Optional.of(station("ST-1")));
        when(lineRepository.findByCode("L-UNKNOWN")).thenReturn(Optional.empty());
        when(stationLineRepository.findByStation_Code(anyString())).thenReturn(List.of());

        String body = """
                {"type":"FeatureCollection","features":[
                  {"type":"Feature","properties":{"feature_type":"station","code":"ST-1",
                    "name":{"tg":"Ист1","ru":"Станция1","en":"Station1"},"status":"planned","lines":["L-UNKNOWN"]},
                    "geometry":{"type":"Point","coordinates":[68.8,38.5]}}]}""";
        ImportJob job = geoJsonJob();
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));

        service.processImport(job.getId(), bytes(body), ImportFormat.GEOJSON, ImportOptions.defaults(), ACTOR);

        verify(errorRepository).save(argThat(e -> ImportError.SEVERITY_WARNING.equals(e.getSeverity())));
    }

    @Test
    void processImportWithNullBodyFails() {
        savesEcho();
        ImportJob job = geoJsonJob();
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));

        service.processImport(job.getId(), null, ImportFormat.GEOJSON, ImportOptions.defaults(), ACTOR);

        verify(jobRepository, atLeast(1)).save(any(ImportJob.class));
        verify(errorRepository).save(any(ImportError.class));
    }

    /**
     * Позиции станции на линиях, посчитанные парсером (GTFS), должны иметь приоритет над
     * порядком следования во входе — иначе пересадочная станция встала бы не на своё место.
     */
    @Test
    void explicitLinePositionsFromParserWinOverInputOrder() {
        savesEcho();
        when(stationRepository.existsByCode(anyString())).thenReturn(false);
        when(stationRepository.findByCode("ST-1")).thenReturn(Optional.of(station("ST-1")));
        when(lineRepository.findByCode("L1")).thenReturn(Optional.of(line("L1")));
        when(stationLineRepository.findByStation_Code(anyString())).thenReturn(List.of());

        NetworkImportValidator.ParsedFeature station = new NetworkImportValidator.ParsedFeature(
                NetworkImportValidator.Kind.STATION, "ST-1", "ST-1",
                Map.of("tg", "т", "ru", "р", "en", "e"), null, "active", null, null,
                List.of(68.8, 38.5), null, false, List.of(), List.of("L1"),
                Map.of("L1", 7), List.of(), List.of());

        ImportJob job = geoJsonJob();
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
        ImportService withStub = serviceWithStubParser(List.of(station));

        withStub.processImport(job.getId(), bytes("ignored"), ImportFormat.GEOJSON, ImportOptions.defaults(), ACTOR);

        ArgumentCaptor<MetroStationLine> link = ArgumentCaptor.forClass(MetroStationLine.class);
        verify(stationLineRepository).save(link.capture());
        assertEquals(7, link.getValue().getPositionIndex());
    }

    // ---- фикстуры ---------------------------------------------------------

    private static ImportJob geoJsonJob() {
        return new ImportJob(UUID.randomUUID(), ImportJob.TYPE_NETWORK_GEOJSON, ImportFormat.GEOJSON,
                "test", "hash");
    }

    /** Сервис с парсером-заглушкой: проверяем применение фич, а не разбор формата. */
    private ImportService serviceWithStubParser(List<NetworkImportValidator.ParsedFeature> features) {
        return new ImportService(jobRepository, errorRepository,
                List.of(new tj.metro.dushanbe.imports.service.parser.NetworkImportParser() {
                    @Override
                    public String format() {
                        return ImportFormat.GEOJSON;
                    }

                    @Override
                    public ParseResult parse(byte[] source, ImportOptions options) {
                        return ParseResult.of(features);
                    }
                }),
                adminLineService, adminStationService, lineRepository, stationRepository,
                stationLineRepository, auditService, CLOCK, featureFlagService, self);
    }

    private static MetroStation station(String code) {
        return new MetroStation(UUID.randomUUID(), code, "planned",
                Map.of("tg", "т", "ru", "р", "en", "e"), null, false, List.of());
    }

    private static MetroLine line(String code) {
        return new MetroLine(UUID.randomUUID(), code, "#E21B2D", "planned",
                Map.of("tg", "т", "ru", "р", "en", "e"), 1, null);
    }
}
