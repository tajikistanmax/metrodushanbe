package tj.metro.dushanbe.imports.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import tj.metro.dushanbe.imports.domain.ImportError;

/**
 * Доступ к ошибкам импорта (IMP-03). Выборка по джобу — индекс {@code ix_import_error_job} (V010).
 */
public interface ImportErrorRepository extends JpaRepository<ImportError, UUID> {

    /** Ошибки конкретного джоба в порядке фиксации. */
    List<ImportError> findByJob_IdOrderByCreatedAtAsc(UUID jobId);
}
