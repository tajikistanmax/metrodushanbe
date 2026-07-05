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

    // --- Публичное чтение: только действующие станции (deleted_at IS NULL, BR-NET-2). ---

    /** Действующая станция по коду; soft-deleted станция трактуется как отсутствующая. */
    Optional<MetroStation> findByCodeAndDeletedAtIsNull(String code);

    /** Все действующие станции по коду (для публичного списка/GeoJSON). */
    List<MetroStation> findByDeletedAtIsNullOrderByCodeAsc();

    /**
     * Действующие станции действующей линии в порядке следования вдоль неё:
     * исключаются как soft-deleted станции, так и станции soft-deleted линии.
     */
    @Query("""
            select sl.station from MetroStationLine sl
            where sl.line.code = :lineCode
              and sl.station.deletedAt is null
              and sl.line.deletedAt is null
            order by sl.positionIndex asc
            """)
    List<MetroStation> findActiveByLineCodeOrderByPosition(@Param("lineCode") String lineCode);
}
