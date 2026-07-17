package tj.metro.dushanbe.integration.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tj.metro.dushanbe.common.error.RequestIdFilter;
import tj.metro.dushanbe.featureflag.service.FeatureFlagService;
import tj.metro.dushanbe.integration.config.IntegrationProperties;
import tj.metro.dushanbe.integration.domain.WebhookDelivery;
import tj.metro.dushanbe.integration.domain.WebhookSubscription;
import tj.metro.dushanbe.integration.repository.WebhookDeliveryRepository;
import tj.metro.dushanbe.integration.repository.WebhookSubscriptionRepository;
import tj.metro.dushanbe.integration.security.WebhookTargetPolicy;

/**
 * Асинхронная доставка событий подписчикам (INT-02, INT-03, INT-05).
 *
 * <p>Читает уже закоммиченные строки {@code webhook_delivery} — то есть работает
 * строго после того, как бизнес-транзакция зафиксировалась (см. {@link OutboxService}).
 *
 * <p><b>Почему у тика нет {@code @Transactional}.</b> Транзакция на весь тик
 * держала бы соединение с БД открытым всё время сетевых вызовов — а это до
 * {@code batchSize} × {@code timeout} секунд на сломанном подписчике. Пул
 * соединений закончился бы раньше, чем очередь. Поэтому сущности читаются вне
 * транзакции, а каждое изменение статуса сохраняется отдельным
 * {@code repository.save(...)} — короткой транзакцией самого репозитория, уже
 * после того, как HTTP завершился.
 *
 * <p><b>Retry (INT-05).</b> Экспоненциальная задержка {@code base × 2^(attempts-1)}
 * с потолком {@code retryMaxDelay}; исчерпание {@code maxAttempts} переводит
 * доставку в {@code dead} — это и есть DLQ. Дальше её судьбу решает оператор
 * (U-OPS-04): чинит подписчика и жмёт «повторить».
 */
