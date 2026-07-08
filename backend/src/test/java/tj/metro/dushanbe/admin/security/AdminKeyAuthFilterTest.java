package tj.metro.dushanbe.admin.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdminKeyAuthFilterTest {

    private final AdminAuthProperties properties = new AdminAuthProperties();
    private final AdminKeyAuthFilter filter = new AdminKeyAuthFilter(properties, new ObjectMapper()
            .registerModule(new JavaTimeModule()));
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final HttpServletResponse response = mock(HttpServletResponse.class);
    private final FilterChain chain = mock(FilterChain.class);

    @BeforeEach
    void setUp() throws Exception {
        properties.setDevKey("secret-key");
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
    }

    @Test
    void shouldNotFilterNonAdminPaths() {
        when(request.getServletPath()).thenReturn("/v1/lines");
        assertTrue(filter.shouldNotFilter(request));
    }

    @Test
    void shouldFilterAdminPaths() {
        when(request.getServletPath()).thenReturn("/v1/admin/lines");
        assertFalse(filter.shouldNotFilter(request));
    }

    @Test
    void allowsRequestWithCorrectKey() throws Exception {
        when(request.getServletPath()).thenReturn("/v1/admin/lines");
        when(request.getHeader(AdminKeyAuthFilter.HEADER)).thenReturn("secret-key");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void blocksRequestWithWrongKey() throws Exception {
        when(request.getServletPath()).thenReturn("/v1/admin/lines");
        when(request.getHeader(AdminKeyAuthFilter.HEADER)).thenReturn("wrong-key");

        filter.doFilterInternal(request, response, chain);

        verify(response).setStatus(401);
    }

    @Test
    void blocksRequestWithMissingKey() throws Exception {
        when(request.getServletPath()).thenReturn("/v1/admin/lines");
        when(request.getHeader(AdminKeyAuthFilter.HEADER)).thenReturn(null);

        filter.doFilterInternal(request, response, chain);

        verify(response).setStatus(401);
    }

    @Test
    void blocksRequestWithBlankDevKey() throws Exception {
        properties.setDevKey("");
        when(request.getServletPath()).thenReturn("/v1/admin/lines");

        filter.doFilterInternal(request, response, chain);

        verify(response).setStatus(401);
    }

    @Test
    void usesRequestUriFallbackWhenServletPathNull() {
        when(request.getServletPath()).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/v1/admin/lines");
        assertFalse(filter.shouldNotFilter(request));
    }
}
