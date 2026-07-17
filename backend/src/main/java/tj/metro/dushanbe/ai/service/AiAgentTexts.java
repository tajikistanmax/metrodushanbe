package tj.metro.dushanbe.ai.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Каталог свободных текстов AI-контура на tg/ru/en (dev-conventions.md, §6: все строки UI —
 * в словарях, без хардкода; языки tg по умолчанию, ru, en).
 *
 * <p><b>Почему тексты живут в Java, а на фронт уходят уже готовыми i18n-объектами.</b>
 * Рассматривались два варианта. (1) Backend отдаёт коды («signal.dev_admin_key»), консоль резолвит
 * их своим словарём. (2) Backend отдаёт {@code Map<String,String>} с полным набором языков.
 *
 * <p>Выбран (2), и вот почему вариант (1) здесь не работает. Сигналы содержат подставляемые
 * значения («Активных линий: 3»), то есть код пришлось бы тащить вместе с параметрами и городить
 * во фронте свой форматтер — механики, которой на платформе больше нигде нет. Рекомендация от LLM
 * вообще приходит текстом в рантайме, кода у неё быть не может в принципе. И главное: словарь
 * консоли молча ломается, когда backend добавляет новый код, — в UI вылезает сырой
 * «signal.dev_admin_key».
 *
 * <p>Вариант (2) ложится на приём, уже принятый на всей платформе: поля {@code *_i18n jsonb} —
 * это {@code Map<String,String>} с обязательными tg/ru/en, резолвинг {@code ?lang=} в backend не
 * реализован, язык выбирает фронт через {@code pickName}. Панель агентов теперь ничем не отличается
 * от линий, станций и новостей.
 *
 * <p>Цена варианта (2) — переводы в Java — снята тем, что этот класс их изолирует: ключи здесь
 * стабильные и проверяются на компиляции + тестом на полноту всех трёх языков, а
 * {@link AiAgentReadinessService} остаётся про логику готовности, а не про строки.
 */
final class AiAgentTexts {

    private AiAgentTexts() {
    }

