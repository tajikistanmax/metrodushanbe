package tj.metro.dushanbe.ai.web.dto;

import java.util.Map;

public record AiChatRequest(
        String agentCode,
        String message,
        Map<String, Object> context
) {
}
