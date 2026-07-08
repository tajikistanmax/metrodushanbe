package tj.metro.dushanbe.ai.web.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record AiBriefingDto(
        OffsetDateTime generatedAt,
        String posture,
        List<AiAgentDto> agents,
        List<String> recommendations
) {
}
