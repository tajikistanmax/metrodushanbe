package tj.metro.dushanbe.notification.repository;

import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tj.metro.dushanbe.notification.domain.NotificationMessage;
import tj.metro.dushanbe.notification.domain.NotificationStatus;

/** Доступ к рассылкам (NTF-01…06). */
public interface NotificationMessageRepository extends JpaRepository<NotificationMessage, UUID> {

    Optional<NotificationMessage> findByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select message from NotificationMessage message where message.code = :code")
    Optional<NotificationMessage> findByCodeForUpdate(@Param("code") String code);

    boolean existsByCode(String code);

    @Query(value = """
            SELECT *
              FROM notification_message
             WHERE status = 'scheduled'
               AND scheduled_at <= :now
             ORDER BY scheduled_at, id
             LIMIT 1
             FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<NotificationMessage> findNextDueForUpdate(@Param("now") OffsetDateTime now);

    /**
     * ШАГ 1 постраничной ленты консоли: страница ИДЕНТИФИКАТОРОВ, без таргетов.
     *
     * <p>Почему шага два, а не один. Fetch join коллекции вместе с {@link Pageable}
     * даёт HHH90003004: строк в результате больше, чем рассылок (по одной на таргет),
     * LIMIT в SQL резал бы таргеты, а не рассылки, — поэтому Hibernate снимает LIMIT
     * и режет страницу в памяти, вычитывая всю таблицу. Пагинация тогда фиктивная и
     * при этом дороже прежней полной выдачи. Здесь коллекции нет вовсе, строка ровно
     * одна на рассылку — LIMIT/OFFSET честно уходят в SQL.
     *
     * <p>Порядок задаёт {@link Pageable} и он ОБЯЗАН заканчиваться уникальным полем
     * (см. {@code AdminSupport.pageable}): {@code created_at} у рассылок, созданных
     * одним заходом, совпадает до миллисекунды.
     */
    @Query(value = "select m.id from NotificationMessage m",
            countQuery = "select count(m) from NotificationMessage m")
    Page<UUID> findIdPage(Pageable pageable);

    /** ШАГ 1 с фильтром {@code ?status=}; опирается на ix_notification_message_status_created. */
    @Query(value = "select m.id from NotificationMessage m where m.status = :status",
            countQuery = "select count(m) from NotificationMessage m where m.status = :status")
    Page<UUID> findIdPageByStatus(@Param("status") NotificationStatus status, Pageable pageable);

    /**
     * ШАГ 2: рассылки страницы вместе с таргетами (fetch join — без N+1).
     *
     * <p>Id уже отобраны шагом 1, поэтому размножение строк fetch join-ом безвредно:
     * режется здесь не страница, а заведомо ограниченный набор. Порядок повторён
     * явно — {@code in} его не сохраняет, и без {@code order by} страница пришла бы
     * в произвольном порядке.
     */
    @Query("""
            select distinct m from NotificationMessage m
            left join fetch m.targets
            where m.id in :ids
            order by m.createdAt desc, m.code asc
            """)
    List<NotificationMessage> findAllWithTargetsByIdIn(@Param("ids") Collection<UUID> ids);

    /**
     * Рассылки в заданном состоянии вместе с таргетами (fetch join — без N+1),
     * новые сверху. Нужна и публичному фиду (он фильтрует по таргетам каждую
     * рассылку), и ленте консоли с фильтром {@code ?status=} — опирается на
     * ix_notification_message_status_created.
     */
    @Query("""
            select distinct m from NotificationMessage m
            left join fetch m.targets
            where m.status = :status
            order by m.createdAt desc
            """)
    List<NotificationMessage> findByStatusWithTargets(@Param("status") NotificationStatus status);
}
