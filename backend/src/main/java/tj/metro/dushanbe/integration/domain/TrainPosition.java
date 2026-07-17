package tj.metro.dushanbe.integration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.locationtech.jts.geom.Point;

/**
 * Замер положения поезда от GPS/диспетчерской платформы (U-INT-04).
 *
 * <p>Запись неизменяема: методов мутации и {@code updatedAt} здесь нет. Это точка
 * потока телеметрии, а не карточка объекта — «поезд сдвинулся» означает новый
 * замер, а не правку старого. Перезапись last-known-position одной строкой на
 * поезд стоила бы истории движения, по которой только и можно разобрать, где
 * поезд встал и почему опоздание накопилось.
 *
 * <p>Стабильного {@code code} нет по той же причине: на отдельный замер не ходит
 * ни один REST-путь, читается всегда срез «где поезда линии сейчас».
 */
@Entity
@Table(name = "train_position")
public class TrainPosition {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "train_code", nullable = false, length = 64)
    private String trainCode;

    @Column(name = "line_code", nullable = false, length = 64)
    private String lineCode;

    /** Последняя пройденная станция; null, если поезд ещё в депо/на перегоне. */
    @Column(name = "station_code", length = 64)
    private String stationCode;

    @Column(name = "next_station_code", length = 64)
    private String nextStationCode;

    /** Точка в EPSG:4326 — как metro_station.point_geom (порядок осей [lon, lat]). */
    @Column(name = "geom", nullable = false)
    private Point geom;

    /** Курс в градусах [0, 360). */
    @Column(name = "heading")
    private Integer heading;

    @Column(name = "speed_kmh")
    private BigDecimal speedKmh;

    /** Отставание от расписания; отрицательное значение = идёт с опережением. */
    @Column(name = "delay_seconds")
    private Integer delaySeconds;

    /** low|medium|high|full (chk_train_position_occupancy). */
    @Column(name = "occupancy", length = 16)
    private String occupancy;

    /** Время замера по часам платформы. */
    @Column(name = "reported_at", nullable = false)
    private OffsetDateTime reportedAt;

    /** Время приёма замера у нас; разница с reportedAt — лаг телеметрии. */
    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    protected TrainPosition() {
    }

    public TrainPosition(UUID id, String trainCode, String lineCode, String stationCode,
                         String nextStationCode, Point geom, Integer heading, BigDecimal speedKmh,
                         Integer delaySeconds, String occupancy, OffsetDateTime reportedAt,
                         OffsetDateTime receivedAt) {
        this.id = id;
        this.trainCode = trainCode;
        this.lineCode = lineCode;
        this.stationCode = stationCode;
        this.nextStationCode = nextStationCode;
        this.geom = geom;
        this.heading = heading;
        this.speedKmh = speedKmh;
        this.delaySeconds = delaySeconds;
        this.occupancy = occupancy;
        this.reportedAt = reportedAt;
        this.receivedAt = receivedAt;
    }

    /**
     * Лаг телеметрии в секундах: насколько замер устарел к моменту приёма.
     * Признак деградации GPS-платформы — ради него reported_at и received_at
     * и хранятся раздельно.
     */
    public long lagSeconds() {
        return java.time.Duration.between(reportedAt, receivedAt).getSeconds();
    }

    public UUID getId() { return id; }
    public String getTrainCode() { return trainCode; }
    public String getLineCode() { return lineCode; }
    public String getStationCode() { return stationCode; }
    public String getNextStationCode() { return nextStationCode; }
    public Point getGeom() { return geom; }
    public Integer getHeading() { return heading; }
    public BigDecimal getSpeedKmh() { return speedKmh; }
    public Integer getDelaySeconds() { return delaySeconds; }
    public String getOccupancy() { return occupancy; }
    public OffsetDateTime getReportedAt() { return reportedAt; }
    public OffsetDateTime getReceivedAt() { return receivedAt; }
}
