package tj.metro.dushanbe.admin.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tj.metro.dushanbe.admin.web.dto.AccessibilityFeatureRequest;
import tj.metro.dushanbe.admin.web.dto.StationCreateRequest;
import tj.metro.dushanbe.admin.web.dto.StationExitRequest;
import tj.metro.dushanbe.admin.web.dto.StationUpdateRequest;
import tj.metro.dushanbe.audit.service.AuditService;
import tj.metro.dushanbe.common.error.NotFoundException;
import tj.metro.dushanbe.network.domain.AccessibilityFeature;
import tj.metro.dushanbe.network.domain.MetroStation;
import tj.metro.dushanbe.network.domain.StationExit;
import tj.metro.dushanbe.network.repository.AccessibilityFeatureRepository;
import tj.metro.dushanbe.network.repository.MetroStationRepository;
import tj.metro.dushanbe.network.repository.StationExitRepository;
import tj.metro.dushanbe.network.service.NetworkService;
import tj.metro.dushanbe.network.web.dto.AccessibilityFeatureDto;
import tj.metro.dushanbe.network.web.dto.StationDto;
import tj.metro.dushanbe.network.web.dto.StationExitDto;

/**
 * Admin-write контур станций и их деталей (ADM-02/NET-03): станции, выходы,
 * объекты доступности. Валидация входа (статус, координаты, полнота языков, типы),
 * обязательный аудит (BR-ADM-1), soft-delete станции (BR-NET-2). Выходы и объекты
 * доступности удаляются физически (не подпадают под soft-delete сети).
 */
@Service
public class AdminStationService {

    /** Типы объектов доступности (совпадают с CHECK в V007). */
    public static final Set<String> FEATURE_TYPES = Set.of(
            "elevator", "escalator", "ramp", "tactile", "audio_assist", "accessible_toilet");

    /** Статусы работоспособности объектов доступности (V007). */
    public static final Set<String> FEATURE_STATUSES = Set.of("available", "out_of_service", "planned");

    private final MetroStationRepository stationRepository;
    private final StationExitRepository stationExitRepository;
    private final AccessibilityFeatureRepository accessibilityFeatureRepository;
    private final AuditService auditService;
    private final Clock clock;

