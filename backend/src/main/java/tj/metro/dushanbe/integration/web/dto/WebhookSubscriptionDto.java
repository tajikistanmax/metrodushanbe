package tj.metro.dushanbe.integration.web.dto;

import java.time.Instant;
import java.util.List;

/**
 * Карточка подписчика в консоли (ADM-06).
 *
 * <p><b>Секрета здесь нет и не будет.</b> Ни плейнтекста, ни {@code secretHash}:
 * хеш — это ключ подписи (см. {@code WebhookSignature}), и отдавать его в списке
 * означало бы отдавать возможность подписывать. Вместо него —
 * {@code secretFingerprint}: первые 8 hex-символов хеша, чтобы оператор мог
 * глазами сверить «тот ли ключ у интегратора» после ротации, не получая ключа.
 */
public record WebhookSubscriptionDto(
        String code,
        String name,
        String targetUrl,
        List<String> eventTypes,
        boolean active,
        int rateLimitPerMinute,
        String secretFingerprint,
        String createdBy,
        Instant createdAt,
        Instant updatedAt) {
}
