package tj.metro.dushanbe.admin.security;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Выпуск и проверка короткоживущего токена актора (аудит-пункт 5).
 *
 * <h2>Зачем</h2>
 * До этого актор аудита брался из заголовка {@code X-Admin-Actor} «на доверии»:
 * любой обладатель общего {@code X-Admin-Key} мог назваться любым активным
 * суперадмином, и журнал зафиксировал бы чужое имя. Токен переводит имя актора из
 * категории «объявлено» в категорию «подтверждено подписью».
 *
 * <h2>Формат</h2>
 * <pre>
 *   token   = base64url(payload) "." hex(HMAC-SHA256(secret, base64url(payload)))
 *   payload = username "|" sessionVersion "|" issuedAtEpochSeconds "|" nonce
 * </pre>
 * Подпись покрывает именно base64url-текст, а не «сырой» payload: так подписанные
 * байты однозначны и совпадают с тем, что считает консоль (см. admin/src/lib/actor-token.ts).
 * Разделитель {@code |} запрещён внутри логина (логин — {@code [a-z0-9._-]}), поэтому
 * разбор payload однозначен и склеить два поля в одно нельзя.
 *
 * <h2>От чего схема ЗАЩИЩАЕТ</h2>
 * <ul>
 *   <li>Подмена имени актора тем, у кого есть только {@code X-Admin-Key}: без
 *       секрета подписи подделать имя нельзя, а секреты у ключа и у токена разные.</li>
 *   <li>Использование отозванной сессии: {@code sessionVersion} в токене сверяется с
 *       {@code admin_user.session_version}; смена роли, пароля или деактивация
 *       увеличивают версию, и все ранее выпущенные токены умирают немедленно.</li>
 *   <li>Бесконечное переиспользование перехваченного токена: {@code issuedAt}
 *       ограничен окном {@code app.admin.actor-token.ttl} (по умолчанию 2 минуты).</li>
 * </ul>
 *
 * <h2>От чего схема НЕ защищает — читать обязательно</h2>
 * <ul>
 *   <li><b>Это НЕ OIDC.</b> Секрет подписи — общий симметричный секрет консоли и
 *       backend. Любой, кто его получил (компрометация хоста консоли, утечка env),
 *       выпускает токен от имени кого угодно. Асимметричной подписи и внешнего
 *       эмитента здесь нет — это отдельный открытый P0 (ТЗ §6.1.7, §9.2).</li>
 *   <li><b>Replay в пределах TTL возможен.</b> {@code nonce} делает токены
 *       различимыми, но серверного хранилища использованных nonce нет: перехваченный
 *       токен можно повторить в течение TTL. Хранилище nonce потребовало бы общего
 *       кэша и не спасает от держателя секрета — цена не оправдана до перехода на OIDC.</li>
 *   <li><b>Это не подтверждение конкретного HTTP-запроса.</b> Токен не подписывает
 *       метод, путь и тело, поэтому в пределах TTL он переносится на другой запрос
 *       того же оператора. Ограничение прав по-прежнему даёт RBAC по роли.</li>
 *   <li>Компрометация {@code X-Admin-Key} по-прежнему открывает {@code /v1/admin/auth/**}
 *       (там актора ещё нет) — от подбора пароля там защищает lockout, а не токен.</li>
 * </ul>
 *
 * <p><b>Почему не Spring-бин.</b> Единственный потребитель — {@link AdminKeyAuthFilter},
 * который создаёт сервис сам. Инъекция бином сделала бы его обязательным во всех
 * {@code @WebMvcTest}-срезах (фильтр авто-поднимается в каждом), а Clock там нет —
 * контексты падали бы. Сервис строится на системных часах: свежесть токена — это
 * wall-clock (epoch seconds), а не доменное время сети, зона здесь роли не играет.
 */
public class AdminActorTokenService {

    private static final String ALGORITHM = "HmacSHA256";
    private static final char SEPARATOR = '.';
    private static final String FIELD_SEPARATOR = "|";

    private final AdminAuthProperties properties;
    private final Clock clock;

    public AdminActorTokenService(AdminAuthProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /** Настроен ли секрет подписи. Без него strict-режим обязан отвергать всё. */
    public boolean isConfigured() {
        String secret = properties.getActorToken().getSecret();
        return secret != null && !secret.isBlank();
    }

    /** Требуется ли токен обязательно (strict-режим, {@code app.admin.actor-token.required}). */
    public boolean isRequired() {
        return properties.getActorToken().isRequired();
    }

    /**
     * Выпускает токен. В проде выпуск делает консоль (Next), поэтому метод нужен
     * прежде всего тестам и диагностике — но алгоритм обязан быть один и тот же,
     * иначе расхождение выпуска и проверки заметит только продакшен.
     */
    public String issue(String username, long sessionVersion, String nonce) {
        String payload = AdminUserNames.normalize(username) + FIELD_SEPARATOR + sessionVersion
                + FIELD_SEPARATOR + Instant.now(clock).getEpochSecond() + FIELD_SEPARATOR + nonce;
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encoded + SEPARATOR + HexFormat.of().formatHex(hmac(encoded));
    }

    /**
     * Проверяет подпись и срок годности токена.
     *
     * <p>Любая некорректность — формат, подпись, срок, нечисловые поля — даёт пустой
     * результат без уточнения причины: разница в ответах подсказывала бы атакующему,
     * какое из полей он подобрал.
     */
    public Optional<AdminActorClaims> verify(String token) {
        if (!isConfigured() || token == null || token.isBlank()) {
            return Optional.empty();
        }
        int separator = token.lastIndexOf(SEPARATOR);
        if (separator <= 0 || separator == token.length() - 1) {
            return Optional.empty();
        }
        String encoded = token.substring(0, separator);
        byte[] provided;
        try {
            provided = HexFormat.of().parseHex(token.substring(separator + 1));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
        // MessageDigest.isEqual — сравнение за постоянное время; обычный equals
        // на массивах утекал бы длину совпавшего префикса подписи.
        if (!MessageDigest.isEqual(provided, hmac(encoded))) {
            return Optional.empty();
        }

        String payload;
        try {
            payload = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
        String[] parts = payload.split("\\|", -1);
        if (parts.length != 4 || parts[0].isBlank() || parts[3].isBlank()) {
            return Optional.empty();
        }

        long sessionVersion;
        long issuedAtSeconds;
        try {
            sessionVersion = Long.parseLong(parts[1]);
            issuedAtSeconds = Long.parseLong(parts[2]);
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }

        Instant issuedAt = Instant.ofEpochSecond(issuedAtSeconds);
        Instant now = Instant.now(clock);
        Duration ttl = properties.getActorToken().getTtl();
        Duration skew = properties.getActorToken().getClockSkew();
        boolean expired = issuedAt.plus(ttl).isBefore(now);
        // Токен «из будущего» дальше допустимого расхождения часов — тоже отказ:
        // иначе выпуск с issuedAt на год вперёд сделал бы TTL бессмысленным.
        boolean fromFuture = issuedAt.minus(skew).isAfter(now);
        if (expired || fromFuture) {
            return Optional.empty();
        }
        return Optional.of(new AdminActorClaims(
                parts[0].toLowerCase(Locale.ROOT), sessionVersion, issuedAt, parts[3]));
    }

    private byte[] hmac(String value) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(
                    properties.getActorToken().getSecret().getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            // HmacSHA256 обязателен для любой JRE; отсутствие означает сломанную
            // среду, а не сценарий, который стоит деградировать до «пропустить».
            throw new IllegalStateException("HMAC-SHA256 недоступен в этой JRE", ex);
        }
    }

    /** Нормализация логина, общая с {@code AdminUser}; вынесена, чтобы не тянуть JPA в security. */
    static final class AdminUserNames {
        private AdminUserNames() {
        }

        static String normalize(String value) {
            return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
        }
    }
}
