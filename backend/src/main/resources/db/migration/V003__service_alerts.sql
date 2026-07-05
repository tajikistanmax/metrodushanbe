-- V003: сервисные уведомления и инциденты (service alert lifecycle).
-- Источник: ТЗ metrodushanbe-v2.md §6.2.6 (жизненный цикл draft -> review -> approved ->
-- published -> superseded/expired) + BR-ALT-3 (таргетинг на линии/станции).
-- Таблицы: service_alert, service_alert_target (пустой таргетинг = alert на всю сеть).

-- ---------------------------------------------------------------------------
-- Сервисные уведомления
-- ---------------------------------------------------------------------------
CREATE TABLE service_alert (
    id           UUID PRIMARY KEY,
    code         VARCHAR(64)  NOT NULL UNIQUE,
    severity     VARCHAR(16)  NOT NULL,
    status       VARCHAR(16)  NOT NULL,
    title_i18n   JSONB        NOT NULL,
    body_i18n    JSONB        NOT NULL,
    starts_at    TIMESTAMPTZ  NOT NULL,
    ends_at      TIMESTAMPTZ,             -- NULL = бессрочно
    published_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- Уровни важности — по контракту API (info|warning|critical)
    CONSTRAINT ck_service_alert_severity CHECK (
        severity IN ('info', 'warning', 'critical')
    ),
    -- Статусы жизненного цикла — по диаграмме состояний ТЗ §6.2.6
    CONSTRAINT ck_service_alert_status CHECK (
        status IN ('draft', 'review', 'approved', 'published', 'superseded', 'expired')
    )
);

-- ---------------------------------------------------------------------------
-- Таргетинг уведомления на линии/станции (BR-ALT-3).
-- target_code ссылается на стабильные коды metro_line.code / metro_station.code.
-- ---------------------------------------------------------------------------
CREATE TABLE service_alert_target (
    alert_id    UUID        NOT NULL REFERENCES service_alert (id) ON DELETE CASCADE,
    target_type VARCHAR(16) NOT NULL,
    target_code VARCHAR(64) NOT NULL,
    CONSTRAINT pk_service_alert_target PRIMARY KEY (alert_id, target_type, target_code),
    CONSTRAINT ck_service_alert_target_type CHECK (target_type IN ('line', 'station'))
);

-- ---------------------------------------------------------------------------
-- Индекс под основную выборку публичного API
-- (ServiceAlertRepository.findActivePublished — предикаты окна вычисляются в БД):
-- status='published' AND starts_at <= :now AND (ends_at IS NULL OR ends_at > :now)
-- ---------------------------------------------------------------------------
CREATE INDEX ix_service_alert_status_window ON service_alert (status, starts_at, ends_at);