    public AdminStationService(MetroStationRepository stationRepository,
                               StationExitRepository stationExitRepository,
                               AccessibilityFeatureRepository accessibilityFeatureRepository,
                               AuditService auditService, Clock clock) {
        this.stationRepository = stationRepository;
        this.stationExitRepository = stationExitRepository;
        this.accessibilityFeatureRepository = accessibilityFeatureRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    // ---- Станции ----------------------------------------------------------

    /** Создать станцию (аудит station.create). */
    @Transactional
    public StationDto create(StationCreateRequest request, String actor) {
        AdminSupport.requireUnique(stationRepository.existsByCode(request.code()),
                "station.code_exists", "code", request.code());
        AdminSupport.requireIn(request.status(), NetworkService.STATION_STATUSES, "station.status_invalid", "status");
        AdminSupport.requireLanguages(request.name(), "name");

        boolean isTransfer = request.isTransfer() != null && request.isTransfer();
        List<String> accessibility = request.accessibility() != null ? request.accessibility() : List.of();
        MetroStation station = new MetroStation(UUID.randomUUID(), request.code(), request.status(),
                request.name(), AdminSupport.point(request.coordinates()), isTransfer, accessibility);
        station.updateDetails(request.status(), request.name(), request.description(), isTransfer, accessibility);
        MetroStation saved = stationRepository.save(station);

        auditService.record(actor, "station.create", "station", saved.getCode(), null, snapshot(saved));
        return toDto(saved);
    }

    /** Обновить станцию по коду (аудит station.update). */
    @Transactional
    public StationDto update(String code, StationUpdateRequest request, String actor) {
        MetroStation station = stationRepository.findByCode(code)
                .orElseThrow(() -> NotFoundException.station(code));
        AdminSupport.requireIn(request.status(), NetworkService.STATION_STATUSES, "station.status_invalid", "status");
        AdminSupport.requireLanguages(request.name(), "name");

        Map<String, Object> before = snapshot(station);
        boolean isTransfer = request.isTransfer() != null ? request.isTransfer() : station.isTransfer();
        List<String> accessibility = request.accessibility() != null
                ? request.accessibility() : station.getAccessibility();
        station.updateDetails(request.status(), request.name(), request.description(), isTransfer, accessibility);
        if (request.coordinates() != null) {
            station.setPointGeom(AdminSupport.point(request.coordinates()));
        }
        MetroStation saved = stationRepository.save(station);

        auditService.record(actor, "station.update", "station", code, before, snapshot(saved));
        return toDto(saved);
    }

    /** Soft-delete станции по коду (BR-NET-2; аудит station.delete). */
    @Transactional
    public void softDelete(String code, String actor) {
        MetroStation station = stationRepository.findByCode(code)
                .orElseThrow(() -> NotFoundException.station(code));
        Map<String, Object> before = snapshot(station);
        station.softDelete(OffsetDateTime.now(clock));
        MetroStation saved = stationRepository.save(station);
        auditService.record(actor, "station.delete", "station", code, before, snapshot(saved));
    }

    // ---- Выходы -----------------------------------------------------------

    /** Создать выход станции (аудит station_exit.create). */
    @Transactional
    public StationExitDto createExit(String stationCode, StationExitRequest request, String actor) {
        MetroStation station = stationRepository.findByCode(stationCode)
                .orElseThrow(() -> NotFoundException.station(stationCode));
        AdminSupport.requireUnique(stationExitRepository.existsByCode(request.code()),
                "station_exit.code_exists", "code", request.code());
        AdminSupport.requireLanguages(request.name(), "name");

        boolean isAccessible = request.isAccessible() != null && request.isAccessible();
        int sortOrder = request.sortOrder() != null ? request.sortOrder() : 0;
        StationExit exit = new StationExit(UUID.randomUUID(), station, request.code(), request.name(),
                AdminSupport.point(request.coordinates()), isAccessible, sortOrder);
        StationExit saved = stationExitRepository.save(exit);

        auditService.record(actor, "station_exit.create", "station_exit", saved.getCode(), null, snapshot(saved));
        return toDto(saved);
    }

    /** Удалить выход по коду (аудит station_exit.delete). */
    @Transactional
    public void deleteExit(String exitCode, String actor) {
        StationExit exit = stationExitRepository.findByCode(exitCode)
                .orElseThrow(() -> NotFoundException.stationExit(exitCode));
        Map<String, Object> before = snapshot(exit);
        stationExitRepository.delete(exit);
        auditService.record(actor, "station_exit.delete", "station_exit", exitCode, before, null);
    }

    // ---- Объекты доступности ---------------------------------------------

    /** Создать объект доступности станции (аудит accessibility_feature.create). */
    @Transactional
    public AccessibilityFeatureDto createFeature(String stationCode, AccessibilityFeatureRequest request, String actor) {
        MetroStation station = stationRepository.findByCode(stationCode)
                .orElseThrow(() -> NotFoundException.station(stationCode));
        AdminSupport.requireIn(request.type(), FEATURE_TYPES, "accessibility_feature.type_invalid", "type");
        AdminSupport.requireLanguages(request.description(), "description");
        String status = request.status() != null ? request.status() : "available";
        AdminSupport.requireIn(status, FEATURE_STATUSES, "accessibility_feature.status_invalid", "status");

        AccessibilityFeature feature = new AccessibilityFeature(UUID.randomUUID(), station,
                request.type(), request.description(), status);
        AccessibilityFeature saved = accessibilityFeatureRepository.save(feature);

        auditService.record(actor, "accessibility_feature.create", "accessibility_feature",
                saved.getId().toString(), null, snapshot(saved));
        return toDto(saved);
    }

    /** Удалить объект доступности по id (аудит accessibility_feature.delete). */
    @Transactional
    public void deleteFeature(UUID featureId, String actor) {
        AccessibilityFeature feature = accessibilityFeatureRepository.findById(featureId)
                .orElseThrow(() -> NotFoundException.accessibilityFeature(featureId.toString()));
        Map<String, Object> before = snapshot(feature);
        accessibilityFeatureRepository.delete(feature);
        auditService.record(actor, "accessibility_feature.delete", "accessibility_feature",
                featureId.toString(), before, null);
    }

    // ---- Маппинг / снимки -------------------------------------------------

    private static StationDto toDto(MetroStation station) {
        List<Double> coordinates = station.getPointGeom() != null
                ? List.of(station.getPointGeom().getX(), station.getPointGeom().getY())
                : List.of();
        return new StationDto(station.getCode(), station.getNameI18n(), station.getStatus(),
                List.of(), station.isTransfer(),
                station.getAccessibility() != null ? station.getAccessibility() : List.of(), coordinates);
    }

    private static StationExitDto toDto(StationExit exit) {
        List<Double> coordinates = exit.getPointGeom() != null
                ? List.of(exit.getPointGeom().getX(), exit.getPointGeom().getY())
                : List.of();
        return new StationExitDto(exit.getCode(), exit.getNameI18n(), exit.isAccessible(), coordinates);
    }

    private static AccessibilityFeatureDto toDto(AccessibilityFeature feature) {
        return new AccessibilityFeatureDto(feature.getType(), feature.getDescriptionI18n(), feature.getStatus());
    }

    private static Map<String, Object> snapshot(MetroStation station) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("code", station.getCode());
        snapshot.put("status", station.getStatus());
        snapshot.put("name", station.getNameI18n());
        snapshot.put("description", station.getDescriptionI18n());
        snapshot.put("isTransfer", station.isTransfer());
        snapshot.put("accessibility", station.getAccessibility());
        snapshot.put("deletedAt", station.getDeletedAt() != null ? station.getDeletedAt().toInstant().toString() : null);
        return snapshot;
    }

    private static Map<String, Object> snapshot(StationExit exit) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("code", exit.getCode());
        snapshot.put("station", exit.getStation().getCode());
        snapshot.put("name", exit.getNameI18n());
        snapshot.put("isAccessible", exit.isAccessible());
        snapshot.put("sortOrder", exit.getSortOrder());
        return snapshot;
    }

    private static Map<String, Object> snapshot(AccessibilityFeature feature) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", feature.getId().toString());
        snapshot.put("station", feature.getStation().getCode());
        snapshot.put("type", feature.getType());
        snapshot.put("description", feature.getDescriptionI18n());
        snapshot.put("status", feature.getStatus());
        return snapshot;
    }
}
