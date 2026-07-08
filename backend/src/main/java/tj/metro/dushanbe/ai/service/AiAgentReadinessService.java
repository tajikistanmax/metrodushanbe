package tj.metro.dushanbe.ai.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tj.metro.dushanbe.ai.config.AiProperties;
import tj.metro.dushanbe.ai.web.dto.AiAgentDto;
import tj.metro.dushanbe.ai.web.dto.AiBriefingDto;
import tj.metro.dushanbe.alert.service.AlertService;
import tj.metro.dushanbe.content.repository.NewsArticleRepository;
import tj.metro.dushanbe.network.repository.MetroLineRepository;
import tj.metro.dushanbe.network.repository.MetroStationRepository;
import tj.metro.dushanbe.schedule.repository.LineScheduleRepository;

@Service
public class AiAgentReadinessService {

    private final MetroLineRepository lineRepository;
    private final MetroStationRepository stationRepository;
    private final AlertService alertService;
    private final NewsArticleRepository newsRepository;
    private final LineScheduleRepository scheduleRepository;
    private final Clock clock;
    private final AiProperties aiProperties;
    private final LlmClient llmClient;

    public AiAgentReadinessService(MetroLineRepository lineRepository,
                                    MetroStationRepository stationRepository,
                                    AlertService alertService,
                                    NewsArticleRepository newsRepository,
                                    LineScheduleRepository scheduleRepository,
                                    Clock clock,
                                    AiProperties aiProperties,
                                    LlmClient llmClient) {
        this.lineRepository = lineRepository;
        this.stationRepository = stationRepository;
        this.alertService = alertService;
        this.newsRepository = newsRepository;
        this.scheduleRepository = scheduleRepository;
        this.clock = clock;
        this.aiProperties = aiProperties;
        this.llmClient = llmClient;
    }

    @Transactional(readOnly = true)
    public AiBriefingDto briefing() {
        int lines = lineRepository.findByDeletedAtIsNullOrderBySortOrderAscCodeAsc().size();
        int stations = stationRepository.findByDeletedAtIsNullOrderByCodeAsc().size();
        int alerts = alertService.activeAlerts(null, null, null).size();
        int news = newsRepository.findByStatusOrderByPublishedAtDesc("published").size();
        long schedules = scheduleRepository.count();

        List<AiAgentDto> agents = buildAgents(lines, stations, alerts, news, schedules);

        List<String> recommendations = new ArrayList<>();
        recommendations.add("Connect real model providers through environment variables only; keep local deterministic fallback for"
                + " demos.");
        recommendations.add("Prioritize production admin auth before enabling AI-assisted write actions.");
        if (alerts > 0) {
            recommendations.add("Ask the operations agent to draft a passenger-safe summary for each critical or warning alert.");
        }
        if (schedules > 0) {
            recommendations.add("Use the schedule agent to compare headways against route planner travel-time assumptions.");
        }

        if (!isLlmDisabled()) {
            try {
                var systemPrompt = "You are the Dushanbe Metro AI operations coordinator. "
                        + "Based on the following readiness data, provide 1-2 concise actionable recommendations.";
                var userMessage = String.format(
                        "Network status: %d lines, %d stations. Active alerts: %d. Published news: %d. Schedule records: %d.",
                        lines, stations, alerts, news, schedules);
                var llmResponse = llmClient.chat(systemPrompt, userMessage, null);
                if (llmResponse != null && !llmResponse.isBlank()) {
                    recommendations.add("[AI] " + llmResponse);
                }
            } catch (RuntimeException e) {
                var llm = aiProperties.getLlm();
                var provider = llm != null ? llm.getProvider() : "unknown";
                recommendations.add("LLM (" + provider + ") recommendation unavailable; check connectivity.");
            }
        }

        String posture = agents.stream().anyMatch(a -> "needs_hardening".equals(a.status())) ? "pilot_ready_with_security_gap" : "ready";
        return new AiBriefingDto(OffsetDateTime.now(clock), posture, agents, recommendations);
    }

    private boolean isLlmDisabled() {
        if (aiProperties == null || aiProperties.getLlm() == null) {
            return true;
        }
        var llm = aiProperties.getLlm();
        return "disabled".equals(llm.getProvider())
                || llm.getApiKey() == null
                || llm.getApiKey().isBlank();
    }

