package tj.metro.dushanbe.citizen.web.dto;

import java.time.Instant;

/** Полное представление обращения в операционной консоли. */
public record CitizenRequestAdminDto(
        String code,
        String type,
        String priority,
        String status,
        String subject,
        String message,
        String contactName,
        String contactEmail,
        String contactPhone,
        String lineCode,
        String stationCode,
        String response,
        String assignedTo,
        Instant responseDueAt,
        Instant resolutionDueAt,
        boolean responseSlaBreached,
        boolean resolutionSlaBreached,
        Instant createdAt,
        Instant updatedAt,
        Instant resolvedAt) {
}
