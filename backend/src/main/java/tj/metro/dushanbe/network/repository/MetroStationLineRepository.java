package tj.metro.dushanbe.network.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import tj.metro.dushanbe.network.domain.MetroStationLine;
import tj.metro.dushanbe.network.domain.MetroStationLineId;

/**
 * Доступ к связям станция-линия.
 */
public interface MetroStationLineRepository extends JpaRepository<MetroStationLine, MetroStationLineId> {

    /** Все связи с жадной загрузкой станции и линии (сеть небольшая). */
    @Query("select sl from MetroStationLine sl join fetch sl.station join fetch sl.line")
    List<MetroStationLine> findAllWithStationAndLine();
}
