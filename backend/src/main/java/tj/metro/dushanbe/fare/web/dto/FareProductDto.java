package tj.metro.dushanbe.fare.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/** Тарифный продукт публичного и административного REST API. */
public record FareProductDto(
        String code,
        Map<String, String> name,
        Map<String, String> description,
        BigDecimal amount,
        String currency,
        String riderCategory,
        Integer validityMinutes,
        boolean active,
        Instant updatedAt) {
}
