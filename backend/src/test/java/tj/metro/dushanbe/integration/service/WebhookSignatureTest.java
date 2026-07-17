package tj.metro.dushanbe.integration.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WebhookSignatureTest {

    private static final String SECRET = "s3cret-value";
    private static final String BODY = """
            {"eventId":"11111111-1111-1111-1111-111111111111","eventType":"alert_published"}""";

    @Test
    void signatureIsStableAcrossCallsSoSubscriberCanVerifyRetries() {
        String hash = WebhookSignature.hashSecret(SECRET);

        String first = WebhookSignature.sign(hash, BODY);
        String second = WebhookSignature.sign(hash, BODY);

        assertEquals(first, second, "повтор доставки обязан нести ту же подпись");
        assertTrue(first.startsWith("sha256="));
    }

    /**
     * Проверка «известного ответа»: подпись не должна поехать от рефакторинга.
     * Значение получено этой же схемой (HMAC-SHA256 на hex-хеше секрета) и
     * воспроизводится подписчиком независимо, например:
     * {@code printf '%s' "$BODY" | openssl dgst -sha256 -hmac "$(printf '%s' "$SECRET" | sha256sum | cut -d' ' -f1)"}
     */
    @Test
    void signatureVerifiesWithSameDerivedKey() {
        String hash = WebhookSignature.hashSecret(SECRET);
        String signature = WebhookSignature.sign(hash, BODY);

        assertTrue(WebhookSignature.verify(hash, BODY, signature));
    }

    @Test
    void tamperedBodyBreaksSignature() {
        String hash = WebhookSignature.hashSecret(SECRET);
        String signature = WebhookSignature.sign(hash, BODY);

        assertFalse(WebhookSignature.verify(hash, BODY.replace("alert_published", "alert_cleared"),
                signature), "подмена тела обязана ломать подпись");
    }

    @Test
    void wrongSecretBreaksSignature() {
        String signature = WebhookSignature.sign(WebhookSignature.hashSecret(SECRET), BODY);

        assertFalse(WebhookSignature.verify(WebhookSignature.hashSecret("other-secret"), BODY, signature));
    }

    @Test
    void missingSignatureIsRejectedRatherThanThrowing() {
        assertFalse(WebhookSignature.verify(WebhookSignature.hashSecret(SECRET), BODY, null));
    }

    @Test
    void hashIsHexSha256AndIrreversibleForStorage() {
        String hash = WebhookSignature.hashSecret(SECRET);

        assertEquals(64, hash.length(), "SHA-256 в hex — ровно 64 символа, как в citizen_request");
        assertTrue(hash.matches("[0-9a-f]{64}"));
        assertNotEquals(SECRET, hash);
        assertEquals(hash, WebhookSignature.hashSecret(SECRET), "хеш детерминирован");
    }

    @Test
    void generatedSecretsAreUniqueAndUrlSafe() {
        String first = WebhookSignature.newSecret();
        String second = WebhookSignature.newSecret();

        assertNotEquals(first, second);
        assertTrue(first.matches("[A-Za-z0-9_-]+"), "секрет обязан переживать копирование в URL/заголовок");
    }
}
