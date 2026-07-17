package tj.metro.dushanbe.ticketing.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Покупка билета (U-CIT-08).
 *
 * <p><b>Платёжных данных в этом запросе нет и не появится.</b> Ни номера карты,
 * ни CVV, ни срока действия: приём карточных данных без сертификации PCI DSS
 * недопустим даже в demo — см. javadoc {@code PaymentGateway}. Когда эквайринг
 * подключат, карта будет вводиться на стороне провайдера.
 *
 * <p>Цены в запросе тоже нет: сумма берётся из {@code fare_product} на сервере.
 * Клиент, называющий цену, — это клиент, назначающий цену.
 *
 * @param fareProductCode код тарифа из GET /v1/fares
 * @param riderRef        идентификатор покупателя для антифрода (TKT-06). ДЕМО:
 *                        непроверенный идентификатор приложения/устройства, НЕ
 *                        персональные данные и НЕ учётная запись — контура
 *                        идентификации пассажиров ещё нет. В проде — subject из JWT.
 * @param demoScenario    код тест-сценария demo-эквайринга ({@code approve} |
 *                        {@code decline}). Существует только в demo-контуре и
 *                        исчезнет вместе с {@code DemoPaymentGateway}.
 */
public record TicketPurchaseRequest(

        @NotBlank
        @Size(max = 64)
        String fareProductCode,

        @Size(max = 64)
        @Pattern(regexp = "^[A-Za-z0-9._:-]*$",
                message = "Допустимы латиница, цифры и символы . _ : -")
        String riderRef,

        @Size(max = 32)
        String demoScenario) {
}
