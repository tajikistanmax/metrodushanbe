package tj.metro.dushanbe.routing.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tj.metro.dushanbe.common.error.NotFoundException;
import tj.metro.dushanbe.network.domain.MetroLine;
import tj.metro.dushanbe.network.domain.MetroStation;
import tj.metro.dushanbe.network.domain.MetroStationLine;
import tj.metro.dushanbe.network.repository.MetroLineRepository;
import tj.metro.dushanbe.network.repository.MetroStationLineRepository;
import tj.metro.dushanbe.network.repository.MetroStationRepository;
import tj.metro.dushanbe.routing.web.dto.RouteDto;
import tj.metro.dushanbe.routing.web.dto.RouteLegDto;
import tj.metro.dushanbe.routing.web.dto.RouteStopDto;

/**
 * Модуль маршрутизации (ТЗ §6.2.3): поиск кратчайшего маршрута станция→станция
 * по графу сети (RTE-01) с оценкой времени и числа пересадок (RTE-05).
 *
 * <p><b>Модель графа (line-expanded).</b> Узел графа — «платформа линии на станции»,
 * пара {@code (stationCode, lineCode)}. Такое расширение позволяет честно учесть
 * штраф за пересадку прямым алгоритмом Дейкстры, не храня в состоянии «с какой линии
 * пришли». Рёбра:
 * <ul>
 *   <li><b>перегон</b> — между соседними по {@code position_index} станциями одной
 *       линии; вес {@link #SEGMENT_MINUTES};</li>
 *   <li><b>пересадка</b> — между платформами разных линий в пределах одного
 *       пересадочного узла ({@code is_transfer=true}); вес {@link #TRANSFER_MINUTES}
 *       (модель пересадки BR-RTE-2: время перехода + штраф).</li>
 * </ul>
 * Рёбра двунаправленные (метро симметрично). Пассажир стартует уже на любой из
 * платформ станции отправления (стоимость посадки — 0).
 *
 * <p><b>Ранжирование (BR-RTE-2)</b> — лексикографически по (время в пути, число
 * пересадок): очередь Дейкстры сравнивает состояния сначала по времени, затем по
 * числу пересадок.
 *
 * <p><b>Закрытые станции/линии (BR-RTE-1).</b> Из графа исключаются станции со
 * статусом {@code temporarily_closed}/{@code decommissioned} и линии
 * {@code suspended}/{@code decommissioned}. Сквозной проезд через закрытую станцию
 * невозможен (ребро-перегон не создаётся, если хотя бы один конец закрыт).
 *
 * <p><b>Веса — оценка-заглушка</b> до появления реального расписания (модуль
 * schedule-realtime, SCH): единые константы, реальные времена перегонов и переходов
 * будут импортированы из графика.
 *
 * <p><b>Задел (вне итерации):</b> RTE-08 fare-aware, RTE-07 step-free (учёт
 * accessibility узла), RTE-06 realtime-коррекция, RTE-04 альтернативные маршруты,
 * BR-RTE-3 service hours, BR-RTE-5 кэш результатов — TODO.
 */
@Service
@Transactional(readOnly = true)
public class RoutingService {

    /**
     * Оценочное время одного перегона, мин. Заглушка-константа до реального
     * расписания (SCH): реальные времена перегонов будут импортированы из графика.
     */
    static final int SEGMENT_MINUTES = 2;

    /**
     * Оценочное время/штраф пересадки в узле, мин (модель пересадки BR-RTE-2).
     * Заглушка-константа: намеренно больше перегона, чтобы при равном числе
     * перегонов маршрут с меньшим числом пересадок ранжировался выше.
     */
    static final int TRANSFER_MINUTES = 5;

    /** Статусы станций, исключаемых из маршрутизации (BR-RTE-1). */
    static final Set<String> CLOSED_STATION_STATUSES = Set.of("temporarily_closed", "decommissioned");

    /** Статусы линий, исключаемых из маршрутизации. */
    static final Set<String> CLOSED_LINE_STATUSES = Set.of("suspended", "decommissioned");

    private final MetroStationRepository stationRepository;
    private final MetroLineRepository lineRepository;
    private final MetroStationLineRepository stationLineRepository;

    public RoutingService(MetroStationRepository stationRepository,
                          MetroLineRepository lineRepository,
                          MetroStationLineRepository stationLineRepository) {
        this.stationRepository = stationRepository;
        this.lineRepository = lineRepository;
        this.stationLineRepository = stationLineRepository;
    }

