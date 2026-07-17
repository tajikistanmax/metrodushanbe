package tj.metro.dushanbe.ticketing.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import tj.metro.dushanbe.ticketing.domain.BlocklistSubjectType;
import tj.metro.dushanbe.ticketing.domain.TicketBlocklistEntry;

/** Доступ к чёрному списку (TKT-06). */
public interface TicketBlocklistRepository extends JpaRepository<TicketBlocklistEntry, UUID> {

    Optional<TicketBlocklistEntry> findByCode(String code);

    boolean existsByCode(String code);

    /** Горячая проверка при валидации и покупке; опирается на uq_ticket_blocklist_subject. */
    boolean existsBySubjectTypeAndSubjectCode(BlocklistSubjectType subjectType, String subjectCode);

    Optional<TicketBlocklistEntry> findBySubjectTypeAndSubjectCode(BlocklistSubjectType subjectType,
                                                                   String subjectCode);

    List<TicketBlocklistEntry> findAllByOrderByCreatedAtDesc();

    List<TicketBlocklistEntry> findBySubjectTypeOrderByCreatedAtDesc(BlocklistSubjectType subjectType);
}