    /** Роли агентов: чем агент занят. Ключ — код агента. */
    private static final Map<String, Map<String, String>> ROLES = Map.ofEntries(
            Map.entry("network-data-agent", of(
                    "Хатҳо, истгоҳҳо, GeoJSON ва омодагии воридотро месанҷад",
                    "Проверяет эталонные линии, станции, GeoJSON и готовность импорта",
                    "Checks canonical lines, stations, GeoJSON, and import readiness")),
            Map.entry("operations-agent", of(
                    "Огоҳиномаҳои хизматрасониро ҷамъбаст мекунад ва амалҳои диспетчерро тайёр менамояд",
                    "Сводит оповещения о работе сети и готовит действия диспетчера",
                    "Summarizes service alerts and prepares dispatcher actions")),
            Map.entry("schedule-agent", of(
                    "Ҷадвалҳои устувор, фосилаи ҳаракат ва пешбинии омадани қаторҳоро таҳлил мекунад",
                    "Разбирает статические расписания, интервалы движения и прогнозы прибытия",
                    "Reviews static schedules, headways, and arrival estimates")),
            Map.entry("content-agent", of(
                    "Ба муҳаррирон дар тайёр кардани хабарҳои бисёрзабона ва паёмҳо барои мусофирон кӯмак мекунад",
                    "Помогает редакторам с многоязычными новостями и сообщениями для пассажиров",
                    "Assists editors with multilingual news and passenger messages")),
            Map.entry("security-agent", of(
                    "Камбудиҳои ҳифзи навиштани админ ва ҷойгиркуниро нишон медиҳад",
                    "Отмечает пробелы в защите админ-записи и развёртывания",
                    "Flags admin-write and deployment hardening work")),
            Map.entry("route-advisor-agent", of(
                    "Роҳҳои беҳтарин, интиқолҳо ва вақти сафарро пешниҳод мекунад",
                    "Подсказывает оптимальные маршруты, пересадки и время в пути",
                    "Suggests optimal passenger routes, transfers, and estimated travel time")),
            Map.entry("maintenance-agent", of(
                    "Эҳтиёҷ ба хизматрасониро пешбинӣ мекунад, ҷадвали хатҳоро таҳлил ва бадшавии ҳолатро қайд менамояд",
                    "Прогнозирует потребность в обслуживании, разбирает расписания линий и отмечает деградацию",
                    "Predicts maintenance needs, reviews line schedules, and flags degradation")),
            Map.entry("station-guide-agent", of(
                    "Маълумоти истгоҳҳоро медиҳад: баромадгоҳҳо, дастрасӣ ва нишонаҳои наздик",
                    "Выдаёт справку по станциям: выходы, доступность и ориентиры рядом",
                    "Provides station details: exits, accessibility, and nearby landmarks")),
            Map.entry("analytics-agent", of(
                    "Тарзи истифодаи шабакаро таҳлил мекунад ва беҳбудӣ пешниҳод менамояд",
                    "Анализирует характер загрузки сети и предлагает улучшения",
                    "Analyzes network usage patterns and suggests improvements")),
            Map.entry("emergency-agent", of(
                    "Ҳамоҳангсозии вокуниши фавқулодда ва интиқоли огоҳиномаҳо ба сатҳи болотар",
                    "Координация аварийного реагирования и эскалация оповещений",
                    "Emergency response coordination and alert escalation")),
            Map.entry("compliance-agent", of(
                    "Мутобиқатро ба меъёрҳои дастрасӣ ва бехатарӣ месанҷад",
                    "Проверяет соответствие нормам доступности и безопасности",
                    "Checks regulatory compliance for accessibility and safety standards")),
            Map.entry("fare-agent", of(
                    "Ҳисоби тароф, маълумот дар бораи чиптаҳо ва ҳуқуқ ба имтиёз",
                    "Расчёт тарифа, сведения о билетах и право на льготы",
                    "Fare calculation, ticketing information, and discount eligibility"))
    );

