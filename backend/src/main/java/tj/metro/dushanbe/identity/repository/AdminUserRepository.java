package tj.metro.dushanbe.identity.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tj.metro.dushanbe.identity.domain.AdminRole;
import tj.metro.dushanbe.identity.domain.AdminUser;

/** Доступ к операторам консоли. Логин всегда нормализован в нижний регистр. */
public interface AdminUserRepository extends JpaRepository<AdminUser, UUID> {

    Optional<AdminUser> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByUsernameAndActiveIsTrue(String username);

    List<AdminUser> findAllByOrderByRoleAscUsernameAsc();

    /** Счётчик активных обладателей роли — гейт «последний суперадмин». */
    long countByRoleAndActiveIsTrue(AdminRole role);

    /**
     * Locks every active holder of the role in a stable order. This serializes concurrent
     * demotions/deletions so two transactions cannot both remove the last superadmin.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from AdminUser user "
            + "where user.role = :role and user.active = true order by user.username")
    List<AdminUser> findActiveByRoleForUpdate(@Param("role") AdminRole role);
}
