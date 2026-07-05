package tj.metro.dushanbe.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Единый источник времени приложения. Отдельный бин — чтобы сервисы,
 * зависящие от «сейчас» (окна действия alerts и т.п.), были детерминированно
 * тестируемыми через {@code Clock.fixed(...)}.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }
}
