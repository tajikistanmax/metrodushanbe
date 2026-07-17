package tj.metro.dushanbe.notification.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import tj.metro.dushanbe.notification.domain.NotificationTarget;
import tj.metro.dushanbe.notification.domain.TargetType;

/** Доступ к адресации рассылок (NTF-02). */
public interface NotificationTargetRepository extends JpaRepository<NotificationTarget, UUID> {

    /** Адресация одной рассылки. Пустой список = рассылка на всю сеть. */
    List<NotificationTarget> findByMessageId(UUID messageId);

    /**
     * Обратный поиск «какие рассылки касаются объекта X» — под
     * ix_notification_target_lookup (target_type, target_code).
     */
    List<NotificationTarget> findByTargetTypeAndTargetCode(TargetType targetType, String targetCode);
}
