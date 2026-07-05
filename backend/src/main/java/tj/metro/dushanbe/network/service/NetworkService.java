package tj.metro.dushanbe.network.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tj.metro.dushanbe.common.error.BadRequestException;
import tj.metro.dushanbe.common.error.NotFoundException;
import tj.metro.dushanbe.network.domain.AccessibilityFeature;
import tj.metro.dushanbe.network.domain.MetroLine;
import tj.metro.dushanbe.network.domain.MetroStation;
import tj.metro.dushanbe.network.domain.MetroStationLine;
import tj.metro.dushanbe.network.domain.StationExit;
import tj.metro.dushanbe.network.repository.AccessibilityFeatureRepository;
import tj.metro.dushanbe.network.repository.MetroLineRepository;
import tj.metro.dushanbe.network.repository.MetroStationLineRepository;
import tj.metro.dushanbe.network.repository.MetroStationRepository;
import tj.metro.dushanbe.network.repository.StationExitRepository;
import tj.metro.dushanbe.network.web.dto.AccessibilityFeatureDto;
import tj.metro.dushanbe.network.web.dto.GeoJsonFeatureCollection;
import tj.metro.dushanbe.network.web.dto.LineDto;
import tj.metro.dushanbe.network.web.dto.StationDetailDto;
import tj.metro.dushanbe.network.web.dto.StationDto;
import tj.metro.dushanbe.network.web.dto.StationExitDto;

/**
 * Сервис сетевого каталога: линии, станции, GeoJSON-слой сети.
 * Статусы — по docs/dev-conventions.md §4.
 */
@Service
@Transactional(readOnly = true)
public class NetworkService {

    /** Допустимые статусы линий. */
    public static final Set<String> LINE_STATUSES = Set.of(
            "planned", "under_construction", "testing", "active", "suspended", "decommissioned");

    /** Допустимые статусы станций. */
    public static final Set<String> STATION_STATUSES = Set.of(
            "planned", "under_construction", "testing", "active", "temporarily_closed", "decommissioned");

    private final MetroLineRepository lineRepository;
    private final MetroStationRepository stationRepository;
    private final MetroStationLineRepository stationLineRepository;
    private final StationExitRepository stationExitRepository;
    private final AccessibilityFeatureRepository accessibilityFeatureRepository;
    private final GeoJsonBuilder geoJsonBuilder;

    public NetworkService(MetroLineRepository lineRepository,
                          MetroStationRepository stationRepository,
                          MetroStationLineRepository stationLineRepository,
                          StationExitRepository stationExitRepository,
                          AccessibilityFeatureRepository accessibilityFeatureRepository,
                          GeoJsonBuilder geoJsonBuilder) {
        this.lineRepository = lineRepository;
        this.stationRepository = stationRepository;
        this.stationLineRepository = stationLineRepository;
        this.stationExitRepository = stationExitRepository;
        this.accessibilityFeatureRepository = accessibilityFeatureRepository;
        this.geoJsonBuilder = geoJsonBuilder;
    }

    /**
     * Список линий, опционально отфильтрованный по статусу. Публичное чтение
     * отдаёт только действующие линии (deleted_at IS NULL, BR-NET-2).
     */
    public List<LineDto> lines(String status) {
        List<MetroLine> lines;
        if (isBlank(status)) {
            lines = lineRepository.findByDeletedAtIsNullOrderBySortOrderAscCodeAsc();
        } else {
            requireValidStatus(status, LINE_STATUSES, "status");
            lines = lineRepository.findByStatusAndDeletedAtIsNullOrderBySortOrderAscCodeAsc(status);
        }
        return lines.stream().map(this::toDto).toList();
    }

    /** Карточка линии по коду; soft-deleted линия → 404 line.not_found. */
    public LineDto lineByCode(String code) {
        return lineRepository.findByCodeAndDeletedAtIsNull(code)
                .map(this::toDto)
                .orElseThrow(() -> NotFoundException.line(code));
    }

    /**
     * Список станций с фильтрами по линии и статусу. Публичное чтение исключает
     * soft-deleted станции и станции soft-deleted линий (BR-NET-2).
     */
    public List<StationDto> stations(String lineCode, String status) {
        if (!isBlank(status)) {
            requireValidStatus(status, STATION_STATUSES, "status");
        }
        List<MetroStation> stations;
        if (!isBlank(lineCode)) {
            lineRepository.findByCodeAndDeletedAtIsNull(lineCode)
                    .orElseThrow(() -> NotFoundException.line(lineCode));
            stations = stationRepository.findActiveByLineCodeOrderByPosition(lineCode);
        } else {
            stations = stationRepository.findByDeletedAtIsNullOrderByCodeAsc();
        }
        if (!isBlank(status)) {
            stations = stations.stream().filter(s -> status.equals(s.getStatus())).toList();
        }
        Map<UUID, List<String>> lineCodesByStation = lineCodesByStation(stationLineRepository.findAllActiveWithStationAndLine());
        return stations.stream()
                .map(s -> toDto(s, lineCodesByStation.getOrDefault(s.getId(), List.of())))
                .toList();
    }