    /**
     * Кратчайший маршрут от станции {@code fromCode} до {@code toCode}.
     * Несуществующий код станции → 404 {@code route.station_not_found}.
     * Нет пути (несвязные компоненты / закрытая станция) → {@code found=false}.
     */
    // Ключ по РЕАЛЬНЫМ именам параметров (fromCode/toCode). Ошибочные #from/#to в SpEL
    // разрешались в null → ключ "null:null" для ЛЮБОГО запроса: первый посчитанный
    // маршрут возвращался всем последующим (в т.ч. для несуществующих станций → 200
    // вместо 404). Проявлялось только при поднятом Redis.
    @Cacheable(value = "routes", key = "#fromCode + ':' + #toCode")
    public RouteDto route(String fromCode, String toCode) {
        // soft-deleted станция (BR-NET-2) трактуется как несуществующая
        stationRepository.findByCodeAndDeletedAtIsNull(fromCode)
                .orElseThrow(() -> routeStationNotFound(fromCode));
        stationRepository.findByCodeAndDeletedAtIsNull(toCode)
                .orElseThrow(() -> routeStationNotFound(toCode));

        Graph graph = buildGraph();

        List<String> fromLines = graph.openLinesByStation().getOrDefault(fromCode, List.of());
        List<String> toLines = graph.openLinesByStation().getOrDefault(toCode, List.of());
        // станция существует, но исключена из графа (закрыта или без привязки к линиям) — пути нет
        if (fromLines.isEmpty() || toLines.isEmpty()) {
            return noRoute(fromCode, toCode);
        }

        // тривиальный случай: отправление и назначение совпадают
        if (fromCode.equals(toCode)) {
            String line = fromLines.getFirst();
            RouteLegDto leg = new RouteLegDto(line, lineName(graph, line), lineColor(graph, line),
                    List.of(fromCode), 0, 0);
            return new RouteDto(fromCode, toCode, true, 0, 0, 0,
                    List.of(leg), List.of(stop(graph, fromCode, line, false)));
        }

        Set<RouteNode> sources = new HashSet<>();
        for (String line : fromLines) {
            sources.add(new RouteNode(fromCode, line));
        }
        List<RouteNode> path = dijkstra(graph.adjacency(), sources, toCode);
        if (path.isEmpty()) {
            return noRoute(fromCode, toCode);
        }
        return buildRoute(fromCode, toCode, path, graph);
    }

    // ---------------------------------------------------------------------
    // Построение графа из данных модуля network
    // ---------------------------------------------------------------------

    private Graph buildGraph() {
        // граф строится только по действующим линиям/станциям (BR-NET-2): soft-deleted
        // элементы сети в маршрутизацию не попадают, как и закрытые статусы (BR-RTE-1)
        Map<String, MetroLine> linesByCode = new LinkedHashMap<>();
        for (MetroLine line : lineRepository.findByDeletedAtIsNullOrderBySortOrderAscCodeAsc()) {
            linesByCode.put(line.getCode(), line);
        }

        List<MetroStationLine> links = stationLineRepository.findAllActiveWithStationAndLine();

        Map<String, MetroStation> stationsByCode = new HashMap<>();
        for (MetroStationLine link : links) {
            stationsByCode.putIfAbsent(link.getStation().getCode(), link.getStation());
        }

        // связи по линиям (только открытые линии)
        Map<String, List<MetroStationLine>> linksByLine = new LinkedHashMap<>();
        for (MetroStationLine link : links) {
            if (isClosedLine(link.getLine().getStatus())) {
                continue;
            }
            linksByLine.computeIfAbsent(link.getLine().getCode(), k -> new ArrayList<>()).add(link);
        }

        Map<RouteNode, List<Edge>> adjacency = new HashMap<>();
        Map<String, List<String>> openLinesByStation = new LinkedHashMap<>();

        // рёбра-перегоны: соседние по position_index открытые станции одной линии
        for (Map.Entry<String, List<MetroStationLine>> entry : linksByLine.entrySet()) {
            String lineCode = entry.getKey();
            List<MetroStationLine> lineLinks = new ArrayList<>(entry.getValue());
            lineLinks.sort(Comparator.comparingInt(MetroStationLine::getPositionIndex));

            for (MetroStationLine link : lineLinks) {
                MetroStation station = link.getStation();
                if (isClosedStation(station.getStatus())) {
                    continue;
                }
                RouteNode node = new RouteNode(station.getCode(), lineCode);
                adjacency.computeIfAbsent(node, k -> new ArrayList<>());
                List<String> stationLines = openLinesByStation.computeIfAbsent(station.getCode(), k -> new ArrayList<>());
                if (!stationLines.contains(lineCode)) {
                    stationLines.add(lineCode);
                }
            }

            for (int i = 0; i < lineLinks.size() - 1; i++) {
                MetroStation a = lineLinks.get(i).getStation();
                MetroStation b = lineLinks.get(i + 1).getStation();
                // сквозной проезд через закрытую станцию невозможен (BR-RTE-1)
                if (isClosedStation(a.getStatus()) || isClosedStation(b.getStatus())) {
                    continue;
                }
                RouteNode na = new RouteNode(a.getCode(), lineCode);
                RouteNode nb = new RouteNode(b.getCode(), lineCode);
                addEdge(adjacency, na, nb, SEGMENT_MINUTES, false);
                addEdge(adjacency, nb, na, SEGMENT_MINUTES, false);
            }
        }

        // рёбра-пересадки: между платформами линий в пересадочном узле (is_transfer)
        for (Map.Entry<String, List<String>> entry : openLinesByStation.entrySet()) {
            String stationCode = entry.getKey();
            List<String> stationLines = entry.getValue();
            MetroStation station = stationsByCode.get(stationCode);
            if (station == null || !station.isTransfer() || stationLines.size() < 2) {
                continue;
            }
            for (int i = 0; i < stationLines.size(); i++) {
                for (int j = i + 1; j < stationLines.size(); j++) {
                    RouteNode ni = new RouteNode(stationCode, stationLines.get(i));
                    RouteNode nj = new RouteNode(stationCode, stationLines.get(j));
                    addEdge(adjacency, ni, nj, TRANSFER_MINUTES, true);
                    addEdge(adjacency, nj, ni, TRANSFER_MINUTES, true);
                }
            }
        }

        return new Graph(adjacency, stationsByCode, linesByCode, openLinesByStation);
    }

