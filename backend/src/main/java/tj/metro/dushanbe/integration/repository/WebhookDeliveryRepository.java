package tj.metro.dushanbe.integration.repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import tj.metro.dushanbe.integration.domain.WebhookDelivery;
import tj.metro.dushanbe.integration.domain.WebhookDeliveryStatus;

/** Доступ к доставкам вебхуков (INT-05, U-OPS-04). */
public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {

    Optional<WebhookDelivery> findByCode(String code);

    Optional<WebhookDelivery> findByEventIdAndSubscriptionCode(UUID eventId, String subscriptionCode);

    /**
     * Очередь ошибок оператора (U-OPS-04) страницей; порядок задаёт {@link Pageable}.
     * Целиком очередь не отдаётся: она растёт как события × подписчики, и полная
     * выдача на проде — это выборка всей таблицы на каждый заход в консоль.
     */
    Page<WebhookDelivery> findByStatusIn(Collection<WebhookDeliveryStatus> statuses, Pageable pageable);

    Page<WebhookDelivery> findByStatus(WebhookDeliveryStatus status, Pageable pageable);

    List<WebhookDelivery> findBySubscriptionCodeOrderByUpdatedAtDesc(String subscriptionCode);

    /** Сколько раз мы уже сходили к подписчику за окно — учёт rate_limit_per_minute. */
    long countBySubscriptionCodeAndUpdatedAtAfter(String subscriptionCode, OffsetDateTime since);

    long countByStatus(WebhookDeliveryStatus status);
}
