package tj.metro.dushanbe.network.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tj.metro.dushanbe.network.domain.MetroStation;

/**
 * Доступ к станциям метрополитена.
 */
public interface MetroStationRepository extends JpaRepository<MetroStation, UUID> {

    Optional<MetroStation> findByCode(String code);

    boolean existsByCode(String code);

    List<MetroStation> findAllByOrderByCodeAsc();

    /** Станции указанной линии в порядке следования вдоль неё. */
    @Query("""
            select sl.station from MetroStationLine sl
            where sl.line.code = :lineCode
            order by sl.positionIndex asc
            """)
    List<MetroStation> findByLineCodeOrderByPosition(@Param("lineCode") String lineCode);
}
