package tj.metro.dushanbe.notification.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import tj.metro.dushanbe.notification.domain.NotificationTemplate;

/** Доступ к заготовкам текстов рассылок (NTF-05). */
public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, UUID> {

    Optional<NotificationTemplate> findByCode(String code);

    boolean existsByCode(String code);

    List<NotificationTemplate> findAllByOrderByCodeAsc();

    /** Только те, из которых сейчас разрешено создавать рассылки. */
    List<NotificationTemplate> findByActiveTrueOrderByCodeAsc();
}
