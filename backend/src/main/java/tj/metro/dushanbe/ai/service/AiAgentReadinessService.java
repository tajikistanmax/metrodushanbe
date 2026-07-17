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

        List<Map<String, String>> recommendations = new ArrayList<>();
        recommendations.add(AiAgentTexts.recommendation("env_only_providers"));
        recommendations.add(AiAgentTexts.recommendation("admin_auth_first"));
        if (alerts > 0) {
            recommendations.add(AiAgentTexts.recommendation("draft_alert_summaries"));
        }
        if (schedules > 0) {
            recommendations.add(AiAgentTexts.recommendation("compare_headways"));
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
                    // Ответ модели приходит одной строкой в рантайме — переводить нечего и некогда,
                    // кладём как есть во все три языка (см. AiAgentTexts#untranslated).
                    recommendations.add(AiAgentTexts.untranslated("[AI] " + llmResponse));
                }
            } catch (RuntimeException e) {
                var llm = aiProperties.getLlm();
                var provider = llm != null ? llm.getProvider() : "unknown";
                recommendations.add(AiAgentTexts.recommendation("llm_unavailable", provider));
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
                        "openai-compatible/local", "reasoning",
                        lines > 0 && stations > 0 ? "ready" : "needs_data",
                        AiAgentTexts.capabilities("schema_check", "geojson_review", "import_triage"),
                        List.of(AiAgentTexts.countSignal("active_lines", lines),
                                AiAgentTexts.countSignal("active_stations", stations)),
                        lines > 0 && stations > 0 ? "version_geometry" : "load_network_dataset"),
                agent("operations-agent",
                        Map.of("tg", "Агенти амалиёт", "ru", "Операционный агент", "en", "Operations agent"),
                        "openai-compatible/local", "fast-reasoning",
                        alerts > 0 ? "watching" : "ready",
                        AiAgentTexts.capabilities("alert_summary", "incident_draft", "priority_routing"),
                        List.of(AiAgentTexts.countSignal("active_alerts", alerts)),
                        alerts > 0 ? "review_alerts_clarity" : "no_disruption"),
                agent("schedule-agent",
                        Map.of("tg", "Агенти ҷадвал", "ru", "Агент расписания", "en", "Schedule agent"),
                        "openai-compatible/local", "planning",
                        schedules > 0 ? "ready" : "needs_schedule",
                        AiAgentTexts.capabilities("headway_check", "arrival_explain", "service_window_review"),
                        List.of(AiAgentTexts.countSignal("schedule_records", schedules)),
                        schedules > 0 ? "add_holiday_calendar" : "create_schedules"),
                agent("content-agent",
                        Map.of("tg", "Агенти мундариҷа", "ru", "Контент-агент", "en", "Content agent"),
                        "openai-compatible/local", "multilingual",
                        news > 0 ? "ready" : "needs_content",
                        AiAgentTexts.capabilities("tg_ru_en_copy", "tone_check", "accessibility_plain_language"),
                        List.of(AiAgentTexts.countSignal("published_news", news)),
                        news > 0 ? "align_languages" : "publish_baseline_content"),
                agent("security-agent",
                        Map.of("tg", "Агенти амният", "ru", "Агент безопасности", "en", "Security agent"),
                        "policy/local", "rules-plus-llm", "needs_hardening",
                        AiAgentTexts.capabilities("auth_gap_review", "audit_review", "secret_hygiene"),
                        List.of(AiAgentTexts.signal("dev_admin_key"),
                                AiAgentTexts.signal("audit_module_present")),
                        "replace_dev_admin_key"),
                agent("route-advisor-agent",
                        Map.of("tg", "Агенти роҳнамо", "ru", "Агент маршрутов", "en", "Route advisor agent"),
                        "openai-compatible/local", "planning", "ready",
                        AiAgentTexts.capabilities("route_planning", "transfer_suggest", "travel_time_estimate"),
                        List.of(AiAgentTexts.countSignal("lines", lines),
                                AiAgentTexts.countSignal("stations", stations)),
                        "integrate_realtime_positions"),
                agent("maintenance-agent",
                        Map.of("tg", "Агенти нигоҳдорӣ", "ru", "Агент обслуживания", "en", "Maintenance agent"),
                        "openai-compatible/local", "predictive",
                        schedules > 0 ? "monitoring" : "needs_data",
                        AiAgentTexts.capabilities("wear_prediction", "inspection_scheduling", "downtime_optimization"),
                        List.of(AiAgentTexts.countSignal("schedule_records_reviewed", schedules)),
                        "add_sensor_telemetry"),
                agent("station-guide-agent",
                        Map.of("tg", "Агенти роҳнамои истгоҳ", "ru", "Гид по станциям", "en", "Station guide agent"),
                        "openai-compatible/local", "knowledge", "ready",
                        AiAgentTexts.capabilities("exit_info", "accessibility_check", "landmark_guide"),
                        List.of(AiAgentTexts.countSignal("stations_indexed", stations)),
                        "complete_station_metadata"),
                agent("analytics-agent",
                        Map.of("tg", "Агенти таҳлил", "ru", "Агент аналитики", "en", "Analytics agent"),
                        "openai-compatible/local", "analytical", "ready",
                        AiAgentTexts.capabilities("passenger_flow", "peak_analysis", "capacity_optimization"),
                        List.of(AiAgentTexts.countSignal("lines_monitored", lines)),
                        "ingest_ridership_history"),
                agent("emergency-agent",
                        Map.of("tg", "Агенти фавқулодда", "ru", "Аварийный агент", "en", "Emergency agent"),
                        "openai-compatible/local", "fast-reasoning",
                        alerts > 0 ? "watching" : "ready",
                        AiAgentTexts.capabilities("incident_response", "escalation_routing", "passenger_evacuation"),
                        List.of(AiAgentTexts.countSignal("active_alerts", alerts)),
                        alerts > 0 ? "coordinate_with_operations" : "run_emergency_drills"),
                agent("compliance-agent",
                        Map.of("tg", "Агенти риоя", "ru", "Агент соответствия", "en", "Compliance agent"),
                        "policy/local", "rules-plus-llm", "ready",
                        AiAgentTexts.capabilities("accessibility_audit", "safety_check", "regulation_tracking"),
                        List.of(AiAgentTexts.signal("wheelchair_standards_met")),
                        "review_regulations"),
                agent("fare-agent",
                        Map.of("tg", "Агенти тарофа", "ru", "Агент тарифов", "en", "Fare agent"),
                        "openai-compatible/local", "calculation", "ready",
                        AiAgentTexts.capabilities("fare_calc", "discount_check", "ticket_type_info"),
                        List.of(AiAgentTexts.signal("standard_fare_configured")),
                        "integrate_payment_gateway")
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

    /**
     * Роль агента однозначно следует из его кода, поэтому берётся из каталога, а не передаётся
     * параметром: так роль и код не могут разъехаться. {@code nextActionCode} — код действия
     * (у части агентов их два, выбор зависит от состояния данных).
     */
    private AiAgentDto agent(String code,
                             Map<String, String> name,
                             String modelProvider,
                             String modelClass,
                             String status,
                             List<Map<String, String>> capabilities,
                             List<Map<String, String>> signals,
                             String nextActionCode) {
        return new AiAgentDto(code, name, AiAgentTexts.role(code), modelProvider, modelClass, status,
                capabilities, signals, AiAgentTexts.nextAction(nextActionCode));
    }
}
