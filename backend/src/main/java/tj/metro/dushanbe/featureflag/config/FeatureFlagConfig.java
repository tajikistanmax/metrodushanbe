package tj.metro.dushanbe.featureflag.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(FeatureFlagProperties.class)
public class FeatureFlagConfig {
}
