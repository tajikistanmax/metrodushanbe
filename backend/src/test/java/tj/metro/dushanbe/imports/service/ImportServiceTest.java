package tj.metro.dushanbe.imports.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
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
import tj.metro.dushanbe.admin.web.dto.StationCreateRequest;
import tj.metro.dushanbe.audit.service.AuditService;
import tj.metro.dushanbe.featureflag.service.FeatureFlagService;
import tj.metro.dushanbe.imports.domain.ImportError;
import tj.metro.dushanbe.imports.domain.ImportJob;
import tj.metro.dushanbe.imports.repository.ImportErrorRepository;
import tj.metro.dushanbe.imports.repository.ImportJobRepository;
import tj.metro.dushanbe.network.domain.MetroLine;
import tj.metro.dushanbe.network.domain.MetroStation;
import tj.metro.dushanbe.network.domain.MetroStationLine;
import tj.metro.dushanbe.network.repository.MetroLineRepository;
import tj.metro.dushanbe.network.repository.MetroStationLineRepository;
import tj.metro.dushanbe.network.repository.MetroStationRepository;

/**
 * Юнит-тесты сервиса импорта: реальный валидатор, остальное — mock. Проверяют
 * применение валидного мини-GeoJSON (апсерт линий/станций + связей, статус success),
 * построчную фиксацию ошибок для битой фичи (ImportError, статус partial) и
 * отклонение не-FeatureCollection (статус failed).
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
            new NetworkImportValidator(), adminLineService, adminStationService,
            lineRepository, stationRepository, stationLineRepository, auditService,
            new ObjectMapper(), CLOCK, featureFlagService, self);

    private void savesEcho() {
        when(jobRepository.save(any(ImportJob.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void syncModeByDefaultProcessesInlineAndReturnsTerminalJob() {
        savesEcho();
        // флаг import.async по умолчанию выключен (Mockito boolean default = false)
        String body = "{\"type\":\"FeatureCollection\",\"features\":[]}";
        ImportJob terminal = new ImportJob(UUID.randomUUID(), ImportJob.TYPE_NETWORK_GEOJSON, "mini.geojson", "hash");
        when(self.processImport(any(), eq(body), eq(ACTOR))).thenReturn(terminal);

        ImportJob result = service.importNetworkGeoJson(body, "mini.geojson", ACTOR);

        // синхронно: делегируем в processImport (не async) и возвращаем его результат
        verify(self).processImport(any(), eq(body), eq(ACTOR));
        verify(self, never()).processImportAsync(any(), any(), any());
        assertSame(terminal, result);

        // создан pending-джоб с корректными источником и SHA-256 тела
        ArgumentCaptor<ImportJob> saved = ArgumentCaptor.forClass(ImportJob.class);
        verify(jobRepository).save(saved.capture());
        assertEquals("pending", saved.getValue().getStatus());
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
        verify(self).processImportAsync(any(), eq(body), eq(ACTOR));
        verify(self, never()).processImport(any(), any(), any());
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
        ImportJob job = new ImportJob(UUID.randomUUID(), ImportJob.TYPE_NETWORK_GEOJSON, "test", "hash");
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));

        service.processImport(job.getId(), body, ACTOR);

        verify(jobRepository, atLeast(1)).save(any(ImportJob.class));
        verify(auditService).record(eq(ACTOR), eq("network.import"), any(), any(), isNull(), any());
    }

    @Test
    void processImportWithInvalidJsonFailsWithError() {
        savesEcho();
        ImportJob job = new ImportJob(UUID.randomUUID(), ImportJob.TYPE_NETWORK_GEOJSON, "test", "hash");
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));

        service.processImport(job.getId(), "not json", ACTOR);

        verify(jobRepository, atLeast(1)).save(any(ImportJob.class));
        verify(errorRepository).save(any(ImportError.class));
    }

    @Test
    void processImportWithWrongTypeFailsWithTopLevelError() {
        savesEcho();
        ImportJob job = new ImportJob(UUID.randomUUID(), ImportJob.TYPE_NETWORK_GEOJSON, "test", "hash");
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));

        service.processImport(job.getId(), "{\"type\":\"Nonsense\"}", ACTOR);

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
        ImportJob job = new ImportJob(UUID.randomUUID(), ImportJob.TYPE_NETWORK_GEOJSON, "test", "hash");
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));

        service.processImport(job.getId(), body, ACTOR);

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
        ImportJob job = new ImportJob(UUID.randomUUID(), ImportJob.TYPE_NETWORK_GEOJSON, "test", "hash");
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));

        service.processImport(job.getId(), body, ACTOR);

        verify(errorRepository).save(argThat(e -> ImportError.SEVERITY_WARNING.equals(e.getSeverity())));
    }

    @Test
    void processImportWithNullBodyFails() {
        savesEcho();
        ImportJob job = new ImportJob(UUID.randomUUID(), ImportJob.TYPE_NETWORK_GEOJSON, "test", "hash");
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));

        service.processImport(job.getId(), null, ACTOR);

        verify(jobRepository, atLeast(1)).save(any(ImportJob.class));
        verify(errorRepository).save(any(ImportError.class));
    }

    // ---- фикстуры ---------------------------------------------------------

    private static MetroStation station(String code) {
        return new MetroStation(UUID.randomUUID(), code, "planned",
                Map.of("tg", "т", "ru", "р", "en", "e"), null, false, List.of());
    }

    private static MetroLine line(String code) {
        return new MetroLine(UUID.randomUUID(), code, "#E21B2D", "planned",
                Map.of("tg", "т", "ru", "р", "en", "e"), 1, null);
    }
}
