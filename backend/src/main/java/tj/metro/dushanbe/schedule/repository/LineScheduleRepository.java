package tj.metro.dushanbe.schedule.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tj.metro.dushanbe.schedule.domain.LineSchedule;

/**
 * Доступ к статическим графикам движения линий.
 */
public interface LineScheduleRepository extends JpaRepository<LineSchedule, UUID> {

    /**
     * Действующие на дату {@code onDate} графики линии для типа дня, свежие раньше:
     * {@code effective_from <= onDate AND (effective_to IS NULL OR effective_to >= onDate)},
     * упорядочены по {@code effective_from} по убыванию — актуальная версия первой
     * (BR-SCH-1: приоритет у более свежего источника; версии графика — SCH-06).
     * «Сегодня» передаёт сервис из инжектированного {@code Clock} — для тестируемости.
     */
    @Query("""
            select s from LineSchedule s
            where s.lineCode = :lineCode
              and s.dayType = :dayType
              and s.effectiveFrom <= :onDate
              and (s.effectiveTo is null or s.effectiveTo >= :onDate)
            order by s.effectiveFrom desc
            """)
    List<LineSchedule> findEffective(@Param("lineCode") String lineCode,
                                     @Param("dayType") String dayType,
                                     @Param("onDate") LocalDate onDate);
}
