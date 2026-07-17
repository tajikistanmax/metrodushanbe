package tj.metro.dushanbe.notification.repository;

import java.util.Collection;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tj.metro.dushanbe.notification.domain.DeliveryStatus;
import tj.metro.dushanbe.notification.domain.NotificationDelivery;

/** Доступ к фактам доставки (NTF-06). */
public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, UUID> {

    /**
     * Доставки одной рассылки страницей — под ix_notification_delivery_message.
     *
     * <p>{@code join fetch d.message} обязателен: DTO доставки печатает код
     * рассылки, а ленивая ссылка дала бы запрос на строку страницы (N+1).
     * С пагинацией такой fetch join совместим — в отличие от коллекции,
     * одиночная ссылка строки не размножает, и LIMIT считает БД, а не память.
     */
    @Query(value = """
            select d from NotificationDelivery d
            join fetch d.message m
            where m.id = :messageId
            """,
            countQuery = """
                    select count(d) from NotificationDelivery d
                    where d.message.id = :messageId
                    """)
    Page<NotificationDelivery> findByMessageIdWithMessage(@Param("messageId") UUID messageId,
                                                          Pageable pageable);

    /**
     * Очередь проблемных доставок (OPS-04) страницей: вызывается с pending/failed —
     * ровно под частичный индекс ix_notification_delivery_failed. Про {@code join
     * fetch} — см. {@link #findByMessageIdWithMessage}.
     */
    @Query(value = """
            select d from NotificationDelivery d
            join fetch d.message
            where d.status in :statuses
            """,
            countQuery = """
                    select count(d) from NotificationDelivery d
                    where d.status in :statuses
                    """)
    Page<NotificationDelivery> findByStatusInWithMessage(
            @Param("statuses") Collection<DeliveryStatus> statuses, Pageable pageable);

    long countByMessageId(UUID messageId);
}
