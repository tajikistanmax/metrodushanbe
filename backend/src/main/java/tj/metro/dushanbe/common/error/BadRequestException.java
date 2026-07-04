package tj.metro.dushanbe.common.error;

/**
 * Некорректные параметры запроса (HTTP 400, код {@code validation.failed}).
 * {@code details} — произвольная структура с подробностями нарушения.
 */
public class BadRequestException extends RuntimeException {

    private final transient Object details;

    public BadRequestException(String message, Object details) {
        super(message);
        this.details = details;
    }

    public Object getDetails() {
        return details;
    }
}
