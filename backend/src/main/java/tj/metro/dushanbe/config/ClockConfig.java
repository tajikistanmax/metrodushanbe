package tj.metro.dushanbe.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Единый источник времени приложения. Отдельный бин — чтобы сервисы,
 * зависящие от «сейчас» (окна действия alerts и т.п.), были детерминированно
 * тестируемыми через {@code Clock.fixed(...)}.
 *
 * <p><b>Почему зона эксплуатации, а не UTC.</b> Часть решений опирается не на
 * момент времени, а на КАЛЕНДАРНУЮ ДАТУ и время суток в городе: тип дня
 * расписания (будни/выходной/праздник), исключения календаря, окна действия
 * уведомлений. С {@code systemUTC()} между 00:00 и 05:00 по Душанбе дата в UTC
 * ещё вчерашняя — то есть ночью пассажир получал бы расписание за прошлый день,
 * а праздник начинался бы на пять часов позже полуночи.
 *
 * <p>На хранение это не влияет: колонки {@code timestamptz} хранят момент, а не
 * смещение. Меняется только то, какую дату видит бизнес-логика, вызывая
 * {@code LocalDate.now(clock)}.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock systemClock() {
        return Clock.system(ZoneId.of("Asia/Dushanbe"));
    }
}
