package tj.metro.dushanbe.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/**
 * Единый envelope ошибок API (ТЗ §7.5, docs/dev-conventions.md §3):
 * {@code {timestamp, requestId, error: {code, message, details}}}.
 */
public record ApiError(Instant timestamp, String requestId, ErrorBody error) {

    /**
     * Тело ошибки. {@code details} — произвольная структура
     * (например, список нарушений валидации); при null не сериализуется.
     */
    public record ErrorBody(String code,
                            String message,
                            @JsonInclude(JsonInclude.Include.NON_NULL) Object details) {
    }

    public static ApiError of(String requestId, String code, String message, Object details) {
        return new ApiError(Instant.now(), requestId, new ErrorBody(code, message, details));
    }
}