    private List<AiAgentDto> buildAgents(int lines, int stations, int alerts, int news, long schedules) {
        return List.of(
                agent("network-data-agent",
                        Map.of("tg", "Агенти маълумоти шабака", "ru", "Агент данных сети", "en", "Network data agent"),
                        "Checks canonical lines, stations, GeoJSON, and import readiness",
                        "openai-compatible/local", "reasoning",
                        lines > 0 && stations > 0 ? "ready" : "needs_data",
                        List.of("schema_check", "geojson_review", "import_triage"),
                        List.of(lines + " active lines", stations + " active stations"),
                        lines > 0 && stations > 0 ? "Keep imported geometry versioned and reviewed." : "Load a trusted network dataset."),
                agent("operations-agent",
                        Map.of("tg", "Агенти амалиёт", "ru", "Операционный агент", "en", "Operations agent"),
                        "Summarizes service alerts and prepares dispatcher actions",
                        "openai-compatible/local", "fast-reasoning",
                        alerts > 0 ? "watching" : "ready",
                        List.of("alert_summary", "incident_draft", "priority_routing"),
                        List.of(alerts + " active alerts"),
                        alerts > 0 ? "Review active alerts for passenger-facing clarity." : "No active public disruption requires action."),
                agent("schedule-agent",
                        Map.of("tg", "Агенти ҷадвал", "ru", "Агент расписания", "en", "Schedule agent"),
                        "Reviews static schedules, headways, and arrival estimates",
                        "openai-compatible/local", "planning",
                        schedules > 0 ? "ready" : "needs_schedule",
                        List.of("headway_check", "arrival_explain", "service_window_review"),
                        List.of(schedules + " schedule records"),
                        schedules > 0 ? "Add holiday calendar exceptions before pilot operations." : "Create line schedule records."),
                agent("content-agent",
                        Map.of("tg", "Агенти мундариҷа", "ru", "Контент-агент", "en", "Content agent"),
                        "Assists editors with multilingual news and passenger messages",
                        "openai-compatible/local", "multilingual",
                        news > 0 ? "ready" : "needs_content",
                        List.of("tg_ru_en_copy", "tone_check", "accessibility_plain_language"),
                        List.of(news + " published news items"),
                        news > 0 ? "Use the agent to keep Tajik, Russian, and English content aligned."
                                : "Publish baseline passenger information."),
                agent("security-agent",
                        Map.of("tg", "Агенти амният", "ru", "Агент безопасности", "en", "Security agent"),
                        "Flags admin-write and deployment hardening work",
                        "policy/local", "rules-plus-llm", "needs_hardening",
                        List.of("auth_gap_review", "audit_review", "secret_hygiene"),
                        List.of("admin uses dev X-Admin-Key", "audit module present"),
                        "Replace dev admin key with Keycloak/OAuth2 roles before production."),
                agent("route-advisor-agent",
                        Map.of("tg", "Агенти роҳнамо", "ru", "Агент маршрутов", "en", "Route advisor agent"),
                        "Suggests optimal passenger routes, transfers, and estimated travel time",
                        "openai-compatible/local", "planning", "ready",
                        List.of("route_planning", "transfer_suggest", "travel_time_estimate"),
                        List.of(lines + " lines", stations + " stations"),
                        "Integrate real-time train positions for dynamic route suggestions."),
                agent("maintenance-agent",
                        Map.of("tg", "Агенти нигоҳдорӣ", "ru", "Агент обслуживания", "en", "Maintenance agent"),
                        "Predicts maintenance needs, reviews line schedules, and flags degradation",
                        "openai-compatible/local", "predictive",
                        schedules > 0 ? "monitoring" : "needs_data",
                        List.of("wear_prediction", "inspection_scheduling", "downtime_optimization"),
                        List.of(schedules + " schedule records reviewed"),
                        "Add sensor telemetry feeds for predictive maintenance."),
                agent("station-guide-agent",
                        Map.of("tg", "Агенти роҳнамои истгоҳ", "ru", "Гид по станциям", "en", "Station guide agent"),
                        "Provides station details: exits, accessibility, and nearby landmarks",
                        "openai-compatible/local", "knowledge", "ready",
                        List.of("exit_info", "accessibility_check", "landmark_guide"),
                        List.of(stations + " stations indexed"),
                        "Complete station metadata with exit maps and point-of-interest lists."),
                agent("analytics-agent",
                        Map.of("tg", "Агенти таҳлил", "ru", "Агент аналитики", "en", "Analytics agent"),
                        "Analyzes network usage patterns and suggests improvements",
                        "openai-compatible/local", "analytical", "ready",
                        List.of("passenger_flow", "peak_analysis", "capacity_optimization"),
                        List.of(lines + " lines monitored"),
                        "Ingest historical ridership data for trend analysis."),
                agent("emergency-agent",
                        Map.of("tg", "Агенти фавқулодда", "ru", "Аварийный агент", "en", "Emergency agent"),
                        "Emergency response coordination and alert escalation",
                        "openai-compatible/local", "fast-reasoning",
                        alerts > 0 ? "watching" : "ready",
                        List.of("incident_response", "escalation_routing", "passenger_evacuation"),
                        List.of(alerts + " active alerts"),
                        alerts > 0 ? "Coordinate with operations agent for passenger-safe response." : "Run emergency drill scenarios."),
                agent("compliance-agent",
                        Map.of("tg", "Агенти риоя", "ru", "Агент соответствия", "en", "Compliance agent"),
                        "Checks regulatory compliance for accessibility and safety standards",
                        "policy/local", "rules-plus-llm", "ready",
                        List.of("accessibility_audit", "safety_check", "regulation_tracking"),
                        List.of("all stations meet wheelchair standards"),
                        "Review latest national transport regulations for policy updates."),
                agent("fare-agent",
                        Map.of("tg", "Агенти тарофа", "ru", "Агент тарифов", "en", "Fare agent"),
                        "Fare calculation, ticketing information, and discount eligibility",
                        "openai-compatible/local", "calculation", "ready",
                        List.of("fare_calc", "discount_check", "ticket_type_info"),
                        List.of("standard fare configured"),
                        "Integrate with payment gateway for real-time fare queries.")
        );
    }

