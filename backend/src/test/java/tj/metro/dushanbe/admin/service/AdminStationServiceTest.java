package tj.metro.dushanbe.admin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
import tj.metro.dushanbe.admin.web.dto.AccessibilityFeatureRequest;
import tj.metro.dushanbe.admin.web.dto.StationCreateRequest;
import tj.metro.dushanbe.admin.web.dto.StationExitRequest;
import tj.metro.dushanbe.admin.web.dto.StationUpdateRequest;
import tj.metro.dushanbe.audit.service.AuditService;
import tj.metro.dushanbe.common.error.BadRequestException;
import tj.metro.dushanbe.common.error.NotFoundException;
import tj.metro.dushanbe.network.domain.AccessibilityFeature;
import tj.metro.dushanbe.network.domain.MetroStation;
import tj.metro.dushanbe.network.domain.StationExit;
import tj.metro.dushanbe.network.repository.AccessibilityFeatureRepository;
import tj.metro.dushanbe.network.repository.MetroStationRepository;
import tj.metro.dushanbe.network.repository.StationExitRepository;
import tj.metro.dushanbe.network.web.dto.AccessibilityFeatureDto;
import tj.metro.dushanbe.network.web.dto.StationDto;
import tj.metro.dushanbe.network.web.dto.StationExitDto;

class AdminStationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-05T10:00:00Z");
    private static final String ACTOR = "admin-1";
    private static final Map<String, String> FULL_I18N =
            Map.of("tg", "Истгоҳ", "ru", "Станция", "en", "Station");
    private static final List<Double> COORDS = List.of(68.0, 38.0);

    private final MetroStationRepository stationRepository = mock(MetroStationRepository.class);
    private final StationExitRepository stationExitRepository = mock(StationExitRepository.class);
    private final AccessibilityFeatureRepository accessibilityFeatureRepository = mock(AccessibilityFeatureRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final AdminStationService service =
            new AdminStationService(stationRepository, stationExitRepository, accessibilityFeatureRepository,
                    auditService, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void createPersistsAndRecordsAudit() {
        when(stationRepository.existsByCode("ST-TEST")).thenReturn(false);
        when(stationRepository.save(any(MetroStation.class))).thenAnswer(inv -> inv.getArgument(0));
        StationCreateRequest request = new StationCreateRequest("ST-TEST", FULL_I18N, "active",
                COORDS, false, List.of(), null);

        StationDto dto = service.create(request, ACTOR);

        assertEquals("ST-TEST", dto.code());
        verify(stationRepository).save(any(MetroStation.class));
        verify(auditService).record(eq(ACTOR), eq("station.create"), eq("station"), eq("ST-TEST"), isNull(), any());
    }

    @Test
    void createRejectsIncompleteNameI18n() {
        when(stationRepository.existsByCode("ST-BAD")).thenReturn(false);
        StationCreateRequest request = new StationCreateRequest("ST-BAD",
                Map.of("tg", "Истгоҳ", "ru", "Станция"), "active", COORDS, false, List.of(), null);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.create(request, ACTOR));

        assertEquals("validation.i18n_incomplete", ex.getCode());
        verify(stationRepository, never()).save(any());
    }

    @Test
    void updateModifiesAndRecordsAudit() {
        MetroStation station = new MetroStation(UUID.randomUUID(), "ST-UPD", "active",
                FULL_I18N, AdminSupport.point(COORDS), false, List.of());
        when(stationRepository.findByCode("ST-UPD")).thenReturn(Optional.of(station));
        when(stationRepository.save(any(MetroStation.class))).thenAnswer(inv -> inv.getArgument(0));
        StationUpdateRequest request = new StationUpdateRequest(FULL_I18N, "planned", null, true,
                List.of("elevator"), null);

        StationDto dto = service.update("ST-UPD", request, ACTOR);

        assertEquals("ST-UPD", dto.code());
        verify(stationRepository).save(any(MetroStation.class));
        verify(auditService).record(eq(ACTOR), eq("station.update"), eq("station"), eq("ST-UPD"), any(), any());
    }

    @Test
    void softDeleteMarksDeletedAndAudits() {
        MetroStation station = new MetroStation(UUID.randomUUID(), "ST-DEL", "active",
                FULL_I18N, AdminSupport.point(COORDS), false, List.of());
        when(stationRepository.findByCode("ST-DEL")).thenReturn(Optional.of(station));
        when(stationRepository.save(any(MetroStation.class))).thenAnswer(inv -> inv.getArgument(0));

        service.softDelete("ST-DEL", ACTOR);

        ArgumentCaptor<MetroStation> saved = ArgumentCaptor.forClass(MetroStation.class);
        verify(stationRepository).save(saved.capture());
        assertNotNull(saved.getValue().getDeletedAt());
        verify(auditService).record(eq(ACTOR), eq("station.delete"), eq("station"), eq("ST-DEL"), any(), any());
    }

    @Test
    void softDeleteUnknownCodeThrowsNotFound() {
        when(stationRepository.findByCode("ST-NOPE")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.softDelete("ST-NOPE", ACTOR));
    }

    @Test
    void createExitPersistsAndRecordsAudit() {
        MetroStation station = new MetroStation(UUID.randomUUID(), "ST-EXIT", "active",
                FULL_I18N, AdminSupport.point(COORDS), false, List.of());
        when(stationRepository.findByCode("ST-EXIT")).thenReturn(Optional.of(station));
        when(stationExitRepository.existsByCode("EX-1")).thenReturn(false);
        when(stationExitRepository.save(any(StationExit.class))).thenAnswer(inv -> inv.getArgument(0));
        StationExitRequest request = new StationExitRequest("EX-1", FULL_I18N, COORDS, true, 1);

        StationExitDto dto = service.createExit("ST-EXIT", request, ACTOR);

        assertEquals("EX-1", dto.code());
        verify(stationExitRepository).save(any(StationExit.class));
        verify(auditService).record(eq(ACTOR), eq("station_exit.create"), eq("station_exit"), eq("EX-1"), isNull(), any());
    }

    @Test
    void createFeaturePersistsAndRecordsAudit() {
        MetroStation station = new MetroStation(UUID.randomUUID(), "ST-FEAT", "active",
                FULL_I18N, AdminSupport.point(COORDS), false, List.of());
        when(stationRepository.findByCode("ST-FEAT")).thenReturn(Optional.of(station));
        when(accessibilityFeatureRepository.save(any(AccessibilityFeature.class))).thenAnswer(inv -> inv.getArgument(0));
        AccessibilityFeatureRequest request = new AccessibilityFeatureRequest("elevator", FULL_I18N, "available");

        AccessibilityFeatureDto dto = service.createFeature("ST-FEAT", request, ACTOR);

        assertEquals("elevator", dto.type());
        verify(accessibilityFeatureRepository).save(any(AccessibilityFeature.class));
        verify(auditService).record(eq(ACTOR), eq("accessibility_feature.create"),
                eq("accessibility_feature"), anyString(), isNull(), any());
    }
}
