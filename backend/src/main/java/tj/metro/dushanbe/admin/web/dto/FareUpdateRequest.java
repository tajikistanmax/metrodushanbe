package tj.metro.dushanbe.admin.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.util.Map;

/** Изменение тарифа; стабильный код остаётся неизменным. */
public record FareUpdateRequest(
        @NotNull Map<String, String> name,
        @NotNull Map<String, String> description,
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal amount,
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency,
        @NotBlank @Pattern(regexp = "all|adult|child|student|senior") String riderCategory,
        @Min(1) Integer validityMinutes,
        @NotNull Boolean active) {
}
