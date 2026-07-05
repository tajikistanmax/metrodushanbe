package tj.metro.dushanbe.alert.web.dto;

/**
 * Таргет сервисного уведомления в ответах API:
 * {@code type} — line|station; {@code code} — стабильный код линии/станции.
 */
public record AlertTargetDto(String type, String code) {
}