    /** Возможности агентов — короткие подписи бейджей. Ключ — снейк-код возможности. */
    private static final Map<String, Map<String, String>> CAPABILITIES = Map.ofEntries(
            Map.entry("schema_check", of("Санҷиши схема", "Проверка схемы", "Schema check")),
            Map.entry("geojson_review", of("Таҳлили GeoJSON", "Разбор GeoJSON", "GeoJSON review")),
            Map.entry("import_triage", of("Таҳлили хатоҳои воридот", "Разбор ошибок импорта", "Import triage")),
            Map.entry("alert_summary", of("Ҷамъбасти огоҳиномаҳо", "Сводка оповещений", "Alert summary")),
            Map.entry("incident_draft", of("Лоиҳаи ҳодиса", "Черновик инцидента", "Incident draft")),
            Map.entry("priority_routing", of("Тақсим аз рӯи аввалият", "Маршрутизация по приоритету", "Priority routing")),
            Map.entry("headway_check", of("Санҷиши фосилаи ҳаракат", "Проверка интервалов", "Headway check")),
            Map.entry("arrival_explain", of("Шарҳи вақти омадан", "Пояснение прибытий", "Arrival explanation")),
            Map.entry("service_window_review", of("Таҳлили вақти хизматрасонӣ", "Разбор окон движения", "Service window review")),
            Map.entry("tg_ru_en_copy", of("Матн бо tg/ru/en", "Тексты на tg/ru/en", "Copy in tg/ru/en")),
            Map.entry("tone_check", of("Санҷиши оҳанг", "Проверка тона", "Tone check")),
            Map.entry("accessibility_plain_language", of("Забони содда", "Простой язык", "Plain language")),
            Map.entry("auth_gap_review", of("Таҳлили камбудиҳои аутентификатсия", "Разбор пробелов в аутентификации",
                    "Auth gap review")),
            Map.entry("audit_review", of("Таҳлили аудит", "Разбор аудита", "Audit review")),
            Map.entry("secret_hygiene", of("Тозагии калидҳои махфӣ", "Гигиена секретов", "Secret hygiene")),
            Map.entry("route_planning", of("Банақшагирии роҳ", "Планирование маршрута", "Route planning")),
            Map.entry("transfer_suggest", of("Пешниҳоди интиқол", "Подсказка пересадок", "Transfer suggestions")),
            Map.entry("travel_time_estimate", of("Арзёбии вақти сафар", "Оценка времени в пути", "Travel time estimate")),
            Map.entry("wear_prediction", of("Пешбинии фарсудашавӣ", "Прогноз износа", "Wear prediction")),
            Map.entry("inspection_scheduling", of("Банақшагирии азназаргузаронӣ", "Планирование осмотров",
                    "Inspection scheduling")),
            Map.entry("downtime_optimization", of("Кам кардани таваққуф", "Оптимизация простоев", "Downtime optimization")),
            Map.entry("exit_info", of("Маълумоти баромадгоҳҳо", "Справка по выходам", "Exit information")),
            Map.entry("accessibility_check", of("Санҷиши дастрасӣ", "Проверка доступности", "Accessibility check")),
            Map.entry("landmark_guide", of("Роҳнамои нишонаҳо", "Ориентиры рядом", "Landmark guide")),
            Map.entry("passenger_flow", of("Ҷараёни мусофирон", "Пассажиропоток", "Passenger flow")),
            Map.entry("peak_analysis", of("Таҳлили вақти серодам", "Анализ пиков", "Peak analysis")),
            Map.entry("capacity_optimization", of("Беҳинасозии иқтидор", "Оптимизация провозной способности",
                    "Capacity optimization")),
            Map.entry("incident_response", of("Вокуниш ба ҳодисаҳо", "Реагирование на инциденты", "Incident response")),
            Map.entry("escalation_routing", of("Роҳи интиқол ба сатҳи болотар", "Маршрут эскалации", "Escalation routing")),
            Map.entry("passenger_evacuation", of("Эвакуатсияи мусофирон", "Эвакуация пассажиров", "Passenger evacuation")),
            Map.entry("accessibility_audit", of("Аудити дастрасӣ", "Аудит доступности", "Accessibility audit")),
            Map.entry("safety_check", of("Санҷиши бехатарӣ", "Проверка безопасности", "Safety check")),
            Map.entry("regulation_tracking", of("Пайгирии меъёрҳо", "Отслеживание норм", "Regulation tracking")),
            Map.entry("fare_calc", of("Ҳисоби тароф", "Расчёт тарифа", "Fare calculation")),
            Map.entry("discount_check", of("Санҷиши имтиёзҳо", "Проверка льгот", "Discount check")),
            Map.entry("ticket_type_info", of("Навъҳои чипта", "Типы билетов", "Ticket types"))
    );

    /**
     * Подписи числовых сигналов. Значение приклеивается через «: N», а не вшивается в фразу
     * («3 active lines»): так подпись не зависит от числа и не требует согласования множественного
     * числа ни в русском (линия/линии/линий), ни в таджикском.
     */
    private static final Map<String, Map<String, String>> COUNT_SIGNALS = Map.ofEntries(
            Map.entry("active_lines", of("Хатҳои фаъол", "Активных линий", "Active lines")),
            Map.entry("active_stations", of("Истгоҳҳои фаъол", "Активных станций", "Active stations")),
            Map.entry("active_alerts", of("Огоҳиномаҳои фаъол", "Активных оповещений", "Active alerts")),
            Map.entry("schedule_records", of("Сабтҳои ҷадвал", "Записей расписания", "Schedule records")),
            Map.entry("published_news", of("Хабарҳои нашршуда", "Опубликованных новостей", "Published news items")),
            Map.entry("lines", of("Хатҳо", "Линий", "Lines")),
            Map.entry("stations", of("Истгоҳҳо", "Станций", "Stations")),
            Map.entry("schedule_records_reviewed", of("Сабтҳои ҷадвали санҷидашуда", "Проверено записей расписания",
                    "Schedule records reviewed")),
            Map.entry("stations_indexed", of("Истгоҳҳои индексшуда", "Проиндексировано станций", "Stations indexed")),
            Map.entry("lines_monitored", of("Хатҳои таҳтиназар", "Под наблюдением линий", "Lines monitored"))
    );

