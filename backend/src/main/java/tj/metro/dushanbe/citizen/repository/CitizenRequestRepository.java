package tj.metro.dushanbe.citizen.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import tj.metro.dushanbe.citizen.domain.CitizenRequest;

public interface CitizenRequestRepository extends JpaRepository<CitizenRequest, UUID> {

    Optional<CitizenRequest> findByPublicCode(String publicCode);

    boolean existsByPublicCode(String publicCode);

    List<CitizenRequest> findAllByOrderByCreatedAtDesc();

    List<CitizenRequest> findByStatusOrderByCreatedAtDesc(String status);
}
