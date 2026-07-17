package tj.metro.dushanbe.identity.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import tj.metro.dushanbe.identity.domain.AdminRole;
import tj.metro.dushanbe.identity.domain.AdminUser;

/** Доступ к операторам консоли. Логин всегда нормализован в нижний регистр. */
public interface AdminUserRepository extends JpaRepository<AdminUser, UUID> {

    Optional<AdminUser> findByUsername(String username);

    boolean existsByUsername(String username);

    List<AdminUser> findAllByOrderByRoleAscUsernameAsc();

    /** Счётчик активных обладателей роли — гейт «последний суперадмин». */
    long countByRoleAndActiveIsTrue(AdminRole role);
}
