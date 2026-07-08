package tj.metro.dushanbe.audit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tj.metro.dushanbe.audit.domain.AuditEvent;
import tj.metro.dushanbe.audit.repository.AuditEventRepository;

class AuditServiceTest {

    private AuditEventRepository repository;
    private Clock clock;
    private AuditService service;

    @BeforeEach
    void setUp() {
        repository = mock(AuditEventRepository.class);
        clock = Clock.fixed(Instant.parse("2026-06-15T10:30:00Z"), ZoneId.of("UTC"));
        service = new AuditService(repository, clock);
    }

    @Test
    void recordCreatesAndSavesEvent() {
        var eventId = UUID.randomUUID();
        Map<String, Object> before = new HashMap<>();
        before.put("status", "draft");
        Map<String, Object> after = new HashMap<>();
        after.put("status", "published");
        when(repository.save(any(AuditEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var result = service.record("admin", "alert.publish", "alert", "ALERT-001",
                before, after);

        assertNotNull(result);
        assertEquals("admin", result.getActor());
        assertEquals("alert.publish", result.getAction());
        assertEquals("alert", result.getEntityType());
        assertEquals("ALERT-001", result.getEntityId());
        assertEquals(before, result.getBefore());
        assertEquals(after, result.getAfter());
        assertEquals(OffsetDateTime.ofInstant(clock.instant(), clock.getZone()), result.getAt());

        verify(repository).save(any(AuditEvent.class));
    }

    @Test
    void recordAllowsNullBeforeAndAfter() {
        when(repository.save(any(AuditEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var result = service.record("system", "line.create", "line", "L1",
                null, null);

        assertNotNull(result);
        assertEquals("system", result.getActor());
        assertEquals("line.create", result.getAction());
        assertEquals("line", result.getEntityType());
        assertEquals("L1", result.getEntityId());
        assertEquals(null, result.getBefore());
        assertEquals(null, result.getAfter());
    }

    @Test
    void recordUsesFixedClock() {
        when(repository.save(any(AuditEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var result = service.record("admin", "line.update", "line", "L1",
                null, null);

        var expectedAt = OffsetDateTime.ofInstant(clock.instant(), clock.getZone());
        assertEquals(expectedAt, result.getAt());
    }
}
