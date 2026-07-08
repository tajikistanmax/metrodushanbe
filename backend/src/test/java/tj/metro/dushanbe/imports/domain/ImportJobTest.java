package tj.metro.dushanbe.imports.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ImportJobTest {

    @Test
    void constructorSetsFieldsAndDefaultsToPending() {
        var id = UUID.randomUUID();
        var job = new ImportJob(id, ImportJob.TYPE_NETWORK_GEOJSON, "source.geojson", "abc123");

        assertEquals(id, job.getId());
        assertEquals(ImportJob.TYPE_NETWORK_GEOJSON, job.getType());
        assertEquals(ImportJob.STATUS_PENDING, job.getStatus());
        assertEquals("source.geojson", job.getSourceName());
        assertEquals("abc123", job.getSourceHash());
        assertEquals(0, job.getFeatureCount());
        assertEquals(0, job.getCreatedCount());
        assertEquals(0, job.getUpdatedCount());
        assertEquals(0, job.getFailedCount());
        assertNull(job.getStartedAt());
        assertNull(job.getFinishedAt());
    }

    @Test
    void onCreateSetsTimestamps() {
        var job = new ImportJob(UUID.randomUUID(), ImportJob.TYPE_NETWORK_GEOJSON, null, null);

        assertNull(job.getCreatedAt());
        assertNull(job.getUpdatedAt());

        job.onCreate();

        assertNotNull(job.getCreatedAt());
        assertNotNull(job.getUpdatedAt());
    }

    @Test
    void onCreateDoesNotOverrideExistingCreatedAt() {
        var job = new ImportJob(UUID.randomUUID(), ImportJob.TYPE_NETWORK_GEOJSON, null, null);
        job.onCreate();
        var original = job.getCreatedAt();

        job.onCreate();

        assertEquals(original, job.getCreatedAt());
    }

    @Test
    void onUpdateUpdatesUpdatedAt() {
        var job = new ImportJob(UUID.randomUUID(), ImportJob.TYPE_NETWORK_GEOJSON, null, null);
        job.onCreate();
        var beforeUpdate = job.getUpdatedAt();

        job.onUpdate();

        assertNotNull(job.getUpdatedAt());
        assertTrue(job.getUpdatedAt().isAfter(beforeUpdate)
                || job.getUpdatedAt().isEqual(beforeUpdate));
    }

    @Test
    void markRunningSetsStatusAndStartedAt() {
        var job = new ImportJob(UUID.randomUUID(), ImportJob.TYPE_NETWORK_GEOJSON, null, null);
        var when = OffsetDateTime.now();

        job.markRunning(when);

        assertEquals(ImportJob.STATUS_RUNNING, job.getStatus());
        assertEquals(when, job.getStartedAt());
    }

    @Test
    void finishWithAppliedAndNoFailuresSetsSuccess() {
        var job = new ImportJob(UUID.randomUUID(), ImportJob.TYPE_NETWORK_GEOJSON, null, null);
        var when = OffsetDateTime.now();

        job.markRunning(when.minusMinutes(5));
        job.finish(10, 8, 2, 0, when);

        assertEquals(ImportJob.STATUS_SUCCESS, job.getStatus());
        assertEquals(10, job.getFeatureCount());
        assertEquals(8, job.getCreatedCount());
        assertEquals(2, job.getUpdatedCount());
        assertEquals(0, job.getFailedCount());
        assertEquals(when, job.getFinishedAt());
    }

    @Test
    void finishWithPartialFailuresSetsPartial() {
        var job = new ImportJob(UUID.randomUUID(), ImportJob.TYPE_NETWORK_GEOJSON, null, null);
        var when = OffsetDateTime.now();

        job.finish(10, 6, 0, 4, when);

        assertEquals(ImportJob.STATUS_PARTIAL, job.getStatus());
        assertEquals(6, job.getCreatedCount());
        assertEquals(4, job.getFailedCount());
    }

    @Test
    void finishWithNoAppliedSetsFailed() {
        var job = new ImportJob(UUID.randomUUID(), ImportJob.TYPE_NETWORK_GEOJSON, null, null);
        var when = OffsetDateTime.now();

        job.finish(10, 0, 0, 10, when);

        assertEquals(ImportJob.STATUS_FAILED, job.getStatus());
        assertEquals(10, job.getFailedCount());
    }

    @Test
    void finishWithAllFailedSetsFailed() {
        var job = new ImportJob(UUID.randomUUID(), ImportJob.TYPE_NETWORK_GEOJSON, null, null);
        var when = OffsetDateTime.now();

        job.finish(5, 0, 0, 5, when);

        assertEquals(ImportJob.STATUS_FAILED, job.getStatus());
    }

    @Test
    void markFailedSetsStatusAndFinishedAt() {
        var job = new ImportJob(UUID.randomUUID(), ImportJob.TYPE_NETWORK_GEOJSON, null, null);
        var when = OffsetDateTime.now();

        job.markFailed(when);

        assertEquals(ImportJob.STATUS_FAILED, job.getStatus());
        assertEquals(when, job.getFinishedAt());
    }
}
