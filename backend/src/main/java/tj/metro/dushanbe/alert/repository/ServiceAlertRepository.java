package tj.metro.dushanbe.alert.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tj.metro.dushanbe.alert.domain.ServiceAlert;

/**
 * Доступ к сервисным уведомлениям.
 */
public interface ServiceAlertRepository extends JpaRepository<ServiceAlert, UUID> {

    /**
     * Активные published-уведомления в окне действия на момент {@code now},
     * вместе с таргетами (fetch join — без N+1). Предикаты окна вычисляются
     * в БД и опираются на индекс {@code ix_service_alert_status_window} (V003) —
     * выборка ограничена активными записями, а не всеми published.
     * «Сейчас» передаёт сервис из инжектированного {@code Clock},
     * поэтому логика времени остаётся тестируемой.
     * Сортировку и фильтры severity/таргетов применяет {@code AlertService}.
     */
    @Query("""
            select distinct a from ServiceAlert a
            left join fetch a.targets
            where a.status = 'published'
              and a.startsAt <= :now
              and (a.endsAt is null or a.endsAt > :now)
            """)
    List<ServiceAlert> findActivePublished(@Param("now") OffsetDateTime now);
}
