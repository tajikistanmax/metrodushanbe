package tj.metro.dushanbe.featureflag.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tj.metro.dushanbe.featureflag.config.FeatureFlagProperties;
import tj.metro.dushanbe.featureflag.domain.FeatureFlag;
import tj.metro.dushanbe.featureflag.repository.FeatureFlagRepository;

@Service
public class FeatureFlagService {

    private final FeatureFlagRepository repository;
    private final FeatureFlagProperties properties;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public FeatureFlagService(FeatureFlagRepository repository, FeatureFlagProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Cacheable(value = "feature.flags", key = "#flagKey")
    public boolean isEnabled(String flagKey) {
        return fetch(flagKey).map(FeatureFlag::isEnabled).orElse(false);
    }

    public boolean isEnabled(String flagKey, boolean defaultValue) {
        return fetch(flagKey).map(FeatureFlag::isEnabled).orElse(defaultValue);
    }

    @Transactional
    @CacheEvict(value = "feature.flags", key = "#flagKey")
    public void setEnabled(String flagKey, boolean enabled, String updatedBy) {
        FeatureFlag flag = repository.findById(flagKey)
                .orElseGet(() -> new FeatureFlag(flagKey, enabled, null, null, null));
        flag.setEnabled(enabled);
        flag.setUpdatedBy(updatedBy);
        flag.setUpdatedAt(java.time.OffsetDateTime.now());
        repository.save(flag);
        cache.remove(flagKey);
    }

    public java.util.List<FeatureFlag> findAll() {
        return repository.findAll();
    }

    private Optional<FeatureFlag> fetch(String flagKey) {
        CacheEntry entry = cache.get(flagKey);
        if (entry != null && !entry.isExpired(properties.getCacheTtlSeconds())) {
            return Optional.of(entry.flag());
        }
        Optional<FeatureFlag> flag = repository.findById(flagKey);
        flag.ifPresent(f -> cache.put(flagKey, new CacheEntry(f, Instant.now())));
        return flag;
    }

    private record CacheEntry(FeatureFlag flag, Instant cachedAt) {
        boolean isExpired(int ttlSeconds) {
            return Duration.between(cachedAt, Instant.now()).getSeconds() >= ttlSeconds;
        }
    }
}
