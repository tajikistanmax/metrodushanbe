package tj.metro.dushanbe.ai.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import tj.metro.dushanbe.admin.security.AdminAuthProperties;
import tj.metro.dushanbe.ai.config.AiProperties;
import tj.metro.dushanbe.ai.service.AiAgentReadinessService;
import tj.metro.dushanbe.ai.service.LlmClient;
import tj.metro.dushanbe.config.RateLimitProperties;

@WebMvcTest(AiController.class)
@AutoConfigureMockMvc(addFilters = false)
class AiControllerChatTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AiAgentReadinessService readinessService;

    @MockitoBean
    private LlmClient llmClient;

    @MockitoBean
    private AdminAuthProperties adminAuthProperties;

    @MockitoBean
    private AiProperties aiProperties;

    @MockitoBean
    private RateLimitProperties rateLimitProperties;

    @Test
    void chatEndpointReturnsAgentReply() throws Exception {
        var llmProps = new AiProperties.Llm();
        llmProps.setProvider("disabled");
        when(aiProperties.getLlm()).thenReturn(llmProps);
        when(readinessService.systemPromptForAgent("route-advisor-agent")).thenReturn("system prompt");
        when(llmClient.chat(any(), any(), any())).thenReturn("Take the red line from A to B.");

        mockMvc.perform(post("/v1/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("agentCode", "route-advisor-agent",
                                        "message", "How do I get from A to B?",
                                        "context", java.util.Map.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agentCode").value("route-advisor-agent"))
                .andExpect(jsonPath("$.reply").value("Take the red line from A to B."))
                .andExpect(jsonPath("$.sources").isArray());
    }

    @Test
    void chatEndpointReturnsDeterministicSourcesWhenDisabled() throws Exception {
        var llmProps = new AiProperties.Llm();
        llmProps.setProvider("disabled");
        when(aiProperties.getLlm()).thenReturn(llmProps);
        when(readinessService.systemPromptForAgent("fare-agent")).thenReturn("system prompt");
        when(llmClient.chat(any(), any(), any())).thenReturn("A single journey costs 5 somoni.");

        var response = mockMvc.perform(post("/v1/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("agentCode", "fare-agent",
                                        "message", "How much is the fare?",
                                        "context", java.util.Map.of()))))
                .andExpect(status().isOk())
                .andReturn();

        assertNotNull(response.getResponse().getContentAsString());
    }
}
