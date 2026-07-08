package tj.metro.dushanbe.common.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final HttpServletRequest request = mock(HttpServletRequest.class);

    @Test
    void notFoundReturns404WithDomainCode() {
        NotFoundException ex = NotFoundException.line("L1");

        ResponseEntity<ApiError> response = handler.handleNotFound(ex, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("line.not_found", response.getBody().error().code());
    }

    @Test
    void badRequestReturns400WithDomainCode() {
        BadRequestException ex = new BadRequestException("invalid severity", Map.of());

        ResponseEntity<ApiError> response = handler.handleBadRequest(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("validation.failed", response.getBody().error().code());
    }

    @Test
    void methodArgumentNotValidReturns400WithFieldErrors() {
        var target = new Object();
        var bindingResult = new BeanPropertyBindingResult(target, "target");
        bindingResult.addError(new FieldError("target", "name", "must not be blank"));
        bindingResult.addError(new FieldError("target", "code", "size must be between 1 and 50"));
        MethodArgumentNotValidException ex =
                new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<ApiError> response = handler.handleMethodArgumentNotValid(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("validation.failed", response.getBody().error().code());
        assertNotNull(response.getBody().error().details());
        @SuppressWarnings("unchecked")
        List<Map<String, String>> details = (List<Map<String, String>>) response.getBody().error().details();
        assertEquals(2, details.size());
        assertEquals("name", details.get(0).get("field"));
    }

    @Test
    void malformedRequestReturns400() {
        HttpMessageNotReadableException ex =
                new HttpMessageNotReadableException("JSON parse error");

        ResponseEntity<ApiError> response = handler.handleMalformedRequest(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void noResourceFoundReturns404() {
        NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, "/unknown");

        ResponseEntity<ApiError> response = handler.handleNoResource(ex, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("resource.not_found", response.getBody().error().code());
        assertTrue(response.getBody().error().message().contains("/unknown"));
    }

    @Test
    void internalErrorReturns500() {
        RuntimeException ex = new RuntimeException("db connection failed");

        ResponseEntity<ApiError> response = handler.handleInternal(ex, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("internal.error", response.getBody().error().code());
    }

    @Test
    void methodArgumentTypeMismatchReturns400() {
        MethodArgumentTypeMismatchException ex =
                new MethodArgumentTypeMismatchException("abc", int.class, "count", null, null);

        ResponseEntity<ApiError> response = handler.handleMalformedRequest(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void missingServletRequestParameterReturns400() {
        MissingServletRequestParameterException ex =
                new MissingServletRequestParameterException("status", "String");

        ResponseEntity<ApiError> response = handler.handleMalformedRequest(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void requestIdFromAttributeIsIncludedInResponse() {
        when(request.getAttribute(RequestIdFilter.ATTRIBUTE)).thenReturn("req-123");
        NotFoundException ex = NotFoundException.station("ST-1");

        ResponseEntity<ApiError> response = handler.handleNotFound(ex, request);

        assertEquals("req-123", response.getBody().requestId());
    }
}
