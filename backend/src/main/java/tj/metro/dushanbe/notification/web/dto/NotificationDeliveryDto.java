package tj.metro.dushanbe.notification.web.dto;

import java.time.Instant;

/**
 * Факт доставки по одному каналу одному получателю (NTF-06).
 *
 * <p>{@code id} — внешний идентификатор доставки (своего {@code code} у неё нет,
 * см. NotificationDelivery); по нему же идёт повтор.
 *
 * <p><b>{@code simulated}</b> — признак того, что у канала нет реального провайдера
 * и доставка на demo-контуре только имитируется (NotificationChannel.external()).
 * Поле обязательное и не скрывается: показать оператору «доставлено» там, где
 * ничего никуда не ушло, — хуже, чем не иметь канала вовсе.
 */
public record NotificationDeliveryDto(String id,
                                      String messageCode,
                                      String channel,
                                      String recipient,
                                      String status,
                                      int attempts,
                                      String lastError,
                                      boolean simulated,
                                      Instant sentAt,
                                      Instant deliveredAt,
                                      Instant updatedAt) {
}
