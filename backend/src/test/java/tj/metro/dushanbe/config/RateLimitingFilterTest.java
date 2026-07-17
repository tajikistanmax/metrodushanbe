package tj.metro.dushanbe.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RateLimitingFilterTest {

    private final RateLimitProperties properties = new RateLimitProperties();
    private final RateLimitingFilter filter = new RateLimitingFilter(properties);
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final HttpServletResponse response = mock(HttpServletResponse.class);
    private final FilterChain chain = mock(FilterChain.class);
    private final StringWriter stringWriter = new StringWriter();

    @BeforeEach
    void setUp() throws Exception {
        properties.setEnabled(true);
        properties.setCapacity(100);
        properties.setRefillPerMinute(100);
        when(response.getWriter()).thenReturn(new PrintWriter(stringWriter));
    }

    @Test
    void shouldNotFilterNonApiPaths() {
        when(request.getServletPath()).thenReturn("/actuator/health");
        assertTrue(filter.shouldNotFilter(request));
    }

    @Test
    void shouldNotFilterActuatorHealth() {
        when(request.getServletPath()).thenReturn("/actuator/health");
        assertTrue(filter.shouldNotFilter(request));
    }

    @Test
    void shouldFilterApiPaths() {
        when(request.getServletPath()).thenReturn("/v1/ai/chat");
        assertFalse(filter.shouldNotFilter(request));
    }

    @Test
    void passesRequestWhenDisabled() throws Exception {
        properties.setEnabled(false);
        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void blocksRequestWhenRateExceeded() throws Exception {
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");
        properties.setCapacity(1);
        properties.setRefillPerMinute(1);
        when(request.getServletPath()).thenReturn("/v1/test");

        filter.doFilterInternal(request, response, chain);
        filter.doFilterInternal(request, response, chain);

        verify(response).setHeader("Retry-After", "60");
        verify(response).setStatus(429);
    }

    @Test
    void respectsXForwardedForHeader() throws Exception {
        properties.setTrustForwardedFor(true);
        when(request.getHeader("X-Forwarded-For")).thenReturn("10.0.0.1, 10.0.0.2");
        when(request.getServletPath()).thenReturn("/v1/test");
        properties.setCapacity(100);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void returnsRetryAfterHeaderOnBlock() throws Exception {
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        when(request.getServletPath()).thenReturn("/v1/test");
        properties.setCapacity(1);
        properties.setRefillPerMinute(60);

        filter.doFilterInternal(request, response, chain);
        filter.doFilterInternal(request, response, chain);

        verify(response).setHeader("Retry-After", "1");
        verify(response).setStatus(429);
    }

    @Test
    void passesRequestWhenUnderLimit() throws Exception {
        when(request.getRemoteAddr()).thenReturn("10.0.0.2");
        when(request.getServletPath()).thenReturn("/v1/test");
        properties.setCapacity(100);
        properties.setRefillPerMinute(100);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(429);
    }
}
