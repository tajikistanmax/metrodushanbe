package tj.metro.dushanbe.network.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tj.metro.dushanbe.admin.security.AdminAuthProperties;
import tj.metro.dushanbe.config.RateLimitProperties;
import tj.metro.dushanbe.network.service.NetworkService;
import tj.metro.dushanbe.network.web.dto.GeoJsonFeature;
import tj.metro.dushanbe.network.web.dto.GeoJsonFeatureCollection;

@WebMvcTest(NetworkController.class)
@AutoConfigureMockMvc(addFilters = false)
class NetworkControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private NetworkService networkService;

    @MockitoBean
    private AdminAuthProperties adminAuthProperties;

    @MockitoBean
    private RateLimitProperties rateLimitProperties;

    @Test
    void geojsonReturnsFeatureCollection() throws Exception {
        var feature = new GeoJsonFeature("Feature", "L1",
                Map.of("code", "L1", "name_tg", "Хат"),
                Map.of("type", "LineString", "coordinates", List.of()));
        var collection = new GeoJsonFeatureCollection("FeatureCollection",
                Map.of(), List.of(feature));
        when(networkService.networkGeoJson()).thenReturn(collection);

        mockMvc.perform(get("/v1/network/geojson"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("FeatureCollection"))
                .andExpect(jsonPath("$.features.length()").value(1))
                .andExpect(jsonPath("$.features[0].id").value("L1"))
                .andExpect(jsonPath("$.features[0].type").value("Feature"));
    }

    @Test
    void geojsonReturnsEmptyFeatures() throws Exception {
        var collection = new GeoJsonFeatureCollection("FeatureCollection",
                Map.of(), List.of());
        when(networkService.networkGeoJson()).thenReturn(collection);

        mockMvc.perform(get("/v1/network/geojson"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features").isEmpty());
    }
}
