package tj.metro.dushanbe.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tj.metro.dushanbe.admin.service.AdminNotificationService;
import tj.metro.dushanbe.featureflag.service.FeatureFlagService;

/**
 * Отложенная публикация рассылок (NTF-05): периодически забирает запланированные
 * рассылки, чьё время наступило, и отправляет их.
 *
 * <p><b>Флаг фичи.</b> Автоотправка выключена по умолчанию
 * ({@code notification.scheduler}, default false): фоновый процесс, сам рассылающий
 * сообщения пассажирам, должен включаться осознанно, а не появляться вместе с
 * деплоем. Флаг читается на каждом тике, а не при старте, — иначе выключить
 * разошедшуюся рассылку можно было бы только рестартом.
 *
 * <p><b>О направлении зависимости.</b> Планировщик живёт в модуле notification, но
 * вызывает {@link AdminNotificationService}: отправка — это write-операция со всеми
 * её гейтами, переходами и аудитом, и дублировать её здесь значило бы завести
 * второй путь отправки, который со временем разойдётся с первым. Актором в аудите
 * идёт {@link #ACTOR} — так в журнале видно, что рассылку отправил не человек.
 */
@Component
public class NotificationScheduler {

    /** Актор аудита для автоматической отправки — отличим от оператора-человека. */
    public static final String ACTOR = "scheduler";

    private static final Logger LOG = LoggerFactory.getLogger(NotificationScheduler.class);

    private final AdminNotificationService notificationService;
    private final FeatureFlagService featureFlagService;
    private final int batchSize;

    public NotificationScheduler(AdminNotificationService notificationService,
                                 FeatureFlagService featureFlagService,
                                 @Value("${app.notifications.batch-size:50}") int batchSize) {
        this.notificationService = notificationService;
        this.featureFlagService = featureFlagService;
        this.batchSize = batchSize;
    }

    /**
     * Тик планировщика. {@code fixedDelay} (а не {@code fixedRate}) — чтобы тики не
     * накладывались друг на друга: две параллельные отправки одной рассылки создали
     * бы двойной комплект доставок.
     */
    @Scheduled(fixedDelayString = "${app.notifications.poll-ms:30000}")
    public void publishDue() {
        if (!featureFlagService.isEnabled("notification.scheduler", false)) {
            return;
        }
        for (int index = 0; index < batchSize; index++) {
            try {
                if (!notificationService.sendNextDue(ACTOR)) {
                    break;
                }
            } catch (RuntimeException e) {
                LOG.warn("Не удалось отправить очередную рассылку по расписанию: {}", e.getMessage());
                // После rollback та же самая earliest due-строка снова стала бы первой;
                // продолжение цикла лишь повторило бы её batchSize раз в одном тике.
                break;
            }
        }
    }
}
