package tj.metro.dushanbe.audit.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditEventTest {

    @Test
    void constructorSetsFields() {
        var id = UUID.randomUUID();
        var now = OffsetDateTime.now();
        Map<String, Object> before = Map.of("status", "active");
        Map<String, Object> after = Map.of("status", "inactive");
        var event = new AuditEvent(id, "admin", "line.update", "line", "L1", before, after, now);

        assertEquals(id, event.getId());
        assertEquals("admin", event.getActor());
        assertEquals("line.update", event.getAction());
        assertEquals("line", event.getEntityType());
        assertEquals("L1", event.getEntityId());
        assertEquals(before, event.getBefore());
        assertEquals(after, event.getAfter());
        assertEquals(now, event.getAt());
    }

    @Test
    void constructorAllowsNullBeforeAndAfter() {
        var event = new AuditEvent(UUID.randomUUID(), "system", "line.create", "line", "L2",
                null, null, OffsetDateTime.now());

        assertNull(event.getBefore());
        assertNull(event.getAfter());
    }

    @Test
    void onCreateSetsAtWhenNull() {
        var event = new AuditEvent(UUID.randomUUID(), "admin", "line.create", "line", "L3",
                null, null, null);
        assertNull(event.getAt());

        event.onCreate();

        assertNotNull(event.getAt());
    }

    @Test
    void onCreateDoesNotOverrideExistingAt() {
        var now = OffsetDateTime.now();
        var event = new AuditEvent(UUID.randomUUID(), "admin", "line.create", "line", "L4",
                null, null, now);

        event.onCreate();

        assertEquals(now, event.getAt());
    }
}
