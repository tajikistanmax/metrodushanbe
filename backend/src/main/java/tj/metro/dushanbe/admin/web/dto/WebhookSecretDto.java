package tj.metro.dushanbe.admin.web.dto;

import tj.metro.dushanbe.integration.web.dto.WebhookSubscriptionDto;

/**
 * Ответ операций, порождающих секрет: создание и ротация (ADM-06).
 *
 * <p>Единственное место во всём API, где секрет виден. Повторно получить его
 * нельзя — только сгенерировать новый ротацией; тем же приёмом отдаётся
 * {@code trackingToken} в {@code CitizenRequestCreateResponse}.
 *
 * @param secret плейнтекст секрета — показать интегратору и не сохранять у себя
 */
public record WebhookSecretDto(WebhookSubscriptionDto subscription, String secret) {
}
