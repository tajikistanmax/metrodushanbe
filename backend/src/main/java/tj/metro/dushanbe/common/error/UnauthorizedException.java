package tj.metro.dushanbe.common.error;

/**
 * Неверные или отозванные учётные данные (HTTP 401).
 *
 * <p>Сообщение намеренно не различает «нет такого пользователя», «неверный пароль»
 * и «учётная запись отключена»: раскрытие этой разницы даёт перебор логинов.
 * Причина пишется в audit, а не в ответ.
 */
public class UnauthorizedException extends RuntimeException {

    private final String code;

    public UnauthorizedException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
