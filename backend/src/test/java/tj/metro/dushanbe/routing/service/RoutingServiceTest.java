package tj.metro.dushanbe.routing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tj.metro.dushanbe.common.error.NotFoundException;
import tj.metro.dushanbe.network.domain.MetroLine;
import tj.metro.dushanbe.network.domain.MetroStation;
import tj.metro.dushanbe.network.domain.MetroStationLine;
import tj.metro.dushanbe.network.repository.MetroLineRepository;
import tj.metro.dushanbe.network.repository.MetroStationLineRepository;
import tj.metro.dushanbe.network.repository.MetroStationRepository;
import tj.metro.dushanbe.routing.web.dto.RouteDto;
import tj.metro.dushanbe.routing.web.dto.RouteLegDto;

/**
 * Юнит-тесты RoutingService на демо-сети без БД: репозитории — mock.
 * Демо-сеть повторяет seed (V002): L1 (ST-L1-01..08 с пересадочным ST-HUB-CENTER
 * на позиции 5) и L2 (ST-L2-01, ST-L2-02, ST-HUB-CENTER, ST-L2-04..06). Дополнительно
 * заведена изолированная линия L3 (ST-ISO-1, ST-ISO-2) для проверки «нет пути».
 * Веса: перегон = 2 мин, пересадка = 5 мин. Проверяются маршрут в пределах линии,
 * маршрут с пересадкой, ранжирование, исключение закрытой станции (BR-RTE-1),
 * несуществующая станция и отсутствие пути.
 */
class RoutingServiceTest {

    private final MetroStationRepository stationRepository = mock(MetroStationRepository.class);
    private final MetroLineRepository lineRepository = mock(MetroLineRepository.class);
    private final MetroStationLineRepository stationLineRepository = mock(MetroStationLineRepository.class);

    private final RoutingService service =
            new RoutingService(stationRepository, lineRepository, stationLineRepository);

    @Test
    void routeWithinSingleLineHasNoTransfers() {
        stubNetwork(demoNetwork());

        RouteDto route = service.route("ST-L1-01", "ST-L1-04");

        assertTrue(route.found());
        assertEquals(0, route.transfers());
        assertEquals(3, route.segmentCount());       // 01->02->03->04 = 3 перегона
        assertEquals(6, route.estimatedMinutes());    // 3 * 2 мин
        assertEquals(1, route.legs().size());
        RouteLegDto leg = route.legs().getFirst();
        assertEquals("L1", leg.lineCode());
        assertEquals(List.of("ST-L1-01", "ST-L1-02", "ST-L1-03", "ST-L1-04"), leg.stations());
        assertEquals(List.of("ST-L1-01", "ST-L1-02", "ST-L1-03", "ST-L1-04"),
                route.stops().stream().map(s -> s.code()).toList());
        assertTrue(route.stops().stream().noneMatch(s -> s.transfer()));
    }

    @Test
    void routeAcrossLinesUsesTransferHub() {
        stubNetwork(demoNetwork());

        RouteDto route = service.route("ST-L1-01", "ST-L2-06");

        assertTrue(route.found());
        assertEquals(1, route.transfers());
        assertEquals(2, route.legs().size());
        // L1: 01->02->03->04->HUB = 4 перегона; L2: HUB->04->05->06 = 3 перегона
        assertEquals(7, route.segmentCount());
        assertEquals(19, route.estimatedMinutes()); // 4*2 + 3*2 + 5 (пересадка)

        assertEquals("L1", route.legs().get(0).lineCode());
        assertEquals("ST-HUB-CENTER", route.legs().get(0).stations().getLast());
        assertEquals("L2", route.legs().get(1).lineCode());
        assertEquals("ST-HUB-CENTER", route.legs().get(1).stations().getFirst());

        // пересадочный узел присутствует в stops ровно один раз и помечен transfer
        List<String> hubStops = route.stops().stream().map(s -> s.code())
                .filter("ST-HUB-CENTER"::equals).toList();
        assertEquals(1, hubStops.size());
        assertTrue(route.stops().stream().anyMatch(s -> "ST-HUB-CENTER".equals(s.code()) && s.transfer()));
    }

    @Test
    void closedStationBreaksLineAndYieldsNoRoute() {
        // ST-L1-03 закрыта (BR-RTE-1): участок L1 между 01..04 разрывается, обхода нет
        Network network = demoNetwork();
        network.close("ST-L1-03");
        stubNetwork(network);

        RouteDto route = service.route("ST-L1-01", "ST-L1-04");

        assertFalse(route.found());
        assertTrue(route.legs().isEmpty());
        assertTrue(route.stops().isEmpty());
    }

