package tj.metro.dushanbe.integration.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import tj.metro.dushanbe.integration.domain.WebhookDelivery;
import tj.metro.dushanbe.integration.repository.WebhookDeliveryRepository;

class WebhookDeliveryClaimerTest {

    private static final OffsetDateTime NOW =
            OffsetDateTime.of(2026, 7, 17, 10, 0, 0, 0, ZoneOffset.UTC);

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final WebhookDeliveryRepository repository = mock(WebhookDeliveryRepository.class);
    private final WebhookDeliveryClaimer claimer = new WebhookDeliveryClaimer(jdbcTemplate, repository);

    @Test
    @SuppressWarnings("unchecked")
    void atomicClaimReturnsTheLeasedDelivery() {
        UUID id = UUID.randomUUID();
        WebhookDelivery delivery = new WebhookDelivery(id, "WHD-1", UUID.randomUUID(),
                "city-portal", "alert_published", "alert", "ALERT-1", "trace-1", "{}", NOW);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class),
                eq(NOW), eq(NOW), any(UUID.class), eq(NOW.plusSeconds(30))))
                .thenReturn(List.of(id));
        when(repository.findById(id)).thenReturn(Optional.of(delivery));

        Optional<WebhookDelivery> result = claimer.claimNext(NOW, NOW.plusSeconds(30));

        assertEquals(Optional.of(delivery), result);
        verify(repository).findById(id);
    }

    @Test
    void rejectsNonPositiveLease() {
        assertThrows(IllegalArgumentException.class, () -> claimer.claimNext(NOW, NOW));
    }
}