    /** Карточка станции по коду; soft-deleted станция → 404 station.not_found. */
    public StationDto stationByCode(String code) {
        MetroStation station = stationRepository.findByCodeAndDeletedAtIsNull(code)
                .orElseThrow(() -> NotFoundException.station(code));
        Map<UUID, List<String>> lineCodesByStation = lineCodesByStation(stationLineRepository.findAllActiveWithStationAndLine());
        return toDto(station, lineCodesByStation.getOrDefault(station.getId(), List.of()));
    }

    /**
     * Детальная карточка станции по коду (NET-02/NET-03): станция, её линии,
     * выходы и объекты доступности. 404 — station.not_found.
     */
    public StationDetailDto stationDetailByCode(String code) {
        MetroStation station = stationRepository.findByCodeAndDeletedAtIsNull(code)
                .orElseThrow(() -> NotFoundException.station(code));
        Map<UUID, List<String>> lineCodesByStation = lineCodesByStation(stationLineRepository.findAllActiveWithStationAndLine());
        List<String> lineCodes = lineCodesByStation.getOrDefault(station.getId(), List.of());

        List<StationExitDto> exits = stationExitRepository.findByStationCodeOrderBySortOrder(code)
                .stream().map(this::toDto).toList();
        List<AccessibilityFeatureDto> features = accessibilityFeatureRepository.findByStationCodeOrderByType(code)
                .stream().map(this::toDto).toList();

        List<Double> coordinates = station.getPointGeom() != null
                ? List.of(station.getPointGeom().getX(), station.getPointGeom().getY())
                : List.of();
        return new StationDetailDto(station.getCode(), station.getNameI18n(), station.getStatus(),
                lineCodes, station.isTransfer(),
                station.getAccessibility() != null ? station.getAccessibility() : List.of(),
                coordinates, exits, features);
    }

    /**
     * GeoJSON-слой всей сети. Порядок фич — как в data/demo-network.geojson:
     * сначала линии (по sort_order), затем станции — по линиям и позиции вдоль линии
     * (пересадочная станция попадает в выдачу один раз).
     */
    public GeoJsonFeatureCollection networkGeoJson() {
        List<MetroLine> lines = lineRepository.findByDeletedAtIsNullOrderBySortOrderAscCodeAsc();
        List<MetroStationLine> links = sortedLinks(stationLineRepository.findAllActiveWithStationAndLine());

        Map<UUID, MetroStation> orderedStations = new LinkedHashMap<>();
        for (MetroStationLine link : links) {
            orderedStations.putIfAbsent(link.getStation().getId(), link.getStation());
        }
        // станции без привязки к линиям (на демо-данных таких нет) — в конец, по коду
        for (MetroStation station : stationRepository.findByDeletedAtIsNullOrderByCodeAsc()) {
            orderedStations.putIfAbsent(station.getId(), station);
        }
        return geoJsonBuilder.buildNetwork(lines, List.copyOf(orderedStations.values()), lineCodesByStation(links));
    }

    /** Связи станция-линия, отсортированные по (sort_order линии, позиция станции). */
    private List<MetroStationLine> sortedLinks(List<MetroStationLine> links) {
        List<MetroStationLine> sorted = new ArrayList<>(links);
        sorted.sort(Comparator
                .comparingInt((MetroStationLine sl) -> sl.getLine().getSortOrder())
                .thenComparing(sl -> sl.getLine().getCode())
                .thenComparingInt(MetroStationLine::getPositionIndex));
        return sorted;
    }

    /** Коды линий каждой станции в порядке sort_order линии (для lines[] в ответах). */
    private Map<UUID, List<String>> lineCodesByStation(List<MetroStationLine> links) {
        Map<UUID, List<String>> result = new LinkedHashMap<>();
        for (MetroStationLine link : sortedLinks(links)) {
            result.computeIfAbsent(link.getStation().getId(), k -> new ArrayList<>())
                    .add(link.getLine().getCode());
        }
        return result;
    }

    private LineDto toDto(MetroLine line) {
        return new LineDto(line.getCode(), line.getNameI18n(), line.getColorHex(),
                line.getStatus(), line.getSortOrder());
    }

    private StationDto toDto(MetroStation station, List<String> lineCodes) {
        List<Double> coordinates = station.getPointGeom() != null
                ? List.of(station.getPointGeom().getX(), station.getPointGeom().getY())
                : List.of();
        return new StationDto(station.getCode(), station.getNameI18n(), station.getStatus(),
                lineCodes, station.isTransfer(),
                station.getAccessibility() != null ? station.getAccessibility() : List.of(),
                coordinates);
    }

    private StationExitDto toDto(StationExit exit) {
        List<Double> coordinates = exit.getPointGeom() != null
                ? List.of(exit.getPointGeom().getX(), exit.getPointGeom().getY())
                : List.of();
        return new StationExitDto(exit.getCode(), exit.getNameI18n(), exit.isAccessible(), coordinates);
    }

    private AccessibilityFeatureDto toDto(AccessibilityFeature feature) {
        return new AccessibilityFeatureDto(feature.getType(), feature.getDescriptionI18n(), feature.getStatus());
    }

    private void requireValidStatus(String status, Set<String> allowed, String paramName) {
        if (!allowed.contains(status)) {
            throw new BadRequestException(
                    "Недопустимое значение параметра '" + paramName + "': " + status,
                    Map.of("parameter", paramName, "value", status, "allowed", allowed.stream().sorted().toList()));
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
