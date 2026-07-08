package tj.metro.dushanbe.schedule.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tj.metro.dushanbe.schedule.domain.CalendarException;
import tj.metro.dushanbe.schedule.repository.CalendarExceptionRepository;

class CalendarExceptionServiceTest {

    private final CalendarExceptionRepository repository = mock(CalendarExceptionRepository.class);
    private final CalendarExceptionService service = new CalendarExceptionService(repository);

    @Test
    void resolveDayTypeReturnsHolidayForExactDate() {
        LocalDate date = LocalDate.of(2026, 7, 7);
        CalendarException exc = new CalendarException(date, "holiday", null, null, null, false);
        when(repository.findByExceptionDate(date)).thenReturn(Optional.of(exc));

        String result = service.resolveDayType(date);

        assertEquals("holiday", result);
    }

    @Test
    void resolveDayTypeReturnsHolidayForSpecialDayType() {
        LocalDate date = LocalDate.of(2026, 7, 7);
        CalendarException exc = new CalendarException(date, "special", null, null, null, false);
        when(repository.findByExceptionDate(date)).thenReturn(Optional.of(exc));

        String result = service.resolveDayType(date);

        assertEquals("holiday", result);
    }

    @Test
    void resolveDayTypeReturnsWeekdayForMonday() {
        LocalDate monday = LocalDate.of(2026, 7, 6);
        when(repository.findByExceptionDate(monday)).thenReturn(Optional.empty());
        when(repository.findByIsRecurringTrue()).thenReturn(List.of());

        String result = service.resolveDayType(monday);

        assertEquals("weekday", result);
    }

    @Test
    void resolveDayTypeReturnsSaturday() {
        LocalDate saturday = LocalDate.of(2026, 7, 11);
        when(repository.findByExceptionDate(saturday)).thenReturn(Optional.empty());
        when(repository.findByIsRecurringTrue()).thenReturn(List.of());

        String result = service.resolveDayType(saturday);

        assertEquals("saturday", result);
    }

    @Test
    void resolveDayTypeReturnsSunday() {
        LocalDate sunday = LocalDate.of(2026, 7, 12);
        when(repository.findByExceptionDate(sunday)).thenReturn(Optional.empty());
        when(repository.findByIsRecurringTrue()).thenReturn(List.of());

        String result = service.resolveDayType(sunday);

        assertEquals("sunday", result);
    }

    @Test
    void recurringExceptionMatchesMonthDayRegardlessOfYear() {
        LocalDate mayDay = LocalDate.of(2026, 5, 1);
        CalendarException recurring = new CalendarException(
                LocalDate.of(2024, 5, 1), "holiday", null, null, null, true);
        when(repository.findByExceptionDate(mayDay)).thenReturn(Optional.empty());
        when(repository.findByIsRecurringTrue()).thenReturn(List.of(recurring));

        String result = service.resolveDayType(mayDay);

        assertEquals("holiday", result);
    }
}
