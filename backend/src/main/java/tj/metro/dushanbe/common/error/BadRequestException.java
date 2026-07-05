package tj.metro.dushanbe.common.error;

/**
 * Некорректные параметры запроса (HTTP 400). Код ошибки — машиночитаемый,
 * по умолчанию {@code validation.failed}; доменные коды — вида
 * {@code alert.severity_invalid}. {@code details} — произвольная структура
 * с подробностями нарушения.
 */
public class BadRequestException extends RuntimeException {

    /** Код по умолчанию для общих ошибок валидации параметров. */
    public static final String VALIDATION_FAILED = "validation.failed";

    private final String code;
    private final transient Object details;

    public BadRequestException(String message, Object details) {
        this(VALIDATION_FAILED, message, details);
    }

    public BadRequestException(String code, String message, Object details) {
        super(message);
        this.code = code;
        this.details = details;
    }

    public String getCode() {
        return code;
    }

    public Object getDetails() {
        return details;
    }
}
