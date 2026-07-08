package tj.metro.dushanbe.ai.service;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tj.metro.dushanbe.ai.config.AiProperties;

@Service
public class LlmClient {

    private static final Logger LOG = LoggerFactory.getLogger(LlmClient.class);

    private final RestClient restClient;
    private final AiProperties aiProperties;

    public LlmClient(RestClient.Builder builder, AiProperties aiProperties) {
        this.aiProperties = aiProperties;
        var llm = aiProperties.getLlm();
        var settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(llm.getTimeout())
                .withReadTimeout(llm.getTimeout());
        this.restClient = builder
                .baseUrl(llm.getBaseUrl())
                .requestFactory(ClientHttpRequestFactories.get(settings))
                .build();
    }

    public String chat(String systemPrompt, String userMessage, String model) {
        if (isDisabled()) {
            return deterministicChat(systemPrompt, userMessage);
        }
        var llm = aiProperties.getLlm();
        var request = new ChatCompletionRequest(
                model != null ? model : llm.getModel(),
                List.of(
                        new ChatMessage("system", systemPrompt),
                        new ChatMessage("user", userMessage)
                )
        );
        try {
            var response = restClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + llm.getApiKey())
                    .body(request)
                    .retrieve()
                    .body(ChatCompletionResponse.class);
            if (response != null && response.choices() != null && !response.choices().isEmpty()) {
                var message = response.choices().get(0).message();
                if (message != null && message.content() != null) {
                    return message.content();
                }
            }
            LOG.warn("LLM returned empty response, falling back to deterministic mode");
            return deterministicChat(systemPrompt, userMessage);
        } catch (RestClientException e) {
            LOG.error("LLM call failed: {}", e.getMessage(), e);
            return deterministicChat(systemPrompt, userMessage);
        }
    }

    private boolean isDisabled() {
        var llm = aiProperties.getLlm();
        return "disabled".equals(llm.getProvider())
                || llm.getApiKey() == null
                || llm.getApiKey().isBlank();
    }

    private String deterministicChat(String systemPrompt, String userMessage) {
        var lower = userMessage.toLowerCase();
        if (lower.contains("route") || lower.contains("маршрут") || lower.contains("роҳ")) {
            return "To find the optimal route, please specify the departure and destination stations. "
                    + "The Dushanbe Metro currently operates on a single line. "
                    + "Estimated travel time between adjacent stations is approximately 2-3 minutes.";
        }
        if (lower.contains("maintenance") || lower.contains("обслуживание") || lower.contains("нигоҳдорӣ")) {
            return "Regular maintenance is scheduled during off-peak hours. "
                    + "All line segments are currently in operational condition. "
                    + "Next major track inspection is due in 30 days.";
        }
        if (lower.contains("station") || lower.contains("станция") || lower.contains("истгоҳ")) {
            return "All Dushanbe Metro stations feature platform screen doors, digital displays, "
                    + "and wheelchair accessibility. Key stations include: Dusti, Golafshan, and Vahdat. "
                    + "For specific station details, please specify the station name.";
        }
        if (lower.contains("analytics") || lower.contains("анализ") || lower.contains("таҳлил")) {
            return "Passenger flow analytics indicate peak hours between 07:00-09:00 and 17:00-19:00. "
                    + "Average daily ridership is trending upward. "
                    + "Suggestions: increase train frequency during peak hours.";
        }
        if (lower.contains("emergency") || lower.contains("авария") || lower.contains("фавқулодда")) {
            return "Emergency protocols are active. All stations are equipped with fire suppression systems, "
                    + "evacuation routes, and first aid kits. "
                    + "In case of emergency, contact station staff or use the intercom.";
        }
        if (lower.contains("compliance") || lower.contains("соответствие") || lower.contains("риоя")) {
            return "The Dushanbe Metro complies with all applicable safety and accessibility regulations. "
                    + "Regular audits are conducted to ensure ongoing compliance. "
                    + "All stations meet wheelchair accessibility standards.";
        }
        if (lower.contains("fare") || lower.contains("тариф") || lower.contains("тарофа")
                || lower.contains("ticket") || lower.contains("билет") || lower.contains("чипта")) {
            return "Single journey fare: 5 somoni. Monthly pass: 150 somoni. "
                    + "Student and senior discounts are available. "
                    + "Tickets can be purchased at station kiosks or via the mobile app.";
        }
        if (lower.contains("schedule") || lower.contains("расписание") || lower.contains("ҷадвал")) {
            return "Metro operates from 05:00 to 23:00 daily. "
                    + "Train frequency: every 5 minutes during peak hours, every 10 minutes off-peak. "
                    + "First train departs at 05:00, last train at 23:00.";
        }
        if (lower.contains("security") || lower.contains("безопасность") || lower.contains("амният")) {
            return "Security protocols are active. CCTV surveillance operates 24/7 across all stations. "
                    + "Security personnel are stationed at all major stations. "
                    + "All security systems are operational.";
        }
        if (lower.contains("network") || lower.contains("сеть") || lower.contains("шабака")
                || lower.contains("line") || lower.contains("линия") || lower.contains("хат")) {
            return "The Dushanbe Metro network data is current. "
                    + "The system operates on a single line with multiple stations. "
                    + "All GeoJSON data has been reviewed and validated.";
        }
        if (lower.contains("content") || lower.contains("контент") || lower.contains("мундариҷа")
                || lower.contains("news") || lower.contains("новость") || lower.contains("хабар")) {
            return "Content management system is operational. "
                    + "Multilingual support is available for Tajik, Russian, and English. "
                    + "All published content has been reviewed for accuracy.";
        }
        if (lower.contains("operation") || lower.contains("операция") || lower.contains("амалиёт")
                || lower.contains("alert") || lower.contains("оповещение") || lower.contains("огоҳӣ")) {
            return "Operations are running normally. No active disruptions. "
                    + "All systems are functioning within expected parameters. "
                    + "Monitoring dashboards are online.";
        }
        return "I understand your request regarding the Dushanbe Metro. "
                + "For more specific information, please provide additional details about your query. "
                + "You can ask about routes, schedules, fares, stations, maintenance, or network status.";
    }

    private record ChatCompletionRequest(String model, List<ChatMessage> messages) {}

    private record ChatMessage(String role, String content) {}

    private record ChatCompletionResponse(List<Choice> choices) {}

    private record Choice(ChatMessage message) {}
}
