package tj.metro.dushanbe.admin.web;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tj.metro.dushanbe.identity.repository.AdminUserRepository;
import tj.metro.dushanbe.admin.security.AdminAuthProperties;
import tj.metro.dushanbe.admin.web.dto.FeatureFlagToggleRequest;
import tj.metro.dushanbe.config.RateLimitProperties;
import tj.metro.dushanbe.featureflag.domain.FeatureFlag;
import tj.metro.dushanbe.featureflag.service.FeatureFlagService;

@WebMvcTest(AdminFeatureFlagController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminFeatureFlagControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private FeatureFlagService featureFlagService;

    @MockitoBean
    private AdminAuthProperties adminAuthProperties;

    @MockitoBean
    private RateLimitProperties rateLimitProperties;

/**
     * AdminKeyAuthFilter — бин типа Filter, поэтому @WebMvcTest создаёт его даже
     * при addFilters = false. Фильтр резолвит актора через репозиторий, которого
     * в web-срезе нет, — без этой заглушки контекст не поднимется.
     */
    @MockitoBean
    private AdminUserRepository adminUserRepository;

    @Test
    void listReturnsAllFlags() throws Exception {
        var flags = List.of(
                new FeatureFlag("milan", true, "Milan line flag", OffsetDateTime.now(), "admin"),
                new FeatureFlag("kaizen", false, "Kaizen line flag", OffsetDateTime.now(), "admin"));
        when(featureFlagService.findAll()).thenReturn(flags);

        mockMvc.perform(get("/v1/admin/feature-flags"))
                .andExpect(status().isOk())
                .andExpect(content().json(objectMapper.writeValueAsString(flags)));
    }

    @Test
    void toggleCallsServiceWithFlagKeyAndValue() throws Exception {
        var request = new FeatureFlagToggleRequest(true);

        mockMvc.perform(put("/v1/admin/feature-flags/milan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(featureFlagService).setEnabled("milan", true, "dev-admin");
    }

    @Test
    void toggleWithCustomActorHeader() throws Exception {
        var request = new FeatureFlagToggleRequest(false);

        mockMvc.perform(put("/v1/admin/feature-flags/kaizen")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("X-Admin-Actor", "superadmin"))
                .andExpect(status().isOk());

        verify(featureFlagService).setEnabled("kaizen", false, "superadmin");
    }
}
