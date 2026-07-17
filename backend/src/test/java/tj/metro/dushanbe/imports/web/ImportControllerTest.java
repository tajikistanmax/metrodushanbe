package tj.metro.dushanbe.imports.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
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
import tj.metro.dushanbe.config.RateLimitProperties;
import tj.metro.dushanbe.imports.domain.ImportJob;
import tj.metro.dushanbe.imports.service.ImportQueryService;
import tj.metro.dushanbe.imports.service.ImportService;
import tj.metro.dushanbe.imports.web.dto.ImportErrorDto;
import tj.metro.dushanbe.imports.web.dto.ImportJobDto;
import tj.metro.dushanbe.imports.web.dto.ImportPageDto;

@WebMvcTest(ImportController.class)
@AutoConfigureMockMvc(addFilters = false)
class ImportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ImportService importService;

    @MockitoBean
    private ImportQueryService importQueryService;

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
    void importNetworkReturnsAccepted() throws Exception {
        var id = UUID.randomUUID();
        var job = new ImportJob(id, ImportJob.TYPE_NETWORK_GEOJSON, "test-source", null);
        when(importService.importNetworkGeoJson(any(), eq("test-source"), eq("dev-admin")))
                .thenReturn(job);

        mockMvc.perform(post("/v1/admin/imports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"FeatureCollection\",\"features\":[]}")
                        .header("X-Import-Source", "test-source"))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.type").value(ImportJob.TYPE_NETWORK_GEOJSON))
                .andExpect(jsonPath("$.status").value("pending"));
    }

    @Test
    void jobReturnsJobDto() throws Exception {
        var id = UUID.randomUUID();
        var dto = new ImportJobDto(id, ImportJob.TYPE_NETWORK_GEOJSON, "success",
                "source", null, 5, 3, 2, 0,
                Instant.parse("2025-01-01T12:00:00Z"),
                Instant.parse("2025-01-01T12:01:00Z"),
                Instant.parse("2025-01-01T12:00:00Z"));
        when(importQueryService.job(id)).thenReturn(dto);

        mockMvc.perform(get("/v1/admin/imports/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.featureCount").value(5))
                .andExpect(jsonPath("$.createdCount").value(3))
                .andExpect(jsonPath("$.updatedCount").value(2));
    }

    @Test
    void jobErrorsReturnsList() throws Exception {
        var id = UUID.randomUUID();
        var error = new ImportErrorDto(UUID.randomUUID(), "L1",
                "Invalid geometry", "error", Instant.parse("2025-01-01T12:00:00Z"));
        when(importQueryService.errors(id)).thenReturn(List.of(error));

        mockMvc.perform(get("/v1/admin/imports/{id}/errors", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].featureRef").value("L1"))
                .andExpect(jsonPath("$[0].message").value("Invalid geometry"))
                .andExpect(jsonPath("$[0].severity").value("error"));
    }

    @Test
    void listReturnsPage() throws Exception {
        var page = new ImportPageDto(List.of(), 0, 50, 0, 0);
        when(importQueryService.page(anyInt(), anyInt())).thenReturn(page);

        mockMvc.perform(get("/v1/admin/imports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.items").isEmpty());
    }
}
