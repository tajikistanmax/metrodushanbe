package tj.metro.dushanbe.featureflag.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import tj.metro.dushanbe.featureflag.config.FeatureFlagProperties;
import tj.metro.dushanbe.featureflag.domain.FeatureFlag;
import tj.metro.dushanbe.featureflag.repository.FeatureFlagRepository;

class FeatureFlagServiceTest {

    private final FeatureFlagRepository repository = mock(FeatureFlagRepository.class);
    private final FeatureFlagProperties properties = new FeatureFlagProperties();
    private final FeatureFlagService service = new FeatureFlagService(repository, properties);

    @Test
    void isEnabledReturnsTrueForEnabledFlag() {
        FeatureFlag flag = new FeatureFlag("test-feature", true, null, null, null);
        when(repository.findById("test-feature")).thenReturn(Optional.of(flag));

        boolean result = service.isEnabled("test-feature");

        assertTrue(result);
    }

    @Test
    void isEnabledReturnsFalseForDisabledFlag() {
        FeatureFlag flag = new FeatureFlag("test-disabled", false, null, null, null);
        when(repository.findById("test-disabled")).thenReturn(Optional.of(flag));

        boolean result = service.isEnabled("test-disabled");

        assertFalse(result);
    }

    @Test
    void isEnabledWithDefaultValueReturnsDefaultWhenNotFound() {
        when(repository.findById("nonexistent")).thenReturn(Optional.empty());

        boolean result = service.isEnabled("nonexistent", true);

        assertTrue(result);
    }

    @Test
    void setEnabledUpdatesFlagAndRemovesCacheEntry() {
        FeatureFlag flag = new FeatureFlag("cached-flag", false, null, null, null);
        when(repository.findById("cached-flag")).thenReturn(Optional.of(flag));
        when(repository.save(flag)).thenReturn(flag);

        service.isEnabled("cached-flag");
        assertFalse(service.isEnabled("cached-flag"));

        service.setEnabled("cached-flag", true, "admin");

        assertTrue(flag.isEnabled());
        verify(repository).save(flag);
    }
}
