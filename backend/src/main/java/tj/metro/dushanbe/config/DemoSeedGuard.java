package tj.metro.dushanbe.config;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Делает видимым и проверяемым главный вопрос выпуска (аудит §23): применяются ли
 * в этой среде демонстрационные данные.
 *
 * <p><b>Почему это вообще нужно.</b> Демо-сид ({@code classpath:db/seed}) содержит
 * выдуманные линии, станции, выходы, новости, расписания и тарифы. На
 * государственном портале метро они были бы опубликованы как ОФИЦИАЛЬНЫЕ данные.
 * Раньше отличие production от dev держалось на одной незаметной строке
 * конфигурации; молчаливое переключение здесь хуже отсутствия защиты, потому что
 * ошибку никто не заметит до публикации.
 *
 * <p><b>Почему {@link FlywayConfigurationCustomizer}, а не {@code @PostConstruct}.</b>
 * Кастомайзер вызывается Flyway-автоконфигурацией с УЖЕ разрешёнными locations
 * (после биндинга свойств и любых override через {@code SPRING_FLYWAY_LOCATIONS})
 * и строго ДО {@code migrate()}. Поэтому в профиле {@code prod} исключение здесь
 * гарантированно предотвращает применение seed, а не ловит его постфактум. Падение
 * на старте дешевле, чем опубликованные фиктивные данные.
 *
 * <p>Вне production кастомайзер лишь пишет в лог однозначную строку о режиме данных
 * и ничего не меняет в конфигурации Flyway.
 */
@Component
public class DemoSeedGuard implements FlywayConfigurationCustomizer {

    /** Каталог демо-сида; должен совпадать с locations в application.yml. */
    static final String SEED_LOCATION = "db/seed";

    private static final String PROD_PROFILE = "prod";

    private static final Logger LOG = LoggerFactory.getLogger(DemoSeedGuard.class);

    private final Environment environment;

    public DemoSeedGuard(Environment environment) {
        this.environment = environment;
    }

    /**
     * Возвращает {@code true}, если среди locations есть каталог демо-сида.
     * Сравнение по подстроке, а не по полному равенству: Flyway допускает и
     * {@code classpath:db/seed}, и {@code filesystem:...}, и завершающий слэш.
     */
    static boolean containsSeedLocation(List<String> locations) {
        if (locations == null) {
            return false;
        }
        return locations.stream()
                .filter(location -> location != null)
                .map(location -> location.toLowerCase(Locale.ROOT))
                .anyMatch(location -> location.contains(SEED_LOCATION));
    }

    @Override
    public void customize(FluentConfiguration configuration) {
        List<String> locations = Arrays.stream(configuration.getLocations())
                .map(location -> location.getDescriptor())
                .toList();
        boolean seedEnabled = containsSeedLocation(locations);
        boolean production = environment.matchesProfiles(PROD_PROFILE);

        if (production && seedEnabled) {
            throw new IllegalStateException(
                    "Профиль prod с включённым демо-сидом (" + SEED_LOCATION + ") в spring.flyway.locations: "
                            + locations
                            + ". Демонстрационные линии, станции, новости, расписания и тарифы"
                            + " недопустимы в production — уберите seed-каталог из locations.");
        }

        if (seedEnabled) {
            LOG.warn("Данные: ДЕМОНСТРАЦИОННЫЕ. Flyway locations={} — применяется демо-сид"
                    + " (выдуманные линии, станции, новости, расписания и тарифы)."
                    + " Эта конфигурация недопустима в production.", locations);
        } else {
            LOG.info("Данные: только реальные. Flyway locations={} — демо-сид ({}) НЕ применяется.",
                    locations, SEED_LOCATION);
        }
    }
}
