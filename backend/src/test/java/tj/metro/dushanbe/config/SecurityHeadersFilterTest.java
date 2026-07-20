package tj.metro.dushanbe.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SecurityHeadersFilterTest {

    private final SecurityHeadersFilter filter = new SecurityHeadersFilter();
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final HttpServletResponse response = mock(HttpServletResponse.class);
    private final FilterChain chain = mock(FilterChain.class);
    private final Map<String, String> headers = new HashMap<>();

    @BeforeEach
    void captureHeaders() {
        // response.setHeader(k, v) → в карту, чтобы проверять итоговые значения.
        org.mockito.Mockito.doAnswer(invocation -> {
                    headers.put(invocation.getArgument(0), invocation.getArgument(1));
                    return null;
                })
                .when(response)
                .setHeader(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void setsBaselineSecurityHeadersAndApiCsp() throws Exception {
        when(request.getScheme()).thenReturn("http");
        when(request.getRequestURI()).thenReturn("/api/v1/network");

        filter.doFilter(request, response, chain);

        assertEquals("nosniff", headers.get("X-Content-Type-Options"));
        assertEquals("DENY", headers.get("X-Frame-Options"));
        assertEquals("0", headers.get("X-XSS-Protection"));
        assertEquals("strict-origin-when-cross-origin", headers.get("Referrer-Policy"));
        assertEquals(
                "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'",
                headers.get("Content-Security-Policy"));
        verify(chain).doFilter(request, response);
    }

    @Test
    void doesNotSetHstsOnPlainHttp() throws Exception {
        when(request.getScheme()).thenReturn("http");
        when(request.getRequestURI()).thenReturn("/api/v1/network");
        when(request.getHeader("X-Forwarded-Proto")).thenReturn(null);

        filter.doFilter(request, response, chain);

        assertNull(headers.get("Strict-Transport-Security"));
    }

    @Test
    void setsHstsOnDirectHttps() throws Exception {
        when(request.getScheme()).thenReturn("https");
        when(request.getRequestURI()).thenReturn("/api/v1/network");

        filter.doFilter(request, response, chain);

        assertEquals("max-age=31536000; includeSubDomains", headers.get("Strict-Transport-Security"));
    }

    @Test
    void setsHstsBehindTlsTerminatingProxy() throws Exception {
        when(request.getScheme()).thenReturn("http");
        when(request.getRequestURI()).thenReturn("/api/v1/network");
        when(request.getHeader("X-Forwarded-Proto")).thenReturn("https,http");

        filter.doFilter(request, response, chain);

        assertEquals("max-age=31536000; includeSubDomains", headers.get("Strict-Transport-Security"));
    }

    @Test
    void omitsStrictCspForSwaggerUi() throws Exception {
        when(request.getScheme()).thenReturn("http");
        when(request.getRequestURI()).thenReturn("/api/swagger-ui/index.html");

        filter.doFilter(request, response, chain);

        // Интерактивная документация не должна получать строгую CSP,
        // но базовые заголовки остаются.
        assertNull(headers.get("Content-Security-Policy"));
        assertEquals("nosniff", headers.get("X-Content-Type-Options"));
        verify(chain).doFilter(request, response);
    }
}
