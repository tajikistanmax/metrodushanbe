package tj.metro.dushanbe.integration.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import tj.metro.dushanbe.integration.domain.TrainPosition;

/** Доступ к телеметрии положения поездов (U-INT-04). */
public interface TrainPositionRepository extends JpaRepository<TrainPosition, UUID> {

    Optional<TrainPosition> findFirstByTrainCodeOrderByReportedAtDesc(String trainCode);

    /**
     * Срез «где поезда линии сейчас»: по одному, самому свежему замеру на поезд.
     *
     * <p>Это агрегат (максимум по группе), поэтому derived query здесь не хватает.
     * Сравнение по reported_at, а не по received_at: если платформа прислала
     * пакет с опозданием, показывать надо более поздний ЗАМЕР, а не тот, что
     * позже дошёл — иначе поезд на карте прыгнет назад.
     */
    @Query("""
            SELECT p
            FROM TrainPosition p
            WHERE p.lineCode = :lineCode
              AND p.reportedAt = (
                  SELECT MAX(latest.reportedAt)
                  FROM TrainPosition latest
                  WHERE latest.trainCode = p.trainCode
                    AND latest.lineCode = :lineCode
              )
            ORDER BY p.trainCode
            """)
    List<TrainPosition> findLatestByLine(String lineCode);
}
