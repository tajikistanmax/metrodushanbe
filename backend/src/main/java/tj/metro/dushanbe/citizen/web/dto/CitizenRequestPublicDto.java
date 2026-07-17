package tj.metro.dushanbe.citizen.web.dto;

import java.time.Instant;

/** Публичный статус обращения без контактных данных и внутреннего назначения. */
public record CitizenRequestPublicDto(
        String code,
        String type,
        String status,
        String subject,
        String response,
        Instant createdAt,
        Instant updatedAt) {
}
