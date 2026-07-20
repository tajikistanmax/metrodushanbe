package tj.metro.dushanbe.identity.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tj.metro.dushanbe.identity.domain.LoginAttempt;
import tj.metro.dushanbe.identity.domain.LoginAttemptScope;

/**
 * Счётчики неудачных входов (аудит-пункт 9).
 *
 * <p>Пара {@code (scope, subject)} уникальна по БД. Чтение под пессимистичной
 * блокировкой сериализует параллельные попытки входа по одному ключу: без него два
 * одновременных запроса нарастили бы счётчик каждый по своей копии строки и порог
 * сработал бы с опозданием.
 */
public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select attempt from LoginAttempt attempt "
            + "where attempt.scope = :scope and attempt.subject = :subject")
    Optional<LoginAttempt> findForUpdate(@Param("scope") LoginAttemptScope scope,
                                         @Param("subject") String subject);

    Optional<LoginAttempt> findByScopeAndSubject(LoginAttemptScope scope, String subject);
}
