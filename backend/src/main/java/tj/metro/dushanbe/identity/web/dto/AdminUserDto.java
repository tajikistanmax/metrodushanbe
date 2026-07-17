package tj.metro.dushanbe.identity.web.dto;

import java.time.OffsetDateTime;

/**
 * Оператор консоли в REST-ответах.
 *
 * <p>Поля {@code passwordHash} здесь нет намеренно: хеш не покидает backend
 * ни при каких обстоятельствах.
 */
public record AdminUserDto(
        String username,
        String displayName,
        String role,
        boolean active,
        long sessionVersion,
        OffsetDateTime lastLoginAt,
        OffsetDateTime updatedAt) {
}
