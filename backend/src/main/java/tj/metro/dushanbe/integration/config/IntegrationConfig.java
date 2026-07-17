package tj.metro.dushanbe.integration.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Конфигурация модуля интеграций.
 *
 * <p>{@code @EnableScheduling} включён здесь, а не в общем {@code config/}: на
 * момент написания планировщик в проекте не был включён нигде, а
 * {@link tj.metro.dushanbe.integration.service.WebhookDispatcher} без него просто
 * не запускается. Трогать чужие конфигурации ради этого не нужно —
 * {@code @EnableScheduling} идемпотентен: если такой же аннотацией параллельно
 * помечен другой {@code @Configuration} (например, в модуле notification),
 * контекст поднимется штатно, потому что аннотация лишь регистрирует
 * инфраструктурный бин планировщика с фиксированным именем, а не создаёт новый
 * при каждом объявлении. Опасен был бы одноимённый {@code @Bean}-метод в двух
 * конфигурациях — таких здесь нет намеренно.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(IntegrationProperties.class)
public class IntegrationConfig {
}