    /** Сигналы без чисел — констатация факта о контуре. */
    private static final Map<String, Map<String, String>> SIGNALS = Map.ofEntries(
            // Не «агент так считает», а реальная дыра dev-контура: admin-API до сих пор пускает
            // по дефолтному X-Admin-Key (см. AdminKeyAuthFilter). Формулировка не смягчена намеренно.
            Map.entry("dev_admin_key", of(
                    "Контури админ то ҳол бо калиди dev-и X-Admin-Key кор мекунад",
                    "Админ-контур до сих пор работает на dev-ключе X-Admin-Key",
                    "Admin API still runs on the dev X-Admin-Key")),
            Map.entry("audit_module_present", of(
                    "Модули аудит пайваст аст",
                    "Модуль аудита подключён",
                    "Audit module present")),
            Map.entry("wheelchair_standards_met", of(
                    "Ҳамаи истгоҳҳо ба меъёрҳои дастрасӣ барои аробачаҳо мувофиқанд",
                    "Все станции соответствуют стандартам доступности для колясок",
                    "All stations meet wheelchair standards")),
            Map.entry("standard_fare_configured", of(
                    "Тарофи асосӣ танзим шудааст",
                    "Базовый тариф настроен",
                    "Standard fare configured"))
    );

    /** Рекомендуемое следующее действие по агенту. Ключ — код действия, не код агента: у части агентов их два. */
    private static final Map<String, Map<String, String>> NEXT_ACTIONS = Map.ofEntries(
            Map.entry("version_geometry", of(
                    "Геометрияи воридшударо таҳти версиябандӣ ва тафтиш нигоҳ доред.",
                    "Держите импортированную геометрию под версионированием и ревью.",
                    "Keep imported geometry versioned and reviewed.")),
            Map.entry("load_network_dataset", of(
                    "Маҷмӯи мӯътамади маълумоти шабакаро бор кунед.",
                    "Загрузите доверенный набор данных сети.",
                    "Load a trusted network dataset.")),
            Map.entry("review_alerts_clarity", of(
                    "Огоҳиномаҳои фаъолро аз нигоҳи фаҳмо будан барои мусофирон санҷед.",
                    "Проверьте активные оповещения на понятность для пассажиров.",
                    "Review active alerts for passenger-facing clarity.")),
            Map.entry("no_disruption", of(
                    "Ихтилоли ҷамъиятии фаъоле, ки амал талаб кунад, нест.",
                    "Активных публичных сбоев, требующих действий, нет.",
                    "No active public disruption requires action.")),
            Map.entry("add_holiday_calendar", of(
                    "Пеш аз оғози озмоиш истиснои рӯзҳои идро ба тақвим илова кунед.",
                    "Добавьте праздничные исключения календаря до начала пилота.",
                    "Add holiday calendar exceptions before pilot operations.")),
            Map.entry("create_schedules", of(
                    "Сабтҳои ҷадвали хатҳоро эҷод кунед.",
                    "Создайте записи расписания линий.",
                    "Create line schedule records.")),
            Map.entry("align_languages", of(
                    "Барои ҳамоҳанг нигоҳ доштани матнҳои тоҷикӣ, русӣ ва англисӣ аз агент истифода баред.",
                    "Используйте агента, чтобы держать тексты на таджикском, русском и английском согласованными.",
                    "Use the agent to keep Tajik, Russian, and English content aligned.")),
            Map.entry("publish_baseline_content", of(
                    "Маълумоти асосиро барои мусофирон нашр кунед.",
                    "Опубликуйте базовую информацию для пассажиров.",
                    "Publish baseline passenger information.")),
            Map.entry("replace_dev_admin_key", of(
                    "Пеш аз истифодаи саноатӣ калиди dev-и админро бо нақшҳои Keycloak/OAuth2 иваз кунед.",
                    "До продакшена замените dev-ключ админа на роли Keycloak/OAuth2.",
                    "Replace dev admin key with Keycloak/OAuth2 roles before production.")),
            Map.entry("integrate_realtime_positions", of(
                    "Барои пешниҳоди динамикии роҳ мавқеи қаторҳоро дар вақти воқеӣ пайваст кунед.",
                    "Подключите позиции поездов в реальном времени для динамических подсказок маршрута.",
                    "Integrate real-time train positions for dynamic route suggestions.")),
            Map.entry("add_sensor_telemetry", of(
                    "Барои хизматрасонии пешбинишаванда телеметрияи сенсорҳоро илова кунед.",
                    "Добавьте телеметрию датчиков для предиктивного обслуживания.",
                    "Add sensor telemetry feeds for predictive maintenance.")),
            Map.entry("complete_station_metadata", of(
                    "Метамаълумоти истгоҳҳоро бо нақшаи баромадгоҳҳо ва рӯйхати нишонаҳо пурра кунед.",
                    "Дополните метаданные станций схемами выходов и списками ориентиров.",
                    "Complete station metadata with exit maps and point-of-interest lists.")),
            Map.entry("ingest_ridership_history", of(
                    "Барои таҳлили тамоюлҳо маълумоти таърихии ҷараёни мусофиронро бор кунед.",
                    "Загрузите исторические данные о пассажиропотоке для анализа трендов.",
                    "Ingest historical ridership data for trend analysis.")),
            Map.entry("coordinate_with_operations", of(
                    "Барои вокуниши бехатар барои мусофирон бо агенти амалиёт ҳамоҳанг кунед.",
                    "Согласуйте реагирование с операционным агентом, чтобы оно было безопасным для пассажиров.",
                    "Coordinate with operations agent for passenger-safe response.")),
            Map.entry("run_emergency_drills", of(
                    "Сенарияҳои машқи фавқулоддаро гузаронед.",
                    "Прогоните сценарии аварийных учений.",
                    "Run emergency drill scenarios.")),
            Map.entry("review_regulations", of(
                    "Меъёрҳои охирини миллии нақлиётро барои навсозии сиёсатҳо аз назар гузаронед.",
                    "Сверьтесь с последними национальными транспортными нормами и обновите политики.",
                    "Review latest national transport regulations for policy updates.")),
            Map.entry("integrate_payment_gateway", of(
                    "Барои дархости тароф дар вақти воқеӣ дарвозаи пардохтро пайваст кунед.",
                    "Подключите платёжный шлюз для запросов тарифа в реальном времени.",
                    "Integrate with payment gateway for real-time fare queries."))
    );

