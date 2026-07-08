package tj.metro.dushanbe.featureflag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.feature-flags")
public class FeatureFlagProperties {

    private int cacheTtlSeconds = 60;

    public int getCacheTtlSeconds() {
        return cacheTtlSeconds;
    }

    public void setCacheTtlSeconds(int cacheTtlSeconds) {
        this.cacheTtlSeconds = cacheTtlSeconds;
    }
}
