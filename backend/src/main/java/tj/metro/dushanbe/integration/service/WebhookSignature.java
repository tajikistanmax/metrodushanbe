package tj.metro.dushanbe.integration.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Секреты и подпись вебхуков (INT-02, INT-05).
 *
 * <p><b>Схема.</b> При создании подписки генерируется случайный секрет и
 * показывается вызывающему ровно один раз. В БД уходит только
 * {@link #hashSecret(String)} — SHA-256 в hex, тем же приёмом, что
 * {@code CitizenRequest.trackingTokenHash}. Тело вебхука подписывается
 * HMAC-SHA256, где КЛЮЧОМ служит этот же хеш как производный ключ: подписчик
 * знает исходный секрет, выводит из него хеш у себя и проверяет подпись.
 *
 * <p><b>Что эта схема даёт и чего не даёт — честно.</b> Она исключает утечку
 * САМОГО секрета через GET-ответы консоли, логи и дампы: восстановить плейнтекст
 * из хеша нельзя, а значит его нельзя переиспользовать там, где интегратор
 * (вопреки правилам) завёл тот же секрет. Она НЕ защищает от компрометации нашей
 * БД: получив secret_hash, атакующий подпишет им что угодно. Настоящее решение —
 * хранение ключа в KMS/vault и подпись через него; это внешний блокер
 * инфраструктуры, до его закрытия схема выше — осознанный компромисс, а не
 * недосмотр.
 *
 * <p>Своей криптографии здесь нет намеренно: HMAC-SHA256 из JCE, сравнение —
 * {@link MessageDigest#isEqual} (constant-time), как в {@code CitizenRequestService}.
 */
public final class WebhookSignature {

    /** Заголовок с подписью тела. */
    public static final String SIGNATURE_HEADER = "X-Metro-Signature";

    /** Заголовок с ключом идемпотентности события (INT-05). */
    public static final String EVENT_ID_HEADER = "X-Metro-Event-Id";

    /** Заголовок сквозной трассировки; тот же requestId, что в X-Request-Id. */
    public static final String TRACE_ID_HEADER = "X-Metro-Trace-Id";

    /** Заголовок с типом события — чтобы подписчик роутил, не разбирая тело. */
    public static final String EVENT_TYPE_HEADER = "X-Metro-Event-Type";

    /** Префикс подписи: оставляет место для смены алгоритма без слома парсера. */
    private static final String PREFIX = "sha256=";

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final SecureRandom RANDOM = new SecureRandom();

    private WebhookSignature() {
    }

    /** Новый секрет подписчика: 32 случайных байта в url-safe base64. */
    public static String newSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 секрета в hex — единственная форма, попадающая в БД. */
    public static String hashSecret(String secret) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    /**
     * Подпись тела вебхука: {@code sha256=<hex HMAC-SHA256(secretHash, payload)>}.
     *
     * <p>Подписывается ровно та строка, которая уйдёт в теле, байт в байт.
     * Пересериализовать payload перед отправкой нельзя: порядок ключей JSON не
     * гарантирован, и подпись перестанет сходиться у подписчика.
     *
     * @param secretHash производный ключ — {@code webhook_subscription.secret_hash}
     * @param payload    тело запроса в том виде, в котором оно отправляется
     */
    public static String sign(String secretHash, String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secretHash.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return PREFIX + HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | java.security.InvalidKeyException exception) {
            throw new IllegalStateException("HmacSHA256 is not available", exception);
        }
    }

    /**
     * Проверка подписи в constant-time. Нужна подписчикам и тестам; у нас самих
     * входящих вебхуков пока нет.
     */
    public static boolean verify(String secretHash, String payload, String signature) {
        if (signature == null) {
            return false;
        }
        return MessageDigest.isEqual(
                sign(secretHash, payload).getBytes(StandardCharsets.US_ASCII),
                signature.getBytes(StandardCharsets.US_ASCII));
    }
}
