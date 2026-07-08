package tj.metro.dushanbe.ai.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tj.metro.dushanbe.ai.config.AiProperties;

class LlmClientTest {

    private LlmClient llmClient;

    @BeforeEach
    void setUp() {
        var props = new AiProperties();
        props.getLlm().setProvider("disabled");
        var builder = RestClient.builder();
        llmClient = new LlmClient(builder, props);
    }

    @Test
    void deterministicChatReturnsResponseForRouteQuery() {
        var reply = llmClient.chat("system prompt", "What is the best route from A to B?", null);
        assertNotNull(reply);
        assertFalse(reply.isBlank());
        assertTrue(reply.toLowerCase().contains("route"));
    }

    @Test
    void deterministicChatReturnsResponseForFareQuery() {
        var reply = llmClient.chat("system prompt", "How much is a ticket?", null);
        assertNotNull(reply);
        assertTrue(reply.toLowerCase().contains("fare") || reply.toLowerCase().contains("somoni"));
    }

    @Test
    void deterministicChatReturnsResponseForGeneralQuery() {
        var reply = llmClient.chat("system prompt", "Tell me about the metro", null);
        assertNotNull(reply);
        assertFalse(reply.isBlank());
    }

    @Test
    void deterministicChatIgnoresSystemPrompt() {
        var reply = llmClient.chat("unrelated system prompt", "maintenance schedule", null);
        assertNotNull(reply);
        assertTrue(reply.toLowerCase().contains("maintenance") || reply.toLowerCase().contains("inspection"));
    }
}
