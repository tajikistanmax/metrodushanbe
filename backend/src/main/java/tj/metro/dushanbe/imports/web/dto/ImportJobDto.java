package tj.metro.dushanbe.imports.web.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Сводка задания импорта в ответах админского API: статус жизненного цикла,
 * источник (IMP-01) и счётчики применённых/отклонённых фич.
 */
public record ImportJobDto(UUID id,
                           String type,
                           String status,
                           String sourceName,
                           String sourceHash,
                           int featureCount,
                           int createdCount,
                           int updatedCount,
                           int failedCount,
                           Instant startedAt,
                           Instant finishedAt,
                           Instant createdAt) {
}
