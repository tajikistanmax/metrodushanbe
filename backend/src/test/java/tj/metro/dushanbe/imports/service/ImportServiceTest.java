package tj.metro.dushanbe.imports.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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

    private final ImportService service = new ImportService(jobRepository, errorRepository,
            new NetworkImportValidator(), adminLineService, adminStationService,
            lineRepository, stationRepository, stationLineRepository, auditService,
            new ObjectMapper(), CLOCK);

    private void savesEcho() {
        when(jobRepository.save(any(ImportJob.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void validGeoJsonCreatesLinesStationsAndLinks() {
        savesEcho();
        when(lineRepository.existsByCode(anyString())).thenReturn(false);
        when(stationRepository.existsByCode(anyString())).thenReturn(false);
        // после апсерта станция и линия доступны для перепривязки
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
                    "geometry":{"type":"Point","coordinates":[68.8,38.5]}},
                  {"type":"Feature","properties":{"feature_type":"station","code":"ST-2",
                    "name":{"tg":"Ист2","ru":"Станция2","en":"Station2"},"status":"planned","lines":["L1"]},
                    "geometry":{"type":"Point","coordinates":[68.7,38.6]}}]}""";

        ImportJob job = service.importNetworkGeoJson(body, "mini.geojson", ACTOR);

        assertEquals(ImportJob.STATUS_SUCCESS, job.getStatus());
        assertEquals(3, job.getFeatureCount());
        assertEquals(3, job.getCreatedCount());
        assertEquals(0, job.getFailedCount());
        verify(adminLineService).create(any(), eq(ACTOR));
        verify(adminStationService, org.mockito.Mockito.times(2)).create(any(StationCreateRequest.class), eq(ACTOR));
        // две станции привязаны к L1 с позициями 1 и 2
        ArgumentCaptor<MetroStationLine> links = ArgumentCaptor.forClass(MetroStationLine.class);
        verify(stationLineRepository, org.mockito.Mockito.times(2)).save(links.capture());
        assertEquals(List.of(1, 2), links.getAllValues().stream().map(MetroStationLine::getPositionIndex).toList());
        verify(auditService).record(eq(ACTOR), eq("network.import"), eq("import_job"), anyString(), any(), any());
        assertEquals("mini.geojson", job.getSourceName());
        assertTrue(job.getSourceHash() != null && job.getSourceHash().length() == 64, "SHA-256 hex источника");
    }

    @Test
    void brokenFeatureIsRecordedAndJobPartial() {
        savesEcho();
        when(lineRepository.existsByCode(anyString())).thenReturn(false);
        when(stationRepository.existsByCode(anyString())).thenReturn(false);
        when(stationRepository.findByCode("ST-OK")).thenReturn(Optional.of(station("ST-OK")));
        when(stationLineRepository.findByStation_Code(anyString())).thenReturn(List.of());

        // одна валидная станция + одна битая (нет name.en, нет geometry Point)
        String body = """
                {"type":"FeatureCollection","features":[
                  {"type":"Feature","properties":{"feature_type":"station","code":"ST-OK",
                    "name":{"tg":"Ок","ru":"Ок","en":"Ok"},"status":"planned"},
                    "geometry":{"type":"Point","coordinates":[68.8,38.5]}},
                  {"type":"Feature","properties":{"feature_type":"station","code":"ST-BAD",
                    "name":{"tg":"Плох","ru":"Плохо"},"status":"nonsense"},
                    "geometry":{"type":"LineString","coordinates":[]}}]}""";

        ImportJob job = service.importNetworkGeoJson(body, null, ACTOR);

        assertEquals(ImportJob.STATUS_PARTIAL, job.getStatus());
        assertEquals(2, job.getFeatureCount());
        assertEquals(1, job.getCreatedCount());
        assertEquals(1, job.getFailedCount());
        // битая станция не должна доходить до апсерта
        verify(adminStationService, never()).create(
                org.mockito.ArgumentMatchers.argThat(r -> "ST-BAD".equals(r.code())), anyString());
        ArgumentCaptor<ImportError> errors = ArgumentCaptor.forClass(ImportError.class);
        verify(errorRepository, org.mockito.Mockito.atLeastOnce()).save(errors.capture());
        assertTrue(errors.getAllValues().stream().anyMatch(e -> "ST-BAD".equals(e.getFeatureRef())),
                "должна быть построчная ошибка по ST-BAD");
    }

    @Test
    void nonFeatureCollectionFailsWithTopLevelError() {
        savesEcho();

        ImportJob job = service.importNetworkGeoJson("{\"type\":\"Nonsense\"}", null, ACTOR);

        assertEquals(ImportJob.STATUS_FAILED, job.getStatus());
        ArgumentCaptor<ImportError> errors = ArgumentCaptor.forClass(ImportError.class);
        verify(errorRepository).save(errors.capture());
        assertEquals(ImportError.TOP_LEVEL_REF, errors.getValue().getFeatureRef());
        verify(adminLineService, never()).create(any(), anyString());
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
