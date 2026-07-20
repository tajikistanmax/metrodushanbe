package tj.metro.dushanbe.admin.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Токен актора (аудит-пункт 5): подпись подтверждает имя, TTL ограничивает срок,
 * чужой секрет и порча токена отвергаются.
 */
class AdminActorTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-20T12:00:00Z");
    private static final String SECRET = "actor-secret-0123456789-abcdefgh";

    private AdminAuthProperties props(String secret, boolean required) {
        AdminAuthProperties properties = new AdminAuthProperties();
        properties.getActorToken().setSecret(secret);
        properties.getActorToken().setRequired(required);
        properties.getActorToken().setTtl(Duration.ofMinutes(2));
        properties.getActorToken().setClockSkew(Duration.ofSeconds(30));
        return properties;
    }

    private AdminActorTokenService service(String secret, Instant now) {
        return new AdminActorTokenService(props(secret, true), Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void issuedTokenVerifiesBackToSameClaims() {
        AdminActorTokenService service = service(SECRET, NOW);

        String token = service.issue("Operator1", 7L, "nonce-abc");
        Optional<AdminActorClaims> claims = service.verify(token);

        assertTrue(claims.isPresent());
        assertEquals("operator1", claims.get().username());
        assertEquals(7L, claims.get().sessionVersion());
        assertEquals("nonce-abc", claims.get().nonce());
    }

    @Test
    void tamperedPayloadIsRejected() {
        AdminActorTokenService service = service(SECRET, NOW);
        String token = service.issue("operator1", 1L, "n");
        // Портим один символ подписи — HMAC перестаёт сходиться.
        String tampered = token.substring(0, token.length() - 1)
                + (token.endsWith("0") ? "1" : "0");

        assertTrue(service.verify(tampered).isEmpty());
    }

    @Test
    void tokenSignedWithOtherSecretIsRejected() {
        String token = service("first-secret-first-secret-123456", NOW).issue("root", 0L, "n");
        // Тот же момент времени, другой секрет — подделка имени актора.
        AdminActorTokenService verifier = service("other-secret-other-secret-123456", NOW);

        assertTrue(verifier.verify(token).isEmpty());
    }

    @Test
    void expiredTokenIsRejected() {
        String token = service(SECRET, NOW).issue("root", 0L, "n");
        // Проверяем через три минуты — за пределами TTL 2 минуты.
        AdminActorTokenService later = service(SECRET, NOW.plus(Duration.ofMinutes(3)));

        assertTrue(later.verify(token).isEmpty());
    }

    @Test
    void tokenFromFutureBeyondSkewIsRejected() {
        // Выпущен «на час вперёд» — иначе issuedAt из будущего обнулял бы смысл TTL.
        String token = service(SECRET, NOW.plus(Duration.ofHours(1))).issue("root", 0L, "n");
        AdminActorTokenService now = service(SECRET, NOW);

        assertTrue(now.verify(token).isEmpty());
    }

    @Test
    void withinSkewFutureTokenIsAccepted() {
        String token = service(SECRET, NOW.plus(Duration.ofSeconds(10))).issue("root", 0L, "n");
        AdminActorTokenService now = service(SECRET, NOW);

        assertTrue(now.verify(token).isPresent());
    }

    @Test
    void blankSecretMeansNotConfiguredAndRejectsEverything() {
        AdminActorTokenService service = service("", NOW);

        assertFalse(service.isConfigured());
        assertTrue(service.verify("anything.here").isEmpty());
    }

    @Test
    void malformedTokensAreRejected() {
        AdminActorTokenService service = service(SECRET, NOW);

        assertTrue(service.verify(null).isEmpty());
        assertTrue(service.verify("").isEmpty());
        assertTrue(service.verify("no-separator").isEmpty());
        assertTrue(service.verify(".onlysig").isEmpty());
        assertTrue(service.verify("payload.").isEmpty());
    }
}
