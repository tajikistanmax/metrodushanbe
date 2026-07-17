package tj.metro.dushanbe.incident.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import tj.metro.dushanbe.incident.domain.Incident;
import tj.metro.dushanbe.incident.domain.IncidentStatus;

/** Доступ к инцидентам операционного контура. */
public interface IncidentRepository extends JpaRepository<Incident, UUID> {

    Optional<Incident> findByCode(String code);

    boolean existsByCode(String code);

    List<Incident> findAllByOrderByOccurredAtDesc();

    List<Incident> findByStatusOrderByOccurredAtDesc(IncidentStatus status);

    /** Счётчик для дашборда: сколько инцидентов зарегистрировано за окно. */
    long countByOccurredAtGreaterThanEqual(OffsetDateTime from);

    long countByStatusIn(List<IncidentStatus> statuses);

    /**
     * Разрез «инциденты за окно по категориям» для плиток дашборда.
     * Возвращает пары [категория, количество]; отсутствующие категории в выдачу
     * не попадают — нули достраивает сервис.
     */
    @Query("""
            SELECT i.category, COUNT(i)
            FROM Incident i
            WHERE i.occurredAt >= :from
            GROUP BY i.category
            """)
    List<Object[]> countByCategorySince(OffsetDateTime from);

    /** Максимальный порядковый номер года — для генерации кода INC-YYYY-NNNN. */
    @Query("""
            SELECT MAX(i.code)
            FROM Incident i
            WHERE i.code LIKE :prefix
            """)
    Optional<String> findMaxCodeWithPrefix(String prefix);

}
