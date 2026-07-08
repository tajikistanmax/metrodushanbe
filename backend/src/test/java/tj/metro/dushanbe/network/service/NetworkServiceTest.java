package tj.metro.dushanbe.network.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import tj.metro.dushanbe.common.error.BadRequestException;
import tj.metro.dushanbe.common.error.NotFoundException;
import tj.metro.dushanbe.network.domain.AccessibilityFeature;
import tj.metro.dushanbe.network.domain.MetroLine;
import tj.metro.dushanbe.network.domain.MetroStation;
import tj.metro.dushanbe.network.domain.StationExit;
import tj.metro.dushanbe.network.repository.AccessibilityFeatureRepository;
import tj.metro.dushanbe.network.repository.MetroLineRepository;
import tj.metro.dushanbe.network.repository.MetroStationLineRepository;
import tj.metro.dushanbe.network.repository.MetroStationRepository;
import tj.metro.dushanbe.network.repository.StationExitRepository;
import tj.metro.dushanbe.network.web.dto.AccessibilityFeatureDto;
import tj.metro.dushanbe.network.web.dto.StationDetailDto;
import tj.metro.dushanbe.network.web.dto.StationExitDto;

/**
 * Юнит-тесты NetworkService на сборку детальной карточки станции без БД:
 * репозитории — mock, геометрия — JTS-фикстуры. Проверяются перенос полей
 * станции, выходов и объектов доступности в StationDetailDto и 404 на
 * неизвестном коде станции.
 */
class NetworkServiceTest {

