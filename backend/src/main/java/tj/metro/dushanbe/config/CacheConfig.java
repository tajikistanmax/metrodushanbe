package tj.metro.dushanbe.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

/**
 * Кэширование публичных read-эндпоинтов через Redis (@Cacheable в NetworkService,
 * AlertService, RoutingService, ScheduleService, FeatureFlagService).
 *
 * <p><b>Устойчивость к недоступности Redis.</b> Кэш — оптимизация, а не источник истины,
 * поэтому его сбой не должен ронять запросы. {@link CacheErrorHandler} ниже логирует и
 * <i>подавляет</i> ошибки get/put/evict/clear: при недоступном/не сконфигурированном Redis
 * методы работают «мимо кэша» (как cache miss), а не отдают 500. Это же делает
 * интеграционные тесты (без Redis в контексте) и локальный запуск без Redis рабочими.
 */
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    private static final Logger LOG = LoggerFactory.getLogger(CacheConfig.class);

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory,
                                     ObjectMapper objectMapper) {
        GenericJackson2JsonRedisSerializer serializer =
                GenericJackson2JsonRedisSerializer.builder()
                        .objectMapper(objectMapper.copy())
                        .defaultTyping(true)
                        .build();
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        serializer))
                .disableCachingNullValues();

        Map<String, RedisCacheConfiguration> cacheConfigurations = Map.of(
                "lines", defaultConfig.entryTtl(Duration.ofHours(1)),
                "stations", defaultConfig.entryTtl(Duration.ofHours(1)),
                "network.geojson", defaultConfig.entryTtl(Duration.ofHours(6)),
                "alerts", defaultConfig.entryTtl(Duration.ofMinutes(10)),
                "news", defaultConfig.entryTtl(Duration.ofMinutes(30)),
                "schedules", defaultConfig.entryTtl(Duration.ofMinutes(30)),
                "routes", defaultConfig.entryTtl(Duration.ofMinutes(15)),
                "feature.flags", defaultConfig.entryTtl(Duration.ofMinutes(1)),
                "fares", defaultConfig.entryTtl(Duration.ofMinutes(30)));

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }

    /**
     * Обработчик ошибок кэша: логирует и подавляет сбои Redis, чтобы @Cacheable/@CacheEvict
     * не пробрасывали {@code RedisConnectionFailureException} и т.п. как 500. При ошибке
     * чтения метод выполняется как при промахе; при ошибке записи/инвалидации — просто
     * пропускаем операцию с кэшем.
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException ex, Cache cache, Object key) {
                LOG.warn("Redis недоступен при чтении кэша (cache={}, key={}): {} — работаем мимо кэша",
                        cache.getName(), key, ex.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException ex, Cache cache, Object key, Object value) {
                LOG.warn("Redis недоступен при записи в кэш (cache={}, key={}): {}",
                        cache.getName(), key, ex.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException ex, Cache cache, Object key) {
                LOG.warn("Redis недоступен при инвалидации кэша (cache={}, key={}): {}",
                        cache.getName(), key, ex.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException ex, Cache cache) {
                LOG.warn("Redis недоступен при очистке кэша (cache={}): {}", cache.getName(), ex.getMessage());
            }
        };
    }
}
