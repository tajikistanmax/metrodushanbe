package tj.metro.dushanbe.integration.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tj.metro.dushanbe.common.error.BadRequestException;
import tj.metro.dushanbe.common.error.NotFoundException;
import tj.metro.dushanbe.integration.domain.TrainPosition;
import tj.metro.dushanbe.integration.repository.TrainPositionRepository;
import tj.metro.dushanbe.integration.web.dto.TrainPositionDto;
import tj.metro.dushanbe.integration.web.dto.TrainPositionReportRequest;
import tj.metro.dushanbe.network.repository.MetroLineRepository;

/**
 * Приём и выдача realtime-телеметрии положения поездов (U-INT-04).
 *
 * <p><b>Почему здесь нет {@code @Cacheable} — и не должно быть.</b> Позиции
 * меняются каждые несколько секунд; это буквально то, ради чего эндпоинт и
 * существует. Кэш с любым осмысленным TTL показывал бы поезд там, где его уже
 * нет, — то есть подменял бы «где поезд сейчас» на «где он был». Даже TTL в 5
 * секунд не спасает: он не ускоряет (запрос и так один индексный проход по
 * ix_train_position_line_reported), но добавляет окно, в котором данные заведомо
 * врут, и делает поведение невоспроизводимым при разборе жалоб. Кэш здесь не
 * оптимизация, а источник неверных данных — поэтому в CacheConfig под телеметрию
 * ничего регистрировать не нужно.
 */
@Service
public class TelemetryService {

    /** Совпадает с chk_train_position_occupancy — иначе БД отвергнет замер. */
    private static final Set<String> OCCUPANCY = Set.of("low", "medium", "high", "full");

    /** Фабрика геометрий в EPSG:4326, порядок осей [lon, lat] — как в AdminSupport. */
    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), 4326);

    private final TrainPositionRepository repository;
    private final MetroLineRepository lineRepository;
    private final Clock clock;

    public TelemetryService(TrainPositionRepository repository,
                            MetroLineRepository lineRepository,
                            Clock clock) {
        this.repository = repository;
        this.lineRepository = lineRepository;
        this.clock = clock;
    }

    /**
     * Принимает замер от GPS/диспетчерской платформы.
     *
     * <p>{@code receivedAt} проставляем мы, {@code reportedAt} приходит извне и
     * НЕ подменяется на «сейчас», даже если он в прошлом: расхождение этих отметок
     * — единственный признак того, что платформа отстала (см. V024).
     */
    @Transactional
    public TrainPositionDto report(TrainPositionReportRequest request) {
        requireKnownLine(request.lineCode());
        requireOccupancy(request.occupancy());
        Point geom = point(request.coordinates());
        OffsetDateTime reportedAt = request.reportedAt().atOffset(ZoneOffset.UTC);
        TrainPosition position = new TrainPosition(UUID.randomUUID(), request.trainCode().trim(),
                request.lineCode().trim(), blankToNull(request.stationCode()),
                blankToNull(request.nextStationCode()), geom, request.heading(), request.speedKmh(),
                request.delaySeconds(), blankToNull(request.occupancy()), reportedAt,
                OffsetDateTime.now(clock));
        return toDto(repository.save(position));
    }

    /** Срез «где поезда линии сейчас»: по одному свежему замеру на поезд. */
    @Transactional(readOnly = true)
    public List<TrainPositionDto> currentPositions(String lineCode) {
        String normalized = requireKnownLine(lineCode);
        return repository.findLatestByLine(normalized).stream()
                .map(TelemetryService::toDto)
                .toList();
    }

    /**
     * Линия обязана существовать: иначе телеметрия молча копилась бы под опечаткой
     * в коде линии, и «поездов на линии нет» выглядело бы как отсутствие движения,
     * а не как ошибка интеграции.
     */
    private String requireKnownLine(String lineCode) {
        String normalized = blankToNull(lineCode);
        if (normalized == null) {
            throw new BadRequestException("telemetry.line_required",
                    "Код линии обязателен", Map.of("field", "lineCode"));
        }
        if (lineRepository.findByCodeAndDeletedAtIsNull(normalized).isEmpty()) {
            throw NotFoundException.line(normalized);
        }
        return normalized;
    }

    private static void requireOccupancy(String occupancy) {
        String normalized = blankToNull(occupancy);
        if (normalized != null && !OCCUPANCY.contains(normalized)) {
            throw new BadRequestException("telemetry.occupancy_invalid",
                    "Недопустимая заполненность: " + normalized,
                    Map.of("field", "occupancy", "allowed", OCCUPANCY.stream().sorted().toList()));
        }
    }

    /**
     * Точка [lon, lat] в EPSG:4326.
     *
     * <p>{@code AdminSupport.point(...)} делает ровно это, но живёт в
     * {@code admin.service} с package-private видимостью и потому недоступен
     * отсюда; расширять его видимость — трогать чужой файл. Вдобавок здесь нужна
     * проверка диапазонов, которой у него нет: сбоящий GPS присылает не мусорную
     * структуру, а правдоподобную пару чисел вне Земли, и её обязан отсечь именно
     * приёмник телеметрии. Коды ошибок оставлены доменными ({@code telemetry.*}),
     * чтобы интегратор по коду понимал, что чинить у себя.
     */
    private static Point point(List<Double> coordinates) {
        if (coordinates == null || coordinates.size() != 2
                || coordinates.get(0) == null || coordinates.get(1) == null) {
            throw new BadRequestException("telemetry.coordinates_invalid",
                    "Координаты точки должны быть массивом [lon, lat]",
                    Map.of("field", "coordinates", "expected", "[lon, lat]"));
        }
        double lon = coordinates.get(0);
        double lat = coordinates.get(1);
        if (lon < -180 || lon > 180 || lat < -90 || lat > 90) {
            throw new BadRequestException("telemetry.coordinates_invalid",
                    "Координаты вне допустимого диапазона (lon ±180, lat ±90)",
                    Map.of("field", "coordinates", "lon", lon, "lat", lat));
        }
        return GEOMETRY_FACTORY.createPoint(new Coordinate(lon, lat));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static TrainPositionDto toDto(TrainPosition position) {
        return new TrainPositionDto(
                position.getTrainCode(),
                position.getLineCode(),
                position.getStationCode(),
                position.getNextStationCode(),
                List.of(position.getGeom().getX(), position.getGeom().getY()),
                position.getHeading(),
                position.getSpeedKmh(),
                position.getDelaySeconds(),
                position.getOccupancy(),
                position.getReportedAt().toInstant(),
                position.getReceivedAt().toInstant(),
                position.lagSeconds());
    }
}
