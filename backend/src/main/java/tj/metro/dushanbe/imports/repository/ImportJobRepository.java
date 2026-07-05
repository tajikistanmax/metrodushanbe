package tj.metro.dushanbe.imports.repository;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import tj.metro.dushanbe.imports.domain.ImportJob;

/**
 * Доступ к заданиям импорта. Лента джобов — по индексу {@code ix_import_job_created_at} (V010).
 */
public interface ImportJobRepository extends JpaRepository<ImportJob, UUID> {

    /** Постраничная лента джобов (сортировка задаётся {@link Pageable}). */
    Page<ImportJob> findAllBy(Pageable pageable);
}
