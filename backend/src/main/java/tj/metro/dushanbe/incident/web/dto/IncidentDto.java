package tj.metro.dushanbe.incident.web.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Инцидент в ответах админского API.
 *
 * <p>{@code allowedTransitions} отдаётся вместе с карточкой, чтобы консоль
 * показывала ровно те действия, которые примет backend, и не приходилось
 * дублировать карту переходов в UI.
 */
public record IncidentDto(
        String code,
        String category,
        String severity,
        String status,
        String title,
        String description,
        String lineCode,
        String stationCode,
        String reportedBy,
        String assignedTo,
        String resolution,
        String publicAlertCode,
        OffsetDateTime occurredAt,
        OffsetDateTime acknowledgedAt,
        OffsetDateTime resolvedAt,
        OffsetDateTime closedAt,
        OffsetDateTime updatedAt,
        List<String> allowedTransitions) {
}
