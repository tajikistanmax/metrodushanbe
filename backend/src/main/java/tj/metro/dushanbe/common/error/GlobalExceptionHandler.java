package tj.metro.dushanbe.common.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Централизованная обработка ошибок: все ответы — в едином envelope
 * {@link ApiError} (ТЗ §7.5, docs/dev-conventions.md §3).
 * Коды: 404 — {@code line.not_found}/{@code station.not_found},
 * 400 — {@code validation.failed} либо доменный код из {@link BadRequestException}
 * (например {@code alert.severity_invalid}), 500 — {@code internal.error}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 404: доменный "не найдено" (station.not_found / line.not_found). */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NotFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(requestId(request), ex.getCode(), ex.getMessage(), null));
    }

    /** 401: неверные учётные данные оператора. Детали не раскрываем — только код. */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiError> handleUnauthorized(UnauthorizedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of(requestId(request), ex.getCode(), ex.getMessage(), null));
    }

    /** 403: учётная запись действительна, но её роль не покрывает операцию. */
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiError> handleForbidden(ForbiddenException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.of(requestId(request), ex.getCode(), ex.getMessage(), null));
    }

    /** 429: превышен лимит попыток входа (lockout, аудит-пункт 9). */
    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ApiError> handleTooManyRequests(TooManyRequestsException ex,
                                                          HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(ApiError.of(requestId(request), ex.getCode(), ex.getMessage(), null));
    }

    /** 400: доменная валидация параметров запроса (код из исключения, например alert.severity_invalid). */
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiError> handleBadRequest(BadRequestException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(ApiError.of(requestId(request), ex.getCode(), ex.getMessage(), ex.getDetails()));
    }

    /** 400: Bean Validation на @RequestBody. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                 HttpServletRequest request) {
        List<Map<String, String>> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> {
                    Map<String, String> item = new LinkedHashMap<>();
                    item.put("field", fe.getField());
                    item.put("message", String.valueOf(fe.getDefaultMessage()));
                    return item;
                })
                .toList();
        return ResponseEntity.badRequest()
                .body(ApiError.of(requestId(request), BadRequestException.VALIDATION_FAILED, "Ошибка валидации запроса", details));
    }

    /** 400: Bean Validation на параметрах (@RequestParam/@PathVariable). */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex,
                                                              HttpServletRequest request) {
        List<Map<String, String>> details = ex.getConstraintViolations().stream()
                .map(v -> {
                    Map<String, String> item = new LinkedHashMap<>();
                    item.put("field", String.valueOf(v.getPropertyPath()));
                    item.put("message", v.getMessage());
                    return item;
                })
                .toList();
        return ResponseEntity.badRequest()
                .body(ApiError.of(requestId(request), BadRequestException.VALIDATION_FAILED, "Ошибка валидации запроса", details));
    }

    /** 400: неверный тип параметра, отсутствующий параметр, нечитаемое тело. */
    @ExceptionHandler({MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            HttpMessageNotReadableException.class})
    public ResponseEntity<ApiError> handleMalformedRequest(Exception ex, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(ApiError.of(requestId(request), BadRequestException.VALIDATION_FAILED, "Некорректный запрос", ex.getMessage()));
    }

    /**
     * 404 для неизвестных путей: без этого обработчика NoResourceFoundException
     * провалился бы в общий handler и вернул 500 вместо 404.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(requestId(request), "resource.not_found",
                        "Ресурс не найден: " + ex.getResourcePath(), null));
    }

    /** 500: все прочие ошибки. Детали в ответ не раскрываем, только в лог. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleInternal(Exception ex, HttpServletRequest request) {
        String requestId = requestId(request);
        LOG.error("Необработанная ошибка, requestId={}", requestId, ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(requestId, "internal.error", "Внутренняя ошибка сервера", null));
    }

    private String requestId(HttpServletRequest request) {
        Object attribute = request.getAttribute(RequestIdFilter.ATTRIBUTE);
        return attribute != null ? attribute.toString() : "";
    }
}
