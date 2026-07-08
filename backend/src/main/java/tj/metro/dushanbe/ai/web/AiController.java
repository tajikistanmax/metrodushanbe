package tj.metro.dushanbe.ai.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tj.metro.dushanbe.ai.config.AiProperties;
import tj.metro.dushanbe.ai.service.AiAgentReadinessService;
import tj.metro.dushanbe.ai.service.LlmClient;
import tj.metro.dushanbe.ai.web.dto.AiBriefingDto;
import tj.metro.dushanbe.ai.web.dto.AiChatRequest;
import tj.metro.dushanbe.ai.web.dto.AiChatResponse;

@RestController
@RequestMapping("/v1/ai")
@Tag(name = "AI Agents", description = "AI agent readiness and operational briefing")
public class AiController {

    private final AiAgentReadinessService readinessService;
    private final LlmClient llmClient;
    private final AiProperties aiProperties;

    public AiController(AiAgentReadinessService readinessService, LlmClient llmClient, AiProperties aiProperties) {
        this.readinessService = readinessService;
        this.llmClient = llmClient;
        this.aiProperties = aiProperties;
    }

    @GetMapping("/briefing")
    @Operation(summary = "AI agent readiness briefing",
            description = "Returns configured AI-agent roles, readiness signals, and recommended next actions. "
                    + "This endpoint is safe for MVP demos and does not require an external model key.")
    public AiBriefingDto briefing() {
        return readinessService.briefing();
    }

    @PostMapping("/chat")
    @Operation(summary = "Chat with an AI agent",
            description = "Sends a message to a specific AI agent and returns its reply. "
                    + "Uses the configured LLM provider when available, otherwise falls back to deterministic responses.")
    public AiChatResponse chat(@RequestBody AiChatRequest request) {
        var systemPrompt = readinessService.systemPromptForAgent(request.agentCode());
        var reply = llmClient.chat(systemPrompt, request.message(), null);
        var llm = aiProperties.getLlm();
        var isLlmActive = llm != null
                && !"disabled".equals(llm.getProvider())
                && llm.getApiKey() != null
                && !llm.getApiKey().isBlank();
        var sources = isLlmActive ? List.of(llm.getModel()) : List.of("deterministic");
        return new AiChatResponse(request.agentCode(), reply, sources);
    }
}
