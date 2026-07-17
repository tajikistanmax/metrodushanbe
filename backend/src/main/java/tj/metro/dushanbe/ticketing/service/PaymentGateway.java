package tj.metro.dushanbe.ticketing.service;

import java.math.BigDecimal;

/**
 * Платёжный провайдер (TKT-05).
 *
 * <h2>ОГРАНИЧЕНИЕ КОНТУРА — ЧИТАТЬ ДО ЛЮБОЙ РЕАЛИЗАЦИИ</h2>
 *
 * <p><b>Реального эквайринга в проекте нет.</b> Нет договора с банком-эквайером,
 * нет сертификации PCI DSS, нет фискализации — см. «Внешние блокеры» в
 * {@code docs/implementation-status.md}. Единственная реализация — demo
 * ({@code DemoPaymentGateway}), детерминированно имитирующая исход. Боевая
 * реализация подключается отдельной задачей и не этим модулем.
 *
 * <p><b>Карточные данные через этот интерфейс не проходят и проходить не могут.</b>
 * В сигнатурах нет и не должно появиться ни PAN, ни CVV, ни срока действия, ни
 * имени держателя, ни 3-D Secure. Приём карточных данных без сертификации
 * PCI DSS недопустим — в том числе в demo-контуре: «временный» приём карт
 * означает, что данные уже прошли через наш процесс, наши логи и наши дампы.
 * Правильная схема, когда эквайринг появится, — редирект/SDK провайдера: карта
 * вводится на его стороне, к нам возвращается только {@code providerRef}.
 * Реализация, добавляющая карточные поля в этот контракт, ошибочна по
 * построению — расширять нужно контракт провайдера, а не этот интерфейс.
 *
 * <p><b>Маркировка demo обязательна.</b> Всё, что порождено demo-провайдером,
 * помечается {@code is_demo = true} в БД и полем {@code demo} в API-ответе.
 * Пассажир обязан видеть, что платежа не было: молча показать «билет куплен»
 * там, где денег никто не списывал, недопустимо.
 *
 * <p>Двухшаговость боевого эквайринга (authorize → capture) отражена в
 * {@code PaymentStatus}, но здесь свёрнута в {@link #charge}: demo-провайдеру
 * нечего держать в авторизации. Боевая реализация разделит шаги, не меняя
 * состояний платежа.
 */
public interface PaymentGateway {

    /**
     * Код провайдера для {@code payment.provider}.
     *
     * <p>Обязан совпадать с одним из значений chk_payment_provider — сейчас там
     * только {@code 'demo'}. Новый провайдер приходит вместе с миграцией,
     * расширяющей этот CHECK.
     */
    String providerCode();

    /** Демонстрационный ли провайдер: определяет {@code is_demo} у платежа и билета. */
    boolean demo();

    /**
     * Списание средств.
     *
     * @param paymentCode  наш код платежа — идемпотентный ключ для провайдера
     * @param amount       сумма (всегда > 0, см. chk на payment.amount)
     * @param currency     ISO-4217, 3 буквы
     * @param scenario     код тест-сценария demo-контура; в боевой реализации
     *                     игнорируется. Никогда не несёт платёжных данных.
     */
    PaymentOutcome charge(String paymentCode, BigDecimal amount, String currency, String scenario);

    /**
     * Возврат ранее списанных средств (TKT-03).
     *
     * @param refundCode  наш код возврата — идемпотентный ключ
     * @param providerRef ссылка на исходную операцию списания
     */
    PaymentOutcome refund(String refundCode, String providerRef, BigDecimal amount, String currency);

    /**
     * Исход операции у провайдера.
     *
     * <p>{@code failureReason} обязателен при {@code approved == false}: и
     * chk_payment_error, и chk_refund_error требуют причину — отказ без
     * объяснения бесполезен и пассажиру, и разбору с провайдером.
     *
     * @param approved      одобрена ли операция
     * @param providerRef   идентификатор операции у провайдера (null при отказе)
     * @param failureReason причина отказа (null при успехе)
     */
    record PaymentOutcome(boolean approved, String providerRef, String failureReason) {

        public static PaymentOutcome approved(String providerRef) {
            return new PaymentOutcome(true, providerRef, null);
        }

        public static PaymentOutcome declined(String failureReason) {
            return new PaymentOutcome(false, null, failureReason);
        }
    }
}
