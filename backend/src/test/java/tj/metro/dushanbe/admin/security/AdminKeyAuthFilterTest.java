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
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tj.metro.dushanbe.identity.domain.AdminRole;
import tj.metro.dushanbe.identity.domain.AdminUser;
import tj.metro.dushanbe.identity.repository.AdminUserRepository;
import java.util.UUID;

class AdminKeyAuthFilterTest {

    private final AdminAuthProperties properties = new AdminAuthProperties();
    private final AdminUserRepository userRepository = mock(AdminUserRepository.class);
    private final AdminKeyAuthFilter filter = new AdminKeyAuthFilter(properties, new ObjectMapper()
            .registerModule(new JavaTimeModule()), userRepository);
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final HttpServletResponse response = mock(HttpServletResponse.class);
    private final FilterChain chain = mock(FilterChain.class);

    @BeforeEach
    void setUp() throws Exception {
        properties.setDevKey("secret-key");
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        var user = new AdminUser(UUID.randomUUID(), "root", "Root", "hash",
                AdminRole.SUPERADMIN, true);
        when(userRepository.findByUsername("root")).thenReturn(Optional.of(user));
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
        when(request.getHeader(AdminKeyAuthFilter.ACTOR_HEADER)).thenReturn("root");
        when(request.getMethod()).thenReturn("POST");

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
    void blocksRequestWithoutActor() throws Exception {
        when(request.getServletPath()).thenReturn("/v1/admin/lines");
        when(request.getHeader(AdminKeyAuthFilter.HEADER)).thenReturn("secret-key");

        filter.doFilterInternal(request, response, chain);

        verify(response).setStatus(401);
    }

    @Test
    void blocksViewerFromWriteOperation() throws Exception {
        var viewer = new AdminUser(UUID.randomUUID(), "viewer", "Viewer", "hash",
                AdminRole.VIEWER, true);
        when(userRepository.findByUsername("viewer")).thenReturn(Optional.of(viewer));
        when(request.getServletPath()).thenReturn("/v1/admin/lines");
        when(request.getHeader(AdminKeyAuthFilter.HEADER)).thenReturn("secret-key");
        when(request.getHeader(AdminKeyAuthFilter.ACTOR_HEADER)).thenReturn("viewer");
        when(request.getMethod()).thenReturn("POST");

        filter.doFilterInternal(request, response, chain);

        verify(response).setStatus(403);
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
