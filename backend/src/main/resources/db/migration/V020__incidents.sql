-- Операционный учёт инцидентов (ТЗ §6.2.6, макет photo/…13_42_39 «Инциденты»).
--
-- Отличие от service_alert: это ВНУТРЕННЯЯ запись — что случилось, насколько
-- критично, кто разбирает и чем закончилось. service_alert — то, что видит
-- пассажир. Один инцидент может породить публичное уведомление, но не обязан:
-- большинство инцидентов пассажира не касаются. Связь — public_alert_code.
--
-- Тексты здесь не i18n: их вводит оператор для внутреннего разбора, они не
-- публикуются. Публикуемый текст живёт в service_alert.title_i18n/body_i18n.
CREATE TABLE incident (
    id                uuid PRIMARY KEY,
    code              varchar(64) NOT NULL UNIQUE,
    category          varchar(24) NOT NULL,
    severity          varchar(16) NOT NULL,
    status            varchar(24) NOT NULL,
    title             varchar(200) NOT NULL,
    description       text NOT NULL,

    -- Стабильные коды metro_line.code / metro_station.code. FK намеренно нет —
    -- как и в service_alert_target: инцидент обязан пережить soft-delete или
    -- переименование объекта сети, историю нельзя терять каскадом.
    line_code         varchar(64),
    station_code      varchar(64),

    -- Логины admin_user. reported_by без FK: запись должна пережить удаление
    -- оператора. assigned_to проверяется на существование в сервисном слое.
    reported_by       varchar(64) NOT NULL,
    assigned_to       varchar(64),

    resolution        text,
    public_alert_code varchar(64),

    occurred_at       timestamptz NOT NULL,
    acknowledged_at   timestamptz,
    resolved_at       timestamptz,
    closed_at         timestamptz,
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT chk_incident_category CHECK (
        category IN ('safety', 'technical', 'passenger', 'infrastructure', 'other')
    ),
    CONSTRAINT chk_incident_severity CHECK (
        severity IN ('low', 'medium', 'high', 'critical')
    ),
    CONSTRAINT chk_incident_status CHECK (
        status IN ('open', 'acknowledged', 'in_progress', 'resolved', 'closed')
    ),
    -- Инцидент нельзя закрыть «молча»: у решённого обязан быть разбор.
    CONSTRAINT chk_incident_resolution CHECK (
        status NOT IN ('resolved', 'closed') OR resolution IS NOT NULL
    )
);

-- Основная выборка консоли: открытые сверху, внутри — по критичности и свежести.
CREATE INDEX ix_incident_queue ON incident (status, severity, occurred_at DESC);

-- Счётчики дашборда «инциденты за сегодня» и фильтр по исполнителю.
CREATE INDEX ix_incident_occurred ON incident (occurred_at DESC);
CREATE INDEX ix_incident_assignee ON incident (assigned_to) WHERE assigned_to IS NOT NULL;
