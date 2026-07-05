package tj.metro.dushanbe.network.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import tj.metro.dushanbe.network.domain.MetroLine;

/**
 * Доступ к линиям метрополитена.
 */
public interface MetroLineRepository extends JpaRepository<MetroLine, UUID> {

    Optional<MetroLine> findByCode(String code);

    boolean existsByCode(String code);

    List<MetroLine> findAllByOrderBySortOrderAscCodeAsc();

    List<MetroLine> findByStatusOrderBySortOrderAscCodeAsc(String status);

    // --- Публичное чтение: только действующие линии (deleted_at IS NULL, BR-NET-2). ---

    /** Действующая линия по коду; soft-deleted линия трактуется как отсутствующая. */
    Optional<MetroLine> findByCodeAndDeletedAtIsNull(String code);

    /** Все действующие линии в штатном порядке (для публичного списка/GeoJSON). */
    List<MetroLine> findByDeletedAtIsNullOrderBySortOrderAscCodeAsc();

    /** Действующие линии заданного статуса в штатном порядке. */
    List<MetroLine> findByStatusAndDeletedAtIsNullOrderBySortOrderAscCodeAsc(String status);
}
