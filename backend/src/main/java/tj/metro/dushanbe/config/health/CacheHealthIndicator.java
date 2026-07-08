package tj.metro.dushanbe.config.health;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

@Component
public class CacheHealthIndicator implements HealthIndicator {

    private final RedisConnectionFactory redisConnectionFactory;

    @Autowired(required = false)
    public CacheHealthIndicator(RedisConnectionFactory redisConnectionFactory) {
        this.redisConnectionFactory = redisConnectionFactory;
    }

    @Override
    public Health health() {
        if (redisConnectionFactory == null) {
            return Health.unknown().withDetail("message", "Redis not configured").build();
        }
        try (var connection = redisConnectionFactory.getConnection()) {
            connection.ping();
            return Health.up().withDetail("cache", "Redis").build();
        } catch (RedisSystemException e) {
            return Health.down(e).build();
        }
    }
}
