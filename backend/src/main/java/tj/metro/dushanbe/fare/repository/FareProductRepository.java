package tj.metro.dushanbe.fare.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import tj.metro.dushanbe.fare.domain.FareProduct;

public interface FareProductRepository extends JpaRepository<FareProduct, UUID> {

    List<FareProduct> findByActiveTrueOrderByAmountAscCodeAsc();

    List<FareProduct> findAllByOrderByAmountAscCodeAsc();

    Optional<FareProduct> findByCode(String code);

    boolean existsByCode(String code);
}
