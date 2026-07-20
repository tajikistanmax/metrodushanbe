package tj.metro.dushanbe.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * Аудит §23: production не должен применять демо-сид. Тесты фиксируют fail-closed
 * поведение кастомайзера, а не только форматирование лога.
 */
class DemoSeedGuardTest {

    private static DemoSeedGuard guard(String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return new DemoSeedGuard(environment);
    }

    private static FluentConfiguration flywayWith(String... locations) {
        return new FluentConfiguration().locations(locations);
    }

    @Test
    void productionWithSeedLocationRefusesToStart() {
        DemoSeedGuard guard = guard("prod");
        FluentConfiguration configuration = flywayWith("classpath:db/migration", "classpath:db/seed");

        IllegalStateException failure =
                assertThrows(IllegalStateException.class, () -> guard.customize(configuration));

        assertTrue(failure.getMessage().contains("db/seed"));
    }

    @Test
    void productionWithoutSeedLocationStartsNormally() {
        DemoSeedGuard guard = guard("prod");
        FluentConfiguration configuration = flywayWith("classpath:db/migration");

        assertDoesNotThrow(() -> guard.customize(configuration));
    }

    @Test
    void developmentKeepsSeedLocationEnabled() {
        DemoSeedGuard guard = guard();
        FluentConfiguration configuration = flywayWith("classpath:db/migration", "classpath:db/seed");

        assertDoesNotThrow(() -> guard.customize(configuration));
    }

    @Test
    void seedLocationIsDetectedRegardlessOfPrefixAndCase() {
        assertTrue(DemoSeedGuard.containsSeedLocation(List.of("classpath:db/seed")));
        assertTrue(DemoSeedGuard.containsSeedLocation(List.of("filesystem:src/main/resources/DB/Seed")));
        assertTrue(DemoSeedGuard.containsSeedLocation(List.of("classpath:db/migration", "classpath:db/seed/")));
    }

    @Test
    void schemaOnlyLocationsAreNotTreatedAsSeed() {
        assertFalse(DemoSeedGuard.containsSeedLocation(List.of("classpath:db/migration")));
        assertFalse(DemoSeedGuard.containsSeedLocation(null));
        assertFalse(DemoSeedGuard.containsSeedLocation(List.of()));
    }
}
