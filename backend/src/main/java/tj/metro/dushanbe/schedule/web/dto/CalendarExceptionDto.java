package tj.metro.dushanbe.schedule.web.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record CalendarExceptionDto(Long id,
                                   LocalDate exceptionDate,
                                   String dayType,
                                   String descriptionTg,
                                   String descriptionRu,
                                   String descriptionEn,
                                   boolean isRecurring,
                                   OffsetDateTime createdAt) {
}
