package tj.metro.dushanbe.ticketing.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Токены QR-билетов (TKT-04): генерация, хеширование, сравнение.
 *
 * <p>Приём тот же, что у tracking-токена обращений
 * ({@code CitizenRequestService}), и это намеренно: два разных способа хранить
 * предъявительский секрет в одной кодовой базе — гарантия, что один из них
 * рано или поздно сделают неправильно.
 *
 * <p>Правила, ради которых класс существует:
 * <ul>
 *   <li>токен непредсказуем — {@link SecureRandom}, 256 бит. Билет на
 *       предъявителя: угадавший токен проедет;
 *   <li>в системе хранится только SHA-256 — сам токен живёт ровно один раз, в
 *       ответе на покупку. Дамп БД не даёт возможности предъявить чужой билет;
 *   <li>сравнение — {@link MessageDigest#isEqual}, постоянного времени: побайтовый
 *       equals на горячем пути турникета — это оракул для перебора.
 * </ul>
 */
public final class TicketTokens {

    private static final SecureRandom RANDOM = new SecureRandom();

    /** 256 бит: столько же, сколько у tracking-токена обращений. */
    private static final int TOKEN_BYTES = 32;

    private TicketTokens() {
    }

    /** Новый непредсказуемый токен. Показывается покупателю один раз и нигде не сохраняется. */
    public static String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 в hex — ровно 64 символа, как ticket.token_hash varchar(64). */
    public static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    /** Сравнение хешей постоянного времени. */
    public static boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                actual.getBytes(StandardCharsets.US_ASCII));
    }
}