@Service
public class WebhookDispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(WebhookDispatcher.class);

    /** Флаг модуля (ADM-06). Выключен по умолчанию — наружу молча не ходим. */
    private static final String FEATURE_FLAG = "integration.webhooks";

    /** Длина, до которой обрезается текст ошибки: last_error — не место для стектрейса. */
    private static final int ERROR_LIMIT = 1000;

    private final WebhookDeliveryRepository deliveryRepository;
    private final WebhookSubscriptionRepository subscriptionRepository;
    private final FeatureFlagService featureFlagService;
    private final IntegrationProperties properties;
    private final RestClient restClient;
    private final Clock clock;
    private final WebhookTargetPolicy targetPolicy;
    private final WebhookDeliveryClaimer claimer;

    public WebhookDispatcher(WebhookDeliveryRepository deliveryRepository,
                             WebhookSubscriptionRepository subscriptionRepository,
                             FeatureFlagService featureFlagService,
                             IntegrationProperties properties,
                             RestClient.Builder restClientBuilder,
                             Clock clock,
                             WebhookTargetPolicy targetPolicy,
                             WebhookDeliveryClaimer claimer) {
        this.deliveryRepository = deliveryRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.featureFlagService = featureFlagService;
        this.properties = properties;
        this.clock = clock;
        this.targetPolicy = targetPolicy;
        this.claimer = claimer;
        // Таймауты обязательны: без них зависший подписчик держит поток
        // планировщика бесконечно, и очередь встаёт целиком. Приём — как в LlmClient.
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getTimeout());
        this.restClient = restClientBuilder
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * Тик планировщика: забирает доставки с наступившим сроком и везёт их.
     *
     * <p>{@code fixedDelay}, а не {@code fixedRate}: следующий тик стартует через
     * заданный интервал ПОСЛЕ завершения предыдущего. При {@code fixedRate}
     * медленный подписчик заставил бы тики накладываться друг на друга и слать
     * одну и ту же доставку дважды.
     */
    @Scheduled(fixedDelayString = "${app.integration.poll-ms:15000}")
    public void dispatchDue() {
        if (!featureFlagService.isEnabled(FEATURE_FLAG, false)) {
            return;
        }
        for (int index = 0; index < properties.getBatchSize(); index++) {
            OffsetDateTime now = OffsetDateTime.now(clock);
            Optional<WebhookDelivery> claimed = claimer.claimNext(
                    now, now.plus(properties.getClaimLease()));
            if (claimed.isEmpty()) {
                break;
            }
            WebhookDelivery delivery = claimed.get();
            try {
                deliver(delivery);
            } catch (RuntimeException exception) {
                // Ни одна доставка не должна уронить тик: иначе один битый
                // подписчик заблокирует очередь для всех остальных.
                LOG.error("Сбой обработки доставки {}: {}", delivery.getCode(),
                        exception.getMessage(), exception);
            }
        }
    }

    /**
     * Одна попытка доставки. Публичный метод, а не приватный: им же пользуются
     * тесты и (в перспективе) ручной прогон одной доставки из консоли.
     */
    public void deliver(WebhookDelivery delivery) {
        Optional<WebhookSubscription> subscription =
                subscriptionRepository.findByCode(delivery.getSubscriptionCode());
        if (subscription.isEmpty()) {
            // Подписку удалили, пока доставка ждала своего часа. Везти некому и
            // retry не поможет — сразу в DLQ, чтобы не крутить её вечно.
            fail(delivery, "Подписчик '" + delivery.getSubscriptionCode() + "' не найден", null, true);
            return;
        }
        WebhookSubscription target = subscription.get();
        if (!target.isActive()) {
            fail(delivery, "Подписчик '" + target.getCode() + "' отключён", null, true);
            return;
        }
        final URI targetUri;
        try {
            targetUri = targetPolicy.validateForDispatch(target.getTargetUrl());
        } catch (WebhookTargetPolicy.UnsafeTargetException exception) {
            fail(delivery, "Unsafe webhook target: " + exception.getMessage(), null, true);
            return;
        }
        if (rateLimited(target)) {
            // Не провал, а выдержка: попытку не тратим (ADM-06).
            delivery.deferTo(OffsetDateTime.now(clock).plusMinutes(1));
            deliveryRepository.save(delivery);
            return;
        }
        send(delivery, target, targetUri);
    }

    private void send(WebhookDelivery delivery, WebhookSubscription subscription, URI targetUri) {
        String body = delivery.getBodySnapshot();
        String traceId = delivery.getTraceIdSnapshot();
        // Трасса в MDC на время попытки: наши логи доставки встанут в ту же
        // цепочку, что и исходный запрос оператора (INT-05).
        String previousTrace = MDC.get(RequestIdFilter.ATTRIBUTE);
        if (traceId != null) {
            MDC.put(RequestIdFilter.ATTRIBUTE, traceId);
        }
        try {
            ResponseEntity<Void> response = restClient.post()
                    .uri(targetUri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(WebhookSignature.SIGNATURE_HEADER,
                            WebhookSignature.sign(subscription.getSecretHash(), body))
                    .header(WebhookSignature.EVENT_ID_HEADER, delivery.getEventId().toString())
                    .header(WebhookSignature.EVENT_TYPE_HEADER, delivery.getEventTypeSnapshot())
                    .header(WebhookSignature.TRACE_ID_HEADER, traceId)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            if (!response.getStatusCode().is2xxSuccessful()) {
                fail(delivery, "Webhook redirects are not allowed", response.getStatusCode().value(), true);
                return;
            }
            delivery.markSent(response.getStatusCode().value());
            deliveryRepository.save(delivery);
            LOG.info("Вебхук {} доставлен подписчику {} с попытки {}", delivery.getEventId(),
                    subscription.getCode(), delivery.getAttempts());
        } catch (RestClientResponseException exception) {
            // Подписчик ответил, но не 2xx — статус знаем и сохраняем: 401 и 503
            // требуют от оператора совершенно разных действий.
            fail(delivery, exception.getStatusCode() + " " + trim(exception.getResponseBodyAsString()),
                    exception.getStatusCode().value(), false);
        } catch (RestClientException exception) {
            // Не дозвонились вовсе: таймаут, DNS, отказ соединения.
            fail(delivery, trim(String.valueOf(exception.getMessage())), null, false);
        } finally {
            if (previousTrace != null) {
                MDC.put(RequestIdFilter.ATTRIBUTE, previousTrace);
            } else {
                MDC.remove(RequestIdFilter.ATTRIBUTE);
            }
        }
    }

    /**
     * Фиксирует неудачу: либо ждём следующей попытки, либо DLQ.
     *
     * @param terminal провал, который повтором не лечится (нет подписчика,
     *                 нет события) — такие сразу в dead, без сжигания попыток
     */
    private void fail(WebhookDelivery delivery, String error, Integer httpStatus, boolean terminal) {
        String message = trim(error);
        if (terminal || delivery.getAttempts() + 1 >= properties.getMaxAttempts()) {
            delivery.markDead(message, httpStatus);
            LOG.warn("Доставка {} исчерпала попытки и ушла в DLQ: {}", delivery.getCode(), message);
        } else {
            delivery.markFailed(message, httpStatus, nextAttemptAt(delivery.getAttempts() + 1));
            LOG.info("Доставка {} провалилась (попытка {}), следующая в {}: {}", delivery.getCode(),
                    delivery.getAttempts(), delivery.getNextAttemptAt(), message);
        }
        deliveryRepository.save(delivery);
    }

    /**
     * Экспоненциальная задержка: {@code base × 2^(attempts-1)}, но не больше
     * потолка. Потолок обязателен — без него шестая попытка ушла бы на часы, и
     * событие, которое подписчик ждал «в течение минуты», приехало бы к вечеру.
     */
    private OffsetDateTime nextAttemptAt(int attempts) {
        long baseSeconds = properties.getRetryBaseDelay().toSeconds();
        long maxSeconds = properties.getRetryMaxDelay().toSeconds();
        int exponent = Math.max(0, attempts - 1);
        // Считаем в long с ранней отсечкой: 2^attempts при большом maxAttempts
        // переполнил бы int и дал бы отрицательную задержку.
        long seconds = exponent >= 32 ? maxSeconds : Math.min(maxSeconds, baseSeconds << exponent);
        return OffsetDateTime.now(clock).plus(Duration.ofSeconds(seconds));
    }

    /**
     * Упёрлись ли в лимит подписчика (ADM-06). Окно скользящее и считается по
     * фактическим попыткам за последнюю минуту — приблизительно, но этого
     * достаточно: лимит защищает подписчика от всплеска, а не считает деньги.
     */
    private boolean rateLimited(WebhookSubscription subscription) {
        OffsetDateTime since = OffsetDateTime.now(clock).minusMinutes(1);
        long recent = deliveryRepository.countBySubscriptionCodeAndUpdatedAtAfter(
                subscription.getCode(), since);
        return recent >= subscription.getRateLimitPerMinute();
    }

    private static String trim(String value) {
        if (value == null || value.isBlank()) {
            return "Неизвестная ошибка доставки";
        }
        return value.length() <= ERROR_LIMIT ? value : value.substring(0, ERROR_LIMIT);
    }
}
