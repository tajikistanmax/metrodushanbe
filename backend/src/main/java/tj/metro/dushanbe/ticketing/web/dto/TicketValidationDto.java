package tj.metro.dushanbe.ticketing.web.dto;

import java.time.Instant;

/**
 * Решение по предъявленному билету (TKT-04).
 *
 * <p><b>Почему отказ — это HTTP 200, а не ошибка.</b> Турникету нужно решение
 * «пропускать / не пропускать», а не исключение: недействительный билет — штатный
 * исход валидации, а не сбой запроса.
 *
 * <p><b>Почему неизвестный токен не даёт 404.</b> Ответ на неизвестный токен и на
 * заблокированный билет одинаков по форме: {@code valid = false}. Иначе эндпоинт
 * становится оракулом — по коду ответа можно перебором отличать существующие
 * токены от несуществующих, а это первый шаг к подбору.
 *
 * @param valid      пропускать ли пассажира
 * @param reason     машиночитаемая причина отказа (null при valid = true)
 * @param ticketCode null, если билет по токену не найден
 * @param demo       билет демонстрационный: платежа за ним не было
 */
public record TicketValidationDto(
        boolean valid,
        String reason,
        String ticketCode,
        String status,
        String kind,
        String riderCategory,
        Instant validUntil,
        boolean demo) {
}
