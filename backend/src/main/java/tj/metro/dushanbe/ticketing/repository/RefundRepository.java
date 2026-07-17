package tj.metro.dushanbe.ticketing.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import tj.metro.dushanbe.ticketing.domain.Refund;

/** Доступ к возвратам (TKT-03). */
public interface RefundRepository extends JpaRepository<Refund, UUID> {

    Optional<Refund> findByCode(String code);

    boolean existsByCode(String code);

    List<Refund> findByPaymentCodeOrderByCreatedAtDesc(String paymentCode);

    List<Refund> findAllByOrderByCreatedAtDesc();
}
