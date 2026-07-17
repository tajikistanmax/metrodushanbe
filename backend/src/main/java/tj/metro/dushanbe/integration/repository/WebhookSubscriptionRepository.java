package tj.metro.dushanbe.integration.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import tj.metro.dushanbe.integration.domain.WebhookSubscription;

/** Доступ к подписчикам вебхуков (INT-02, ADM-06). */
public interface WebhookSubscriptionRepository extends JpaRepository<WebhookSubscription, UUID> {

    Optional<WebhookSubscription> findByCode(String code);

    boolean existsByCode(String code);

    List<WebhookSubscription> findAllByOrderByCodeAsc();

    /**
     * Кандидаты фан-аута при публикации события. Фильтр по типу события идёт
     * в памяти ({@code WebhookSubscription.subscribedTo}): event_types — jsonb,
     * и запрос по его элементам был бы привязан к диалекту PostgreSQL, ради
     * выборки из десятка строк.
     */
    List<WebhookSubscription> findByActiveTrue();
}
