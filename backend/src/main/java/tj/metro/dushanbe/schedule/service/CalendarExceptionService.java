package tj.metro.dushanbe.schedule.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import tj.metro.dushanbe.schedule.domain.CalendarException;
import tj.metro.dushanbe.schedule.repository.CalendarExceptionRepository;

@Service
public class CalendarExceptionService {

    private final CalendarExceptionRepository repository;

    public CalendarExceptionService(CalendarExceptionRepository repository) {
        this.repository = repository;
    }

    public String resolveDayType(LocalDate date) {
        var exact = repository.findByExceptionDate(date);
        if (exact.isPresent()) {
            return mapDayType(exact.get().getDayType());
        }
        List<CalendarException> recurring = repository.findByIsRecurringTrue();
        for (CalendarException ce : recurring) {
            LocalDate excDate = ce.getExceptionDate();
            if (excDate.getMonth() == date.getMonth() && excDate.getDayOfMonth() == date.getDayOfMonth()) {
                return mapDayType(ce.getDayType());
            }
        }
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY) {
            return "saturday";
        }
        if (dow == DayOfWeek.SUNDAY) {
            return "sunday";
        }
        return "weekday";
    }

    private static String mapDayType(String raw) {
        return switch (raw) {
            case "holiday", "special" -> "holiday";
            case "weekend_override" -> "weekend";
            case "weekday_override" -> "weekday";
            default -> "weekday";
        };
    }
}
