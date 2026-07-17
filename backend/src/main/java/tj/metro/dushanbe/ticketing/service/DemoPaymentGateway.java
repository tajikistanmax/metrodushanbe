package tj.metro.dushanbe.ticketing.service;

import java.math.BigDecimal;
import java.util.Locale;
import org.springframework.stereotype.Service;

/**
 * Демонстрационная имитация эквайринга (TKT-05).
 *
 * <p><b>Денег не двигает. Карт не видит. Ничего не подтверждает.</b> Существует
 * только чтобы контур покупки/возврата был проходим и тестируем до подключения
 * реального провайдера — см. ограничения в {@link PaymentGateway}.
 *
 * <p>Исход <b>детерминирован</b>, без случайности и без задержек: тест, гоняющий
 * покупку, обязан получать один и тот же ответ на одних и тех же входных данных,
 * иначе он начнёт мигать. Решение принимается по двум правилам, в порядке:
 * <ol>
 *   <li>явный код тест-сценария ({@code scenario}) — им пользуются тесты и
 *       демонстрация отказа;
 *   <li>сумма: {@link #DECLINE_AMOUNT} отклоняется всегда. Нужна там, где
 *       сценарий передать некуда (например, ручная проверка через curl).
 * </ol>
 * Во всех остальных случаях — одобрение.
 */
@Service
public class DemoPaymentGateway implements PaymentGateway {

    /** Сценарий «провайдер отклонил операцию». */
    public static final String SCENARIO_DECLINE = "decline";

    /** Сценарий «одобрено» — то же, что и отсутствие сценария; нужен для явности в тестах. */
    public static final String SCENARIO_APPROVE = "approve";

    /** Магическая сумма отказа: см. правило 2 в javadoc класса. */
    public static final BigDecimal DECLINE_AMOUNT = new BigDecimal("13.13");

    /** Текст отказа. Не i18n: это техническая причина для оператора, не для витрины. */
    private static final String DECLINE_REASON =
            "ДЕМО: платёж отклонён имитацией эквайринга (сценарий отказа)";

    @Override
    public String providerCode() {
        return "demo";
    }

    @Override
    public boolean demo() {
        return true;
    }

    @Override
    public PaymentOutcome charge(String paymentCode, BigDecimal amount, String currency,
                                 String scenario) {
        if (declines(amount, scenario)) {
            return PaymentOutcome.declined(DECLINE_REASON);
        }
        return PaymentOutcome.approved(reference("DEMO-CHG", paymentCode));
    }

    @Override
    public PaymentOutcome refund(String refundCode, String providerRef, BigDecimal amount,
                                 String currency) {
        // Возврат в demo не отклоняется никогда: сценарий «эквайер отказал в
        // возврате» проверять нечем — правил и договора с банком ещё нет, а
        // выдумывать их поведение здесь значило бы закрепить фантазию тестом.
        return PaymentOutcome.approved(reference("DEMO-RFN", refundCode));
    }

    private static boolean declines(BigDecimal amount, String scenario) {
        if (scenario != null && SCENARIO_DECLINE.equalsIgnoreCase(scenario.trim())) {
            return true;
        }
        // compareTo, а не equals: 13.13 и 13.130 — одна сумма, но разные BigDecimal.
        return amount != null && amount.compareTo(DECLINE_AMOUNT) == 0;
    }

    /** Синтетическая ссылка «операции у провайдера»: детерминирована по нашему коду. */
    private static String reference(String prefix, String code) {
        return prefix + "-" + code.toUpperCase(Locale.ROOT);
    }
}
