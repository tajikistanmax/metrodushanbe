package tj.metro.dushanbe.integration.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tj.metro.dushanbe.common.error.RequestIdFilter;
import tj.metro.dushanbe.integration.domain.OutboxEvent;
import tj.metro.dushanbe.integration.domain.WebhookDelivery;
import tj.metro.dushanbe.integration.domain.WebhookEventType;
import tj.metro.dushanbe.integration.domain.WebhookSubscription;
import tj.metro.dushanbe.integration.repository.OutboxEventRepository;
import tj.metro.dushanbe.integration.repository.WebhookDeliveryRepository;
import tj.metro.dushanbe.integration.repository.WebhookSubscriptionRepository;
import tj.metro.dushanbe.integration.web.dto.WebhookEventPayload;

/**
 * Публикация событий сети во внутренний outbox (INT-03).
 *
 * <p><b>Зачем outbox, а не отправка вебхука прямо из бизнес-кода.</b> Это
 * ключевая идея модуля, и она про транзакции, а не про производительность.
 * Отправив HTTP прямо в момент изменения, мы получаем два состояния, которые
 * нельзя согласовать:
 * <ul>
 *   <li>HTTP прошёл, а транзакция откатилась (нарушен CHECK, конфликт, падение
 *       приложения между отправкой и коммитом) — «отправили вебхук, а транзакция
 *       откатилась»: городской портал знает о публикации уведомления, которого у
 *       нас нет. Отозвать доставленный вебхук нечем;</li>
 *   <li>транзакция закоммитилась, а HTTP не прошёл — событие потеряно молча, и
 *       узнаем мы об этом от интегратора через неделю.</li>
 * </ul>
 *
 * <p>Поэтому {@link #publish} выполняется без собственной транзакции
 * ({@code Propagation.REQUIRED}) и ПРИСОЕДИНЯЕТСЯ к транзакции вызывающей
 * бизнес-операции — ровно как {@code AuditService.record}. Событие и
 * бизнес-изменение фиксируются атомарно: либо в БД есть и изменение, и запись в
 * outbox, либо нет ни того, ни другого. Наружу же не уходит ничего — доставку
 * отдельно и позже выполняет {@link WebhookDispatcher}, читая уже закоммиченные
 * строки. Ценой этого выбора становится доставка «как минимум один раз» вместо
 * «ровно один раз» — её и закрывает {@code eventId} как ключ идемпотентности на
 * стороне подписчика.
 *
 * <p>Вызывать {@link #publish} в собственной транзакции ({@code REQUIRES_NEW})
 * или из метода без транзакции — значит потерять всю гарантию: строка в outbox
 * переживёт откат бизнес-изменения.
 */
@Service
public class OutboxService {

    private final OutboxEventRepository outboxRepository;
    private final WebhookSubscriptionRepository subscriptionRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxEventRepository outboxRepository,
                         WebhookSubscriptionRepository subscriptionRepository,
                         WebhookDeliveryRepository deliveryRepository,
                         Clock clock,
                         ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.deliveryRepository = deliveryRepository;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    /**
     * Записывает событие и разворачивает его в доставки по активным подписчикам.
     *
     * <p>Фан-аут делается здесь, в той же транзакции, а не диспетчером при
     * отправке. Разница принципиальна: список получателей фиксируется на момент
     * события. Иначе подписка, заведённая через час после сбоя, получила бы
     * события того сбоя (мы бы считали получателей заново при каждом тике), а
     * отключённая за минуту до отправки — не получила бы событие, которое уже
     * было ей адресовано, и оператор не увидел бы этого в очереди доставок.
     *
     * @param eventType     тип события — публичный контракт подписчиков
     * @param aggregateType тип изменившегося объекта (alert|incident|station|…)
     * @param aggregateCode стабильный код объекта
     * @param payload       данные события; попадают в тело вебхука как есть
     * @return сохранённое событие; {@code getEventId()} — ключ идемпотентности
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public OutboxEvent publish(WebhookEventType eventType, String aggregateType,
                               String aggregateCode, Map<String, Object> payload) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        OutboxEvent event = new OutboxEvent(UUID.randomUUID(), UUID.randomUUID(), eventType,
                payload, aggregateType, aggregateCode, now, currentTraceId());
        OutboxEvent saved = outboxRepository.save(event);
        String bodySnapshot = serialize(saved);

        List<WebhookSubscription> subscribers = subscriptionRepository.findByActiveTrue().stream()
                .filter(subscription -> subscription.subscribedTo(eventType))
                .toList();
        for (WebhookSubscription subscription : subscribers) {
            deliveryRepository.save(new WebhookDelivery(UUID.randomUUID(), nextDeliveryCode(),
                    saved.getEventId(), subscription.getCode(), saved.getEventType().code(),
                    saved.getAggregateType(), saved.getAggregateCode(), saved.getTraceId(),
                    bodySnapshot, now));
        }
        return saved;
    }

    /**
     * Трасса запроса для INT-05: тот же requestId, что {@code RequestIdFilter}
     * кладёт в MDC и возвращает клиенту в {@code X-Request-Id}. Благодаря этому
     * цепочка «запрос оператора → событие → доставка → лог подписчика» проходится
     * по одному значению.
     *
     * <p>Вне HTTP-запроса (планировщик, миграция данных) MDC пуст — тогда трассу
     * генерируем здесь. Оставить null было бы хуже: событие без трассы нельзя
     * связать с его доставками при разборе.
     */
    private static String currentTraceId() {
        String requestId = MDC.get(RequestIdFilter.ATTRIBUTE);
        return requestId != null && !requestId.isBlank() ? requestId : UUID.randomUUID().toString();
    }

    private String serialize(OutboxEvent event) {
        WebhookEventPayload payload = new WebhookEventPayload(
                event.getEventId().toString(), event.getEventType().code(),
                event.getAggregateType(), event.getAggregateCode(),
                event.getOccurredAt().toInstant(), event.getTraceId(), event.getPayload());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Событие " + event.getEventId() + " не сериализуется в JSON", exception);
        }
    }

    /** Код доставки виден оператору и уходит в URL ручного повтора (U-OPS-04). */
    private static String nextDeliveryCode() {
        return "WHD-" + UUID.randomUUID();
    }
}
