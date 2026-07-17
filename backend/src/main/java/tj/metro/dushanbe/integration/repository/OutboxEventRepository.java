package tj.metro.dushanbe.integration.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import tj.metro.dushanbe.integration.domain.OutboxEvent;

/** Доступ к событиям outbox (INT-03). */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    Optional<OutboxEvent> findByEventId(UUID eventId);

    boolean existsByEventId(UUID eventId);

    List<OutboxEvent> findByAggregateTypeAndAggregateCodeOrderByOccurredAtDesc(
            String aggregateType, String aggregateCode);

    /** Разбор «портал не получил событие» по трассе запроса (INT-05). */
    List<OutboxEvent> findByTraceIdOrderByOccurredAtAsc(String traceId);
}
