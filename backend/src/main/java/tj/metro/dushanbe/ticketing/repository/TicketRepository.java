package tj.metro.dushanbe.ticketing.repository;

import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tj.metro.dushanbe.ticketing.domain.Ticket;
import tj.metro.dushanbe.ticketing.domain.TicketStatus;

/** Доступ к выпущенным билетам (TKT-04). */
public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    Optional<Ticket> findByCode(String code);

    boolean existsByCode(String code);

    /**
     * Горячий путь турникета: билет ищется строго по хешу токена — самого токена
     * у нас нет (см. Ticket).
     */
    Optional<Ticket> findByTokenHash(String tokenHash);

    /** Serializes turnstile validation for the same token. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ticket from Ticket ticket where ticket.tokenHash = :tokenHash")
    Optional<Ticket> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    /** Serializes financial state changes without locking ordinary reads. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ticket from Ticket ticket where ticket.code = :code")
    Optional<Ticket> findByCodeForUpdate(@Param("code") String code);

    List<Ticket> findAllByOrderByCreatedAtDesc();

    List<Ticket> findByStatusOrderByCreatedAtDesc(TicketStatus status);

    /** Все билеты покупателя — нужны при блокировке его самого (TKT-06). */
    List<Ticket> findByRiderRef(String riderRef);

    /**
     * Антифрод (TKT-06): сколько билетов этот покупатель купил за окно.
     * Опирается на частичный индекс ix_ticket_rider_recent.
     */
    long countByRiderRefAndCreatedAtGreaterThanEqual(String riderRef, OffsetDateTime from);
}