    @Test
    void unknownStationThrowsNotFoundWithRoutingCode() {
        stubNetwork(demoNetwork());

        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> service.route("ST-NOPE", "ST-L2-06"));

        assertEquals("route.station_not_found", ex.getCode());
    }

    @Test
    void disconnectedComponentsYieldNoRoute() {
        // ST-ISO-1 на изолированной линии L3, не связанной с L1/L2
        stubNetwork(demoNetwork());

        RouteDto route = service.route("ST-L1-01", "ST-ISO-1");

        assertFalse(route.found());
        assertEquals(0, route.transfers());
        assertEquals(0, route.estimatedMinutes());
        assertTrue(route.legs().isEmpty());
    }

    // ---------------------------------------------------------------------
    // Фикстуры сети
    // ---------------------------------------------------------------------

    private void stubNetwork(Network network) {
        // публичное чтение маршрутизации использует active-варианты (исключают soft-deleted)
        when(stationRepository.findByCodeAndDeletedAtIsNull(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(network.stations.get(inv.getArgument(0))));
        when(lineRepository.findByDeletedAtIsNullOrderBySortOrderAscCodeAsc()).thenReturn(network.lines);
        when(stationLineRepository.findAllActiveWithStationAndLine()).thenReturn(network.links);
    }

    private Network demoNetwork() {
        Network n = new Network();
        MetroLine l1 = n.line("L1", "#E21B2D", 1);
        MetroLine l2 = n.line("L2", "#138A3D", 2);
        MetroLine l3 = n.line("L3", "#0E5A8A", 3);

        // L1
        n.link(l1, n.station("ST-L1-01", false), 1);
        n.link(l1, n.station("ST-L1-02", false), 2);
        n.link(l1, n.station("ST-L1-03", false), 3);
        n.link(l1, n.station("ST-L1-04", false), 4);
        MetroStation hub = n.station("ST-HUB-CENTER", true);
        n.link(l1, hub, 5);
        n.link(l1, n.station("ST-L1-06", false), 6);
        n.link(l1, n.station("ST-L1-07", false), 7);
        n.link(l1, n.station("ST-L1-08", false), 8);

        // L2 (через тот же пересадочный узел hub)
        n.link(l2, n.station("ST-L2-01", false), 1);
        n.link(l2, n.station("ST-L2-02", false), 2);
        n.link(l2, hub, 3);
        n.link(l2, n.station("ST-L2-04", false), 4);
        n.link(l2, n.station("ST-L2-05", false), 5);
        n.link(l2, n.station("ST-L2-06", false), 6);

        // L3 — изолированная компонента
        n.link(l3, n.station("ST-ISO-1", false), 1);
        n.link(l3, n.station("ST-ISO-2", false), 2);
        return n;
    }

    /** Мутабельный конструктор демо-сети (общие объекты станций разделяются между линиями). */
    private static final class Network {
        private final Map<String, MetroStation> stations = new HashMap<>();
        private final Map<String, MetroLine> lineByCode = new HashMap<>();
        private final List<MetroLine> lines = new ArrayList<>();
        private final List<MetroStationLine> links = new ArrayList<>();

        MetroLine line(String code, String colorHex, int sortOrder) {
            MetroLine line = new MetroLine(UUID.randomUUID(), code, colorHex, "planned",
                    Map.of("tg", code, "ru", code, "en", code), sortOrder, null);
            lines.add(line);
            lineByCode.put(code, line);
            return line;
        }

        MetroStation station(String code, boolean transfer) {
            return stations.computeIfAbsent(code, c -> new MetroStation(UUID.randomUUID(), c, "planned",
                    Map.of("tg", c, "ru", c, "en", c), null, transfer, List.of()));
        }

        void link(MetroLine line, MetroStation station, int positionIndex) {
            links.add(new MetroStationLine(station, line, positionIndex));
        }

        /** Помечает станцию закрытой (temporarily_closed), пересоздавая её объект. */
        void close(String code) {
            MetroStation old = stations.get(code);
            MetroStation closed = new MetroStation(old.getId(), code, "temporarily_closed",
                    old.getNameI18n(), null, old.isTransfer(), List.of());
            stations.put(code, closed);
            // перепривязать существующие связи к закрытому объекту станции
            List<MetroStationLine> rebuilt = new ArrayList<>();
            for (MetroStationLine link : links) {
                if (link.getStation().getCode().equals(code)) {
                    rebuilt.add(new MetroStationLine(closed, link.getLine(), link.getPositionIndex()));
                } else {
                    rebuilt.add(link);
                }
            }
            links.clear();
            links.addAll(rebuilt);
        }
    }
}
