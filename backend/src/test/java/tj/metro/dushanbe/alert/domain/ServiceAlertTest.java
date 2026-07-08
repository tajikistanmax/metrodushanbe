package tj.metro.dushanbe.alert.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ServiceAlertTest {

    private final Map<String, String> i18n = Map.of("tg", "Салом", "ru", "Привет", "en", "Hello");

    @Test
    void constructorSetsFields() {
        var id = UUID.randomUUID();
        var startsAt = OffsetDateTime.now();
        var endsAt = startsAt.plusHours(2);
        var targets = List.of(new AlertTarget("line", "L1"));

        var alert = new ServiceAlert(id, "ALERT-001", "info", "draft",
                i18n, i18n, startsAt, endsAt, targets);

        assertEquals(id, alert.getId());
        assertEquals("ALERT-001", alert.getCode());
        assertEquals("info", alert.getSeverity());
        assertEquals("draft", alert.getStatus());
        assertEquals(i18n, alert.getTitleI18n());
        assertEquals(i18n, alert.getBodyI18n());
        assertEquals(startsAt, alert.getStartsAt());
        assertEquals(endsAt, alert.getEndsAt());
        assertEquals(targets, alert.getTargets());
    }

    @Test
    void constructorDefensiveCopyOfTargets() {
        var mutableTargets = new ArrayList<AlertTarget>();
        mutableTargets.add(new AlertTarget("line", "L1"));

        var alert = new ServiceAlert(UUID.randomUUID(), "ALERT-002", "info", "draft",
                i18n, i18n, OffsetDateTime.now(), null, mutableTargets);

        mutableTargets.add(new AlertTarget("station", "S1"));

        assertEquals(1, alert.getTargets().size());
    }

    @Test
    void constructorAllowsNullEndsAtAndEmptyTargets() {
        var alert = new ServiceAlert(UUID.randomUUID(), "ALERT-003", "warning", "draft",
                i18n, i18n, OffsetDateTime.now(), null, List.of());

        assertNull(alert.getEndsAt());
        assertTrue(alert.getTargets().isEmpty());
    }

    @Test
    void onCreateSetsTimestamps() {
        var alert = new ServiceAlert(UUID.randomUUID(), "ALERT-004", "info", "draft",
                i18n, i18n, OffsetDateTime.now(), null, List.of());

        assertNull(alert.getCreatedAt());
        assertNull(alert.getUpdatedAt());

        alert.onCreate();

        assertNotNull(alert.getCreatedAt());
        assertNotNull(alert.getUpdatedAt());
    }

    @Test
    void onCreateDoesNotOverrideExistingCreatedAt() {
        var createdAt = OffsetDateTime.now().minusHours(1);
        var alert = new ServiceAlert(UUID.randomUUID(), "ALERT-005", "info", "draft",
                i18n, i18n, OffsetDateTime.now(), null, List.of());
        alert.onCreate();

        var originalCreatedAt = alert.getCreatedAt();

        alert.onCreate();

        assertEquals(originalCreatedAt, alert.getCreatedAt());
    }

    @Test
    void onUpdateSetsUpdatedAt() {
        var alert = new ServiceAlert(UUID.randomUUID(), "ALERT-006", "info", "draft",
                i18n, i18n, OffsetDateTime.now(), null, List.of());
        alert.onCreate();
        var beforeUpdate = alert.getUpdatedAt();

        alert.onUpdate();

        assertNotNull(alert.getUpdatedAt());
        assertTrue(alert.getUpdatedAt().isAfter(beforeUpdate)
                || alert.getUpdatedAt().isEqual(beforeUpdate));
    }

    @Test
    void updateContentChangesFields() {
        var alert = new ServiceAlert(UUID.randomUUID(), "ALERT-007", "info", "draft",
                i18n, i18n, OffsetDateTime.now(), null, List.of());
        var newI18n = Map.of("tg", "Ватан", "ru", "Родина", "en", "Motherland");
        var newStartsAt = OffsetDateTime.now().plusDays(1);
        var newEndsAt = newStartsAt.plusHours(3);
        var newTargets = List.of(new AlertTarget("station", "S1"));

        alert.updateContent("warning", newI18n, newI18n, newStartsAt, newEndsAt, newTargets);

        assertEquals("warning", alert.getSeverity());
        assertEquals(newI18n, alert.getTitleI18n());
        assertEquals(newI18n, alert.getBodyI18n());
        assertEquals(newStartsAt, alert.getStartsAt());
        assertEquals(newEndsAt, alert.getEndsAt());
        assertEquals(newTargets, alert.getTargets());
    }

    @Test
    void updateContentClearsAndReplacesTargets() {
        var alert = new ServiceAlert(UUID.randomUUID(), "ALERT-008", "info", "draft",
                i18n, i18n, OffsetDateTime.now(), null, List.of(new AlertTarget("line", "L1")));

        alert.updateContent("info", i18n, i18n, OffsetDateTime.now(), null, List.of());

        assertTrue(alert.getTargets().isEmpty());
    }

    @Test
    void markPublishedSetsStatusAndPublishedAt() {
        var alert = new ServiceAlert(UUID.randomUUID(), "ALERT-009", "info", "draft",
                i18n, i18n, OffsetDateTime.now(), null, List.of());
        var publishedAt = OffsetDateTime.now();

        alert.markPublished(publishedAt);

        assertEquals("published", alert.getStatus());
        assertEquals(publishedAt, alert.getPublishedAt());
    }
}
