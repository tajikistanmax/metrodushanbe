package tj.metro.dushanbe.integration.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Настройки доставки вебхуков (префикс {@code app.integration}), INT-05. */
@ConfigurationProperties(prefix = "app.integration")
public class IntegrationProperties {

    /** Период опроса очереди доставок диспетчером, мс. */
    private long pollMs = 15_000;

    /** Сколько доставок берётся за один тик — чтобы тик был предсказуемо коротким. */
    private int batchSize = 50;

    /**
     * Потолок попыток, после которого доставка уходит в DLQ (статус dead).
     * 6 попыток с базой 30с и удвоением — это около 30 минут борьбы за доставку;
     * дольше держать сломанного подписчика в живой очереди смысла нет, он займёт
     * батч и задержит остальных.
     */
    private int maxAttempts = 6;

    /** База экспоненциальной задержки: задержка = base * 2^(attempts-1). */
    private Duration retryBaseDelay = Duration.ofSeconds(30);

    /** Потолок задержки — иначе экспонента быстро уходит в часы. */
    private Duration retryMaxDelay = Duration.ofMinutes(30);

    /** Таймаут HTTP-запроса к подписчику; должен быть заметно меньше pollMs. */
    private Duration timeout = Duration.ofSeconds(10);

    /** Lease worker-а; должна быть заметно длиннее сетевого таймаута. */
    private Duration claimLease = Duration.ofSeconds(30);

    public long getPollMs() {
        return pollMs;
    }

    public void setPollMs(long pollMs) {
        this.pollMs = pollMs;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public Duration getRetryBaseDelay() {
        return retryBaseDelay;
    }

    public void setRetryBaseDelay(Duration retryBaseDelay) {
        this.retryBaseDelay = retryBaseDelay;
    }

    public Duration getRetryMaxDelay() {
        return retryMaxDelay;
    }

    public void setRetryMaxDelay(Duration retryMaxDelay) {
        this.retryMaxDelay = retryMaxDelay;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public Duration getClaimLease() {
        return claimLease;
    }

    public void setClaimLease(Duration claimLease) {
        this.claimLease = claimLease;
    }
}
