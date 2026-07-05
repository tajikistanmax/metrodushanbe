package tj.metro.dushanbe.network.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tj.metro.dushanbe.network.domain.MetroStationLine;
import tj.metro.dushanbe.network.domain.MetroStationLineId;

/**
 * Доступ к связям станция-линия.
 */
public interface MetroStationLineRepository extends JpaRepository<MetroStationLine, MetroStationLineId> {

    /** Все связи с жадной загрузкой станции и линии (сеть небольшая). */
    @Query("select sl from MetroStationLine sl join fetch sl.station join fetch sl.line")
    List<MetroStationLine> findAllWithStationAndLine();

    /**
     * Связи только для действующих станций и линий (deleted_at IS NULL, BR-NET-2) —
     * для публичного чтения: список станций, GeoJSON, граф маршрутизации.
     */
    @Query("""
            select sl from MetroStationLine sl
            join fetch sl.station join fetch sl.line
            where sl.station.deletedAt is null and sl.line.deletedAt is null
            """)
    List<MetroStationLine> findAllActiveWithStationAndLine();

    /**
     * Коды линий, которым принадлежит станция с данным стабильным кодом.
     * Неизвестный код станции — пустой список.
     */
    @Query("select sl.line.code from MetroStationLine sl where sl.station.code = :stationCode")
    List<String> findLineCodesByStationCode(@Param("stationCode") String stationCode);
}
