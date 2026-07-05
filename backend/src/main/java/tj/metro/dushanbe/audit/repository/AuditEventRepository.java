package tj.metro.dushanbe.audit.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import tj.metro.dushanbe.audit.domain.AuditEvent;

/**
 * Доступ к журналу аудита. Append-only: используются только вставка (наследованный
 * {@code save}) и выборки; методов изменения/удаления в контракте нет.
 * Выборки опираются на индексы {@code ix_audit_event_at} и {@code ix_audit_event_entity} (V009).
 */
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    /** Общая лента аудита с пагинацией (сортировка задаётся {@link Pageable}). */
    Page<AuditEvent> findAllBy(Pageable pageable);

    /** История изменений конкретной сущности, новые сверху. */
    List<AuditEvent> findByEntityTypeAndEntityIdOrderByAtDesc(String entityType, String entityId);
}
