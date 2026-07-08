package tj.metro.dushanbe.admin.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CalendarExceptionUpdateRequest(
        @NotNull LocalDate exceptionDate,
        @NotBlank String dayType,
        String descriptionTg,
        String descriptionRu,
        String descriptionEn,
        boolean isRecurring) {
}
