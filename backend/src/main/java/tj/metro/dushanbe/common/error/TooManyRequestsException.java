package tj.metro.dushanbe.common.error;

/**
 * Слишком много попыток — учётная запись или пара IP+логин временно заблокирована
 * (HTTP 429, аудит-пункт 9).
 *
 * <p>Несёт {@code retryAfterSeconds}, чтобы обработчик выставил заголовок
 * {@code Retry-After}: оператору сообщается, когда можно повторить, без раскрытия
 * внутренних порогов подбора.
 */
public class TooManyRequestsException extends RuntimeException {

    private final String code;
    private final long retryAfterSeconds;

    public TooManyRequestsException(String code, String message, long retryAfterSeconds) {
        super(message);
        this.code = code;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public String getCode() {
        return code;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
