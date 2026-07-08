package tj.metro.dushanbe.schedule.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CalendarExceptionTest {

    @Test
    void constructorSetsFields() {
        var ex = new CalendarException(LocalDate.of(2026, 6, 1), "weekend",
                "Описание tg", "Описание ru", "Description en", true);

        assertEquals(LocalDate.of(2026, 6, 1), ex.getExceptionDate());
        assertEquals("weekend", ex.getDayType());
        assertEquals("Описание tg", ex.getDescriptionTg());
        assertEquals("Описание ru", ex.getDescriptionRu());
        assertEquals("Description en", ex.getDescriptionEn());
        assertTrue(ex.isRecurring());
    }

    @Test
    void onCreateSetsCreatedAt() {
        var ex = new CalendarException(LocalDate.now(), "holiday",
                null, null, null, false);

        assertNull(ex.getCreatedAt());
        ex.onCreate();
        assertNotNull(ex.getCreatedAt());
    }

    @Test
    void onCreateDoesNotOverrideExistingCreatedAt() {
        var ex = new CalendarException(LocalDate.now(), "holiday",
                null, null, null, false);
        ex.onCreate();
        var original = ex.getCreatedAt();

        ex.onCreate();

        assertEquals(original, ex.getCreatedAt());
    }

    @Test
    void settersUpdateFields() {
        var ex = new CalendarException(LocalDate.now(), "weekday",
                null, null, null, false);

        ex.setExceptionDate(LocalDate.of(2027, 1, 1));
        ex.setDayType("holiday");
        ex.setDescriptionTg("Иди Рамазон");
        ex.setDescriptionRu("Праздник Рамадан");
        ex.setDescriptionEn("Eid al-Fitr");
        ex.setRecurring(true);

        assertEquals(LocalDate.of(2027, 1, 1), ex.getExceptionDate());
        assertEquals("holiday", ex.getDayType());
        assertEquals("Иди Рамазон", ex.getDescriptionTg());
        assertEquals("Праздник Рамадан", ex.getDescriptionRu());
        assertEquals("Eid al-Fitr", ex.getDescriptionEn());
        assertTrue(ex.isRecurring());
    }
}
