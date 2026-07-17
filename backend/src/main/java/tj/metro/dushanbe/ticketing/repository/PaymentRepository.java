package tj.metro.dushanbe.ticketing.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import tj.metro.dushanbe.ticketing.domain.Payment;
import tj.metro.dushanbe.ticketing.domain.PaymentKind;
import tj.metro.dushanbe.ticketing.domain.PaymentStatus;

/** Доступ к платежам (TKT-05). */
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByCode(String code);

    boolean existsByCode(String code);

    List<Payment> findByTicketCodeOrderByCreatedAtDesc(String ticketCode);

    List<Payment> findAllByOrderByCreatedAtDesc();

    List<Payment> findByStatusOrderByCreatedAtDesc(PaymentStatus status);

    /** Платёж-покупка билета — тот, который возвращают при refund (TKT-03). */
    Optional<Payment> findFirstByTicketCodeAndKindAndStatus(String ticketCode, PaymentKind kind,
                                                            PaymentStatus status);

    /** Locks the refundable purchase after its ticket has been locked. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Payment> findFirstByTicketCodeAndKindAndStatusOrderByCreatedAtDesc(
            String ticketCode, PaymentKind kind, PaymentStatus status);
}