    private static void addEdge(Map<RouteNode, List<Edge>> adjacency, RouteNode from, RouteNode to,
                                int weight, boolean transfer) {
        adjacency.computeIfAbsent(from, k -> new ArrayList<>()).add(new Edge(to, weight, transfer));
    }

    // ---------------------------------------------------------------------
    // Алгоритм Дейкстры (реализован вручную, без внешних библиотек)
    // ---------------------------------------------------------------------

    /**
     * Дейкстра от множества стартовых платформ до любой платформы станции назначения.
     * Ранжирование (BR-RTE-2): по (время, число пересадок). Возвращает путь узлов
     * от старта к цели либо пустой список, если пути нет.
     */
    private List<RouteNode> dijkstra(Map<RouteNode, List<Edge>> adjacency,
                                     Set<RouteNode> sources, String targetStation) {
        Map<RouteNode, Integer> bestTime = new HashMap<>();
        Map<RouteNode, Integer> bestTransfers = new HashMap<>();
        Map<RouteNode, RouteNode> previous = new HashMap<>();
        Set<RouteNode> settled = new HashSet<>();

        PriorityQueue<State> queue = new PriorityQueue<>(
                Comparator.comparingInt(State::time).thenComparingInt(State::transfers));
        for (RouteNode source : sources) {
            bestTime.put(source, 0);
            bestTransfers.put(source, 0);
            queue.add(new State(source, 0, 0));
        }

        while (!queue.isEmpty()) {
            State current = queue.poll();
            if (!settled.add(current.node())) {
                continue; // узел уже финализирован с лучшей оценкой
            }
            if (current.node().stationCode().equals(targetStation)) {
                return reconstruct(previous, current.node());
            }
            for (Edge edge : adjacency.getOrDefault(current.node(), List.of())) {
                if (settled.contains(edge.to())) {
                    continue;
                }
                int time = current.time() + edge.weight();
                int transfers = current.transfers() + (edge.transfer() ? 1 : 0);
                if (isBetter(time, transfers, bestTime.get(edge.to()), bestTransfers.get(edge.to()))) {
                    bestTime.put(edge.to(), time);
                    bestTransfers.put(edge.to(), transfers);
                    previous.put(edge.to(), current.node());
                    queue.add(new State(edge.to(), time, transfers));
                }
            }
        }
        return List.of();
    }

    /** Лексикографическое сравнение (время, пересадки); {@code null} = ещё не достигнут. */
    private static boolean isBetter(int time, int transfers, Integer bestTime, Integer bestTransfers) {
        if (bestTime == null) {
            return true;
        }
        return time < bestTime || (time == bestTime && transfers < bestTransfers);
    }

    private static List<RouteNode> reconstruct(Map<RouteNode, RouteNode> previous, RouteNode target) {
        LinkedList<RouteNode> path = new LinkedList<>();
        RouteNode node = target;
        while (node != null) {
            path.addFirst(node);
            node = previous.get(node);
        }
        return path;
    }

