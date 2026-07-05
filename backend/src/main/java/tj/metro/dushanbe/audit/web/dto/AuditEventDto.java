package tj.metro.dushanbe.audit.web.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Событие аудита в ответах админского API.
 * {@code before}/{@code after} — снимки состояния сущности (JSON-объекты) или null;
 * {@code at} — ISO-8601 UTC.
 */
public record AuditEventDto(UUID id,
                            String actor,
                            String action,
                            String entityType,
                            String entityId,
                            Map<String, Object> before,
                            Map<String, Object> after,
                            Instant at) {
}
