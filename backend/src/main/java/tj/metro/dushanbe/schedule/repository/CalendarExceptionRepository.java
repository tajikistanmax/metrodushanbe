package tj.metro.dushanbe.schedule.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import tj.metro.dushanbe.schedule.domain.CalendarException;

public interface CalendarExceptionRepository extends JpaRepository<CalendarException, Long> {

    Optional<CalendarException> findByExceptionDate(LocalDate date);

    List<CalendarException> findByExceptionDateBetween(LocalDate start, LocalDate end);

    List<CalendarException> findByIsRecurringTrue();
}