    /** Рекомендации брифинга. Значения с «%s» форматируются через {@link #recommendation(String, Object...)}. */
    private static final Map<String, Map<String, String>> RECOMMENDATIONS = Map.ofEntries(
            Map.entry("env_only_providers", of(
                    "Провайдерҳои воқеии моделҳоро танҳо тавассути тағйирёбандаҳои муҳит пайваст кунед; "
                            + "fallback-и маҳаллии детерминистиро барои намоиш нигоҳ доред.",
                    "Подключайте реальных провайдеров моделей только через переменные окружения; "
                            + "локальный детерминированный fallback оставьте для демо.",
                    "Connect real model providers through environment variables only; keep local deterministic "
                            + "fallback for demos.")),
            Map.entry("admin_auth_first", of(
                    "Пеш аз фаъол кардани навиштан бо кӯмаки AI, аутентификатсияи саноатии админро ҷорӣ кунед.",
                    "Сначала введите продакшен-аутентификацию админа, только потом включайте запись с помощью AI.",
                    "Prioritize production admin auth before enabling AI-assisted write actions.")),
            Map.entry("draft_alert_summaries", of(
                    "Ба агенти амалиёт супоред, ки барои ҳар огоҳиномаи ҳассос ё огоҳкунанда ҷамъбасти барои "
                            + "мусофирон бехатар тайёр кунад.",
                    "Поручите операционному агенту подготовить безопасную для пассажиров сводку по каждому "
                            + "критическому и предупреждающему оповещению.",
                    "Ask the operations agent to draft a passenger-safe summary for each critical or warning alert.")),
            Map.entry("compare_headways", of(
                    "Бо ёрии агенти ҷадвал фосилаи ҳаракатро бо тахминҳои вақти сафари банақшагири роҳ муқоиса кунед.",
                    "Сверьте интервалы движения с допущениями планировщика маршрутов через агента расписания.",
                    "Use the schedule agent to compare headways against route planner travel-time assumptions.")),
            Map.entry("llm_unavailable", of(
                    "Тавсияи LLM (%s) дастрас нест: пайвастро санҷед.",
                    "Рекомендация LLM (%s) недоступна: проверьте связь.",
                    "LLM (%s) recommendation unavailable; check connectivity."))
    );

