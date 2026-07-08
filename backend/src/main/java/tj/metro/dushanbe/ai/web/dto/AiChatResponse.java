package tj.metro.dushanbe.ai.web.dto;

import java.util.List;

public record AiChatResponse(
        String agentCode,
        String reply,
        List<String> sources
) {
}