    // ---------------------------------------------------------------------
    // Сборка ответа
    // ---------------------------------------------------------------------

    private RouteDto buildRoute(String fromCode, String toCode, List<RouteNode> path, Graph graph) {
        // разбиение пути на участки по линиям: границы — рёбра-пересадки (смена линии)
        List<List<RouteNode>> legGroups = new ArrayList<>();
        List<RouteNode> currentGroup = new ArrayList<>();
        String currentLine = null;
        for (RouteNode node : path) {
            if (currentLine == null || node.lineCode().equals(currentLine)) {
                currentGroup.add(node);
            } else {
                legGroups.add(currentGroup);
                currentGroup = new ArrayList<>();
                currentGroup.add(node);
            }
            currentLine = node.lineCode();
        }
        if (!currentGroup.isEmpty()) {
            legGroups.add(currentGroup);
        }

        List<RouteLegDto> legs = new ArrayList<>();
        int totalSegments = 0;
        int rideMinutes = 0;
        for (List<RouteNode> group : legGroups) {
            String lineCode = group.getFirst().lineCode();
            List<String> stationCodes = group.stream().map(RouteNode::stationCode).toList();
            int segments = stationCodes.size() - 1;
            int minutes = segments * SEGMENT_MINUTES;
            totalSegments += segments;
            rideMinutes += minutes;
            legs.add(new RouteLegDto(lineCode, lineName(graph, lineCode), lineColor(graph, lineCode),
                    stationCodes, segments, minutes));
        }
        int transfers = legGroups.size() - 1;
        int estimatedMinutes = rideMinutes + transfers * TRANSFER_MINUTES;

        // плоская последовательность станций; пересадочный узел — один раз, помечен transfer
        List<RouteStopDto> stops = new ArrayList<>();
        for (int li = 0; li < legGroups.size(); li++) {
            List<RouteNode> group = legGroups.get(li);
            for (int j = 0; j < group.size(); j++) {
                if (li > 0 && j == 0) {
                    continue; // станция пересадки уже добавлена как терминал предыдущего участка
                }
                RouteNode node = group.get(j);
                boolean transfer = li < legGroups.size() - 1 && j == group.size() - 1;
                stops.add(stop(graph, node.stationCode(), node.lineCode(), transfer));
            }
        }

        return new RouteDto(fromCode, toCode, true, estimatedMinutes, transfers, totalSegments, legs, stops);
    }

    private RouteDto noRoute(String fromCode, String toCode) {
        return new RouteDto(fromCode, toCode, false, 0, 0, 0, List.of(), List.of());
    }

    private RouteStopDto stop(Graph graph, String stationCode, String lineCode, boolean transfer) {
        MetroStation station = graph.stationsByCode().get(stationCode);
        Map<String, String> name = station != null ? station.getNameI18n() : Map.of();
        return new RouteStopDto(stationCode, name, lineCode, transfer);
    }

    private static Map<String, String> lineName(Graph graph, String lineCode) {
        MetroLine line = graph.linesByCode().get(lineCode);
        return line != null ? line.getNameI18n() : Map.of();
    }

    private static String lineColor(Graph graph, String lineCode) {
        MetroLine line = graph.linesByCode().get(lineCode);
        return line != null ? line.getColorHex() : null;
    }

    private static NotFoundException routeStationNotFound(String stationCode) {
        return new NotFoundException("route.station_not_found",
                "Станция с кодом '" + stationCode + "' не найдена");
    }

    private static boolean isClosedStation(String status) {
        return CLOSED_STATION_STATUSES.contains(status);
    }

    private static boolean isClosedLine(String status) {
        return CLOSED_LINE_STATUSES.contains(status);
    }

    // ---------------------------------------------------------------------
    // Внутренние типы графа
    // ---------------------------------------------------------------------

    /** Узел графа: платформа линии на станции. */
    private record RouteNode(String stationCode, String lineCode) {
    }

    /** Ребро: перегон ({@code transfer=false}) или пересадка ({@code transfer=true}). */
    private record Edge(RouteNode to, int weight, boolean transfer) {
    }

    /** Состояние в очереди Дейкстры. */
    private record State(RouteNode node, int time, int transfers) {
    }

    /** Собранный граф сети и справочники для сборки ответа. */
    private record Graph(Map<RouteNode, List<Edge>> adjacency,
                         Map<String, MetroStation> stationsByCode,
                         Map<String, MetroLine> linesByCode,
                         Map<String, List<String>> openLinesByStation) {
    }
}
