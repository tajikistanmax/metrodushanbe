package tj.metro.dushanbe.config.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

class CacheHealthIndicatorTest {

    @Test
    void healthReturnsUpWhenPingSucceeds() {
        var connectionFactory = mock(RedisConnectionFactory.class);
        var connection = mock(RedisConnection.class);

        when(connectionFactory.getConnection()).thenReturn(connection);
        when(connection.ping()).thenReturn("PONG");

        var indicator = new CacheHealthIndicator(connectionFactory);
        Health health = indicator.health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals("Redis", health.getDetails().get("cache"));
    }

    @Test
    void healthReturnsDownWhenPingFails() {
        var connectionFactory = mock(RedisConnectionFactory.class);
        var connection = mock(RedisConnection.class);

        when(connectionFactory.getConnection()).thenReturn(connection);
        when(connection.ping()).thenThrow(new RedisSystemException("Connection timeout", new RuntimeException()));

        var indicator = new CacheHealthIndicator(connectionFactory);
        Health health = indicator.health();

        assertEquals(Status.DOWN, health.getStatus());
        assertNotNull(health.getDetails().get("error"));
    }

    @Test
    void healthReturnsUnknownWhenFactoryIsNull() {
        var indicator = new CacheHealthIndicator(null);
        Health health = indicator.health();

        assertEquals(Status.UNKNOWN, health.getStatus());
        assertEquals("Redis not configured", health.getDetails().get("message"));
    }
}
