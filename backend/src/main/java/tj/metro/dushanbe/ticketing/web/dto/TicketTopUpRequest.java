package tj.metro.dushanbe.ticketing.web.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Пополнение проездного (U-CIT-08). Платёжных данных не содержит — см.
 * {@link TicketPurchaseRequest}.
 *
 * <p>Здесь сумму называет клиент, в отличие от покупки, — это его деньги и его
 * решение, сколько внести. Границы заданы жёстко: {@code numeric(10,2)} в БД не
 * примет больше двух знаков после запятой, а верхний предел не даёт превратить
 * demo-пополнение в нагрузочный тест на девятизначные суммы.
 *
 * @param demoScenario код тест-сценария demo-эквайринга; см. TicketPurchaseRequest
 */
public record TicketTopUpRequest(

        @NotNull
        @DecimalMin(value = "0.01", message = "Сумма пополнения должна быть положительной")
        @DecimalMax(value = "10000.00", message = "Сумма пополнения превышает допустимый предел")
        @Digits(integer = 8, fraction = 2)
        BigDecimal amount,

        @Size(max = 32)
        String demoScenario) {
}
