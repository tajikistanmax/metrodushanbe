package tj.metro.dushanbe.network.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tj.metro.dushanbe.network.domain.StationExit;

/**
 * Доступ к выходам станций.
 */
public interface StationExitRepository extends JpaRepository<StationExit, UUID> {

    /** Выход по стабильному коду (редакционные операции). */
    Optional<StationExit> findByCode(String code);

    boolean existsByCode(String code);

    /**
     * Выходы станции с данным стабильным кодом в порядке вывода в карточке.
     * Неизвестный код станции — пустой список.
     */
    @Query("""
            select e from StationExit e
            where e.station.code = :stationCode
            order by e.sortOrder asc, e.code asc
            """)
    List<StationExit> findByStationCodeOrderBySortOrder(@Param("stationCode") String stationCode);
}
