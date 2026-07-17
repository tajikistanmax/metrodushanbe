package tj.metro.dushanbe.notification.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Включает планировщик Spring для отложенной публикации рассылок (NTF-05).
 *
 * <p>Общего {@code @EnableScheduling} в {@code config/} нет, поэтому планировщик
 * включается здесь, в своём модуле.
 *
 * <p><b>Имя класса намеренно уникальное.</b> Такую же аннотацию несёт
 * {@code integration/config/IntegrationConfig}: сама повторная {@code @EnableScheduling}
 * безопасна (Spring регистрирует инфраструктуру планировщика один раз), а вот два
 * @Configuration-класса с ОДИНАКОВЫМ простым именем дали бы конфликт имён бинов и
 * уронили контекст. Отсюда {@code NotificationSchedulingConfig}, а не обобщённое
 * {@code SchedulingConfig}.
 */
@Configuration
@EnableScheduling
public class NotificationSchedulingConfig {
}
