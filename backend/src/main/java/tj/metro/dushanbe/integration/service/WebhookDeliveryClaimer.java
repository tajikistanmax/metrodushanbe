package tj.metro.dushanbe.integration.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tj.metro.dushanbe.integration.domain.WebhookDelivery;
import tj.metro.dushanbe.integration.repository.WebhookDeliveryRepository;

/** Atomically leases one due webhook without keeping a DB transaction open during HTTP. */
@Service
public class WebhookDeliveryClaimer {

    private static final String CLAIM_SQL = """
            WITH candidate AS (
                SELECT id
                  FROM webhook_delivery
                 WHERE (status IN ('pending', 'failed') AND next_attempt_at <= ?)
                    OR (status = 'processing' AND claim_until <= ?)
                 ORDER BY COALESCE(next_attempt_at, claim_until), id
                 FOR UPDATE SKIP LOCKED
                 LIMIT 1
            )
            UPDATE webhook_delivery delivery
               SET status = 'processing',
                   claim_token = ?,
                   claim_until = ?,
                   next_attempt_at = NULL,
                   version = delivery.version + 1
              FROM candidate
             WHERE delivery.id = candidate.id
            RETURNING delivery.id
            """;

    private final JdbcTemplate jdbcTemplate;
    private final WebhookDeliveryRepository repository;

    public WebhookDeliveryClaimer(JdbcTemplate jdbcTemplate,
                                  WebhookDeliveryRepository repository) {
        this.jdbcTemplate = jdbcTemplate;
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<WebhookDelivery> claimNext(OffsetDateTime now, OffsetDateTime leaseUntil) {
        if (now == null || leaseUntil == null || !leaseUntil.isAfter(now)) {
            throw new IllegalArgumentException("Webhook lease must end after claim time");
        }
        UUID claimToken = UUID.randomUUID();
        List<UUID> ids = jdbcTemplate.query(CLAIM_SQL,
                (resultSet, rowNumber) -> resultSet.getObject("id", UUID.class),
                now, now, claimToken, leaseUntil);
        return ids.stream().findFirst().flatMap(repository::findById);
    }
}
