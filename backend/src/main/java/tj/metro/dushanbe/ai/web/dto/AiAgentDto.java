package tj.metro.dushanbe.ai.web.dto;

import java.util.List;
import java.util.Map;

public record AiAgentDto(
        String code,
        Map<String, String> name,
        String role,
        String modelProvider,
        String modelClass,
        String status,
        List<String> capabilities,
        List<String> signals,
        String nextAction
) {
}
