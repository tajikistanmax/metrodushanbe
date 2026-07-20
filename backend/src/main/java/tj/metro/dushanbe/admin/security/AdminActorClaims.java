package tj.metro.dushanbe.admin.security;

import java.time.Instant;

/**
 * Подтверждённые утверждения о действующем операторе, извлечённые из
 * {@code X-Admin-Actor-Token} (аудит-пункт 5).
 *
 * <p>«Подтверждённые» здесь означает ровно одно: подпись HMAC сошлась, то есть
 * значения выпущены держателем общего секрета. Соответствие {@code sessionVersion}
 * реальной учётной записи проверяет уже {@link AdminKeyAuthFilter} — токен сам по
 * себе не знает состояния БД.
 *
 * @param username       логин оператора, нормализованный к нижнему регистру
 * @param sessionVersion версия учётной записи на момент выпуска токена
 * @param issuedAt       момент выпуска; ограничивает срок жизни токена
 * @param nonce          случайная строка выпуска — делает токены неповторяющимися
 *                       в пределах секунды (защиты от replay сама по себе не даёт)
 */
public record AdminActorClaims(String username, long sessionVersion, Instant issuedAt, String nonce) {
}
