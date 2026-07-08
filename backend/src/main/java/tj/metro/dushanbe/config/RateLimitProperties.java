package tj.metro.dushanbe.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;
    private int capacity = 100;
    private int refillPerMinute = 100;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public int getRefillPerMinute() {
        return refillPerMinute;
    }

    public void setRefillPerMinute(int refillPerMinute) {
        this.refillPerMinute = refillPerMinute;
    }
}