    private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);

    private final MetroLineRepository lineRepository = mock(MetroLineRepository.class);
    private final MetroStationRepository stationRepository = mock(MetroStationRepository.class);
    private final MetroStationLineRepository stationLineRepository = mock(MetroStationLineRepository.class);
    private final StationExitRepository stationExitRepository = mock(StationExitRepository.class);
    private final AccessibilityFeatureRepository accessibilityFeatureRepository =
            mock(AccessibilityFeatureRepository.class);
    // GeoJsonBuilder не участвует в сборке карточки станции — используем реальный экземпляр
    // (инлайн-мок класса на JDK 25 недоступен, а мок здесь и не нужен).
    private final GeoJsonBuilder geoJsonBuilder = new GeoJsonBuilder();

    private final NetworkService service = new NetworkService(lineRepository, stationRepository,
            stationLineRepository, stationExitRepository, accessibilityFeatureRepository, geoJsonBuilder);

    @Test
    void stationDetailAssemblesBaseFieldsExitsAndFeatures() {
        MetroStation station = station("ST-L1-01", "Южные ворота", 68.8180, 38.5210);
        when(stationRepository.findByCodeAndDeletedAtIsNull("ST-L1-01")).thenReturn(Optional.of(station));
        when(stationLineRepository.findAllActiveWithStationAndLine()).thenReturn(List.of());
        when(stationExitRepository.findByStationCodeOrderBySortOrder("ST-L1-01")).thenReturn(List.of(
                exit(station, "EX-ST-L1-01-A", "Выход A", 68.8182, 38.5212, true),
                exit(station, "EX-ST-L1-01-B", "Выход B", 68.8178, 38.5208, false)));
        when(accessibilityFeatureRepository.findByStationCodeOrderByType("ST-L1-01")).thenReturn(List.of(
                feature(station, "elevator", "Лифт", "available"),
                feature(station, "tactile", "Тактильная плитка", "out_of_service")));

        StationDetailDto dto = service.stationDetailByCode("ST-L1-01");

        assertEquals("ST-L1-01", dto.code());
        assertEquals("Южные ворота", dto.name().get("ru"));
        assertEquals(List.of(68.8180, 38.5210), dto.coordinates());
        assertEquals(List.of(), dto.lines());

        assertEquals(2, dto.exits().size());
        StationExitDto exitA = dto.exits().getFirst();
        assertEquals("EX-ST-L1-01-A", exitA.code());
        assertTrue(exitA.isAccessible());
        assertEquals(List.of(68.8182, 38.5212), exitA.coordinates());
        assertFalse(dto.exits().get(1).isAccessible());

        assertEquals(2, dto.accessibilityFeatures().size());
        AccessibilityFeatureDto elevator = dto.accessibilityFeatures().getFirst();
        assertEquals("elevator", elevator.type());
        assertEquals("Лифт", elevator.description().get("ru"));
        assertEquals("available", elevator.status());
        assertEquals("out_of_service", dto.accessibilityFeatures().get(1).status());
    }

    @Test
    void stationWithoutDetailsReturnsEmptyLists() {
        MetroStation station = station("ST-L1-06", "Рудаки", 68.7800, 38.5850);
        when(stationRepository.findByCodeAndDeletedAtIsNull("ST-L1-06")).thenReturn(Optional.of(station));
        when(stationLineRepository.findAllActiveWithStationAndLine()).thenReturn(List.of());
        when(stationExitRepository.findByStationCodeOrderBySortOrder("ST-L1-06")).thenReturn(List.of());
        when(accessibilityFeatureRepository.findByStationCodeOrderByType("ST-L1-06")).thenReturn(List.of());

        StationDetailDto dto = service.stationDetailByCode("ST-L1-06");

        assertTrue(dto.exits().isEmpty());
        assertTrue(dto.accessibilityFeatures().isEmpty());
    }

    @Test
    void unknownStationThrowsNotFoundWithDomainCode() {
        when(stationRepository.findByCodeAndDeletedAtIsNull("ST-NOPE")).thenReturn(Optional.empty());

        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> service.stationDetailByCode("ST-NOPE"));

        assertEquals("station.not_found", ex.getCode());
    }

    @Test
    void linesReturnsAllWhenNoFilter() {
        when(lineRepository.findByDeletedAtIsNullOrderBySortOrderAscCodeAsc()).thenReturn(List.of(
                metroLine("L1", "Красная", "active", 1),
                metroLine("L2", "Синяя", "planned", 2)));

        var result = service.lines(null);

        assertEquals(2, result.size());
        assertEquals("L1", result.get(0).code());
        assertEquals("L2", result.get(1).code());
    }

    @Test
    void linesFilteredByStatusReturnsMatchingOnly() {
        when(lineRepository.findByStatusAndDeletedAtIsNullOrderBySortOrderAscCodeAsc("active")).thenReturn(List.of(
                metroLine("L1", "Красная", "active", 1)));

        var result = service.lines("active");

        assertEquals(1, result.size());
        assertEquals("L1", result.get(0).code());
    }

    @Test
    void linesWithInvalidStatusThrowsBadRequest() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.lines("invalid_status"));

        assertEquals("validation.failed", ex.getCode());
    }

    @Test
    void lineByCodeReturnsDtoForExisting() {
        when(lineRepository.findByCodeAndDeletedAtIsNull("L1")).thenReturn(
                Optional.of(metroLine("L1", "Красная", "active", 1)));

        var dto = service.lineByCode("L1");

        assertEquals("L1", dto.code());
        assertEquals("Красная", dto.name().get("ru"));
        assertEquals("active", dto.status());
    }

    @Test
    void lineByCodeThrowsNotFoundForUnknown() {
        when(lineRepository.findByCodeAndDeletedAtIsNull("L-NOPE")).thenReturn(Optional.empty());

        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> service.lineByCode("L-NOPE"));

        assertEquals("line.not_found", ex.getCode());
    }

    @Test
    void stationsByLineCodeReturnsFiltered() {
        when(lineRepository.findByCodeAndDeletedAtIsNull("L1")).thenReturn(
                Optional.of(metroLine("L1", "Красная", "active", 1)));
        when(stationRepository.findActiveByLineCodeOrderByPosition("L1")).thenReturn(List.of(
                station("ST-1", "Станция 1", 68.8, 38.5)));

        var result = service.stations("L1", null);

        assertEquals(1, result.size());
        assertEquals("ST-1", result.get(0).code());
    }

    @Test
    void stationsByUnknownLineThrowsNotFound() {
        when(lineRepository.findByCodeAndDeletedAtIsNull("L-NOPE")).thenReturn(Optional.empty());

        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> service.stations("L-NOPE", null));

        assertEquals("line.not_found", ex.getCode());
    }

    @Test
    void stationsWithInvalidStatusThrowsBadRequest() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.stations(null, "invalid_status"));

        assertEquals("validation.failed", ex.getCode());
    }

    @Test
    void stationByCodeReturnsDtoWithLines() {
        MetroStation st = station("ST-1", "Станция 1", 68.8, 38.5);
        when(stationRepository.findByCodeAndDeletedAtIsNull("ST-1")).thenReturn(Optional.of(st));
        when(stationLineRepository.findAllActiveWithStationAndLine()).thenReturn(List.of());

        var dto = service.stationByCode("ST-1");

        assertEquals("ST-1", dto.code());
        assertFalse(dto.coordinates().isEmpty());
    }

    @Test
    void stationByCodeThrowsNotFoundForUnknown() {
        when(stationRepository.findByCodeAndDeletedAtIsNull("ST-NOPE")).thenReturn(Optional.empty());

        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> service.stationByCode("ST-NOPE"));

        assertEquals("station.not_found", ex.getCode());
    }

    // ---- фикстуры ---------------------------------------------------------

    private static MetroStation station(String code, String nameRu, double lon, double lat) {
        return new MetroStation(UUID.randomUUID(), code, "planned",
                Map.of("tg", nameRu, "ru", nameRu, "en", nameRu),
                point(lon, lat), false, List.of("elevator", "tactile"));
    }

    private static MetroLine metroLine(String code, String nameRu, String status, int sortOrder) {
        return new MetroLine(UUID.randomUUID(), code, "#000000", status,
                Map.of("tg", nameRu, "ru", nameRu, "en", nameRu), sortOrder, null);
    }

    private static StationExit exit(MetroStation station, String code, String nameRu,
                                    double lon, double lat, boolean accessible) {
        return new StationExit(UUID.randomUUID(), station, code,
                Map.of("tg", nameRu, "ru", nameRu, "en", nameRu), point(lon, lat), accessible, 0);
    }

    private static AccessibilityFeature feature(MetroStation station, String type,
                                                String descriptionRu, String status) {
        return new AccessibilityFeature(UUID.randomUUID(), station, type,
                Map.of("tg", descriptionRu, "ru", descriptionRu, "en", descriptionRu), status);
    }

    private static Point point(double lon, double lat) {
        return GF.createPoint(new Coordinate(lon, lat));
    }
}
