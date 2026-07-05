package tj.metro.dushanbe.network.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tj.metro.dushanbe.network.domain.AccessibilityFeature;

/**
 * Доступ к объектам доступности станций.
 */
public interface AccessibilityFeatureRepository extends JpaRepository<AccessibilityFeature, UUID> {

    /**
     * Объекты доступности станции с данным стабильным кодом, упорядоченные по типу.
     * Неизвестный код станции — пустой список.
     */
    @Query("""
            select f from AccessibilityFeature f
            where f.station.code = :stationCode
            order by f.type asc
            """)
    List<AccessibilityFeature> findByStationCodeOrderByType(@Param("stationCode") String stationCode);
}