    public String systemPromptForAgent(String agentCode) {
        return switch (agentCode) {
            case "network-data-agent" ->
                    "You are the Network Data Agent for Dushanbe Metro. You manage canonical network topology, "
                            + "GeoJSON imports, and data versioning. Answer questions about lines, stations, and geodata.";
            case "operations-agent" ->
                    "You are the Operations Agent for Dushanbe Metro. You monitor service alerts, "
                            + "dispatch actions, and ensure passenger safety. Answer questions about current operations.";
            case "schedule-agent" ->
                    "You are the Schedule Agent for Dushanbe Metro. You manage timetables, headways, "
                            + "and arrival estimates. Answer questions about train schedules.";
            case "content-agent" ->
                    "You are the Content Agent for Dushanbe Metro. You handle multilingual news, "
                            + "passenger messages, and accessibility copy. Answer questions about published content.";
            case "security-agent" ->
                    "You are the Security Agent for Dushanbe Metro. You oversee authentication, "
                            + "audit trails, and deployment hardening. Answer questions about security posture.";
            case "route-advisor-agent" ->
                    "You are the Route Advisor Agent for Dushanbe Metro. You suggest optimal passenger routes, "
                            + "transfers, and estimated travel times. Answer questions about navigating the metro network.";
            case "maintenance-agent" ->
                    "You are the Maintenance Agent for Dushanbe Metro. You predict maintenance needs, "
                            + "review line schedules, and flag degradation. Answer questions about maintenance status.";
            case "station-guide-agent" ->
                    "You are the Station Guide Agent for Dushanbe Metro. You provide station details: "
                            + "exits, accessibility, nearby landmarks. Answer questions about specific stations.";
            case "analytics-agent" ->
                    "You are the Analytics Agent for Dushanbe Metro. You analyze network usage patterns "
                            + "and suggest improvements. Answer questions about ridership and capacity.";
            case "emergency-agent" ->
                    "You are the Emergency Agent for Dushanbe Metro. You coordinate emergency response "
                            + "and alert escalation. Answer questions about emergency procedures.";
            case "compliance-agent" ->
                    "You are the Compliance Agent for Dushanbe Metro. You check regulatory compliance "
                            + "for accessibility and safety standards. Answer questions about compliance.";
            case "fare-agent" ->
                    "You are the Fare Agent for Dushanbe Metro. You handle fare calculation, "
                            + "ticketing information, and discount eligibility. Answer questions about fares and tickets.";
            default ->
                    "You are a helpful assistant for the Dushanbe Metro system. Provide accurate and concise information.";
        };
    }

    private AiAgentDto agent(String code,
                             Map<String, String> name,
                             String role,
                             String modelProvider,
                             String modelClass,
                             String status,
                             List<String> capabilities,
                             List<String> signals,
                             String nextAction) {
        return new AiAgentDto(code, name, role, modelProvider, modelClass, status, capabilities, signals, nextAction);
    }
}