    /** i18n-объект в порядке tg/ru/en — tg первым, это язык платформы по умолчанию. */
    static Map<String, String> of(String tg, String ru, String en) {
        Map<String, String> text = new LinkedHashMap<>();
        text.put("tg", tg);
        text.put("ru", ru);
        text.put("en", en);
        return Map.copyOf(text);
    }

    static Map<String, String> role(String agentCode) {
        return require(ROLES, agentCode, "role");
    }

    static List<Map<String, String>> capabilities(String... capabilityCodes) {
        List<Map<String, String>> texts = new ArrayList<>(capabilityCodes.length);
        for (String code : capabilityCodes) {
            texts.add(require(CAPABILITIES, code, "capability"));
        }
        return List.copyOf(texts);
    }

    /** Числовой сигнал: «Активных линий: 3». */
    static Map<String, String> countSignal(String signalCode, long value) {
        Map<String, String> label = require(COUNT_SIGNALS, signalCode, "signal");
        Map<String, String> text = new LinkedHashMap<>();
        label.forEach((language, caption) -> text.put(language, caption + ": " + value));
        return Map.copyOf(text);
    }

    static Map<String, String> signal(String signalCode) {
        return require(SIGNALS, signalCode, "signal");
    }

    static Map<String, String> nextAction(String actionCode) {
        return require(NEXT_ACTIONS, actionCode, "action");
    }

    static Map<String, String> recommendation(String recommendationCode, Object... args) {
        Map<String, String> template = require(RECOMMENDATIONS, recommendationCode, "recommendation");
        if (args.length == 0) {
            return template;
        }
        Map<String, String> text = new LinkedHashMap<>();
        template.forEach((language, value) -> text.put(language, String.format(value, args)));
        return Map.copyOf(text);
    }

    /**
     * Текст, который перевести неоткуда: ответ LLM рождается в рантайме одной строкой. Кладём его
     * во все три языка, чтобы контракт остался однородным — фронт всегда делает pickName и никогда
     * не получает пустоту.
     */
    static Map<String, String> untranslated(String text) {
        return of(text, text, text);
    }

    private static Map<String, String> require(Map<String, Map<String, String>> catalogue, String code, String kind) {
        Map<String, String> text = catalogue.get(code);
        if (text == null) {
            throw new IllegalStateException("Нет tg/ru/en-перевода для " + kind + " '" + code + "'");
        }
        return text;
    }
}
