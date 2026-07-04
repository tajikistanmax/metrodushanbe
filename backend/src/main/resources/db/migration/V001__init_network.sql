-- V001: базовая схема сетевого каталога метрополитена.
-- Источник DDL: ТЗ metrodushanbe-v2.md §6.3.1 + docs/dev-conventions.md §4.
-- Таблицы: metro_line, metro_station, metro_station_line (M:N станция-линия).

CREATE EXTENSION IF NOT EXISTS postgis;

-- ---------------------------------------------------------------------------
-- Линии метрополитена
-- ---------------------------------------------------------------------------
CREATE TABLE metro_line (
    id             UUID PRIMARY KEY,
    code           VARCHAR(32)  NOT NULL UNIQUE,
    color_hex      VARCHAR(7)   NOT NULL,
    status         VARCHAR(32)  NOT NULL,
    name_i18n      JSONB        NOT NULL,
    sort_order     INT          NOT NULL DEFAULT 0,
    geom           geometry(MultiLineString, 4326),
    effective_from TIMESTAMPTZ  NOT NULL DEFAULT now(),
    effective_to   TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- Статусы линии — по dev-conventions §4
    CONSTRAINT ck_metro_line_status CHECK (
        status IN ('planned', 'under_construction', 'testing', 'active', 'suspended', 'decommissioned')
    ),
    CONSTRAINT ck_metro_line_color_hex CHECK (color_hex ~ '^#[0-9A-Fa-f]{6}$')
);

-- ---------------------------------------------------------------------------
-- Станции метрополитена
-- ---------------------------------------------------------------------------
CREATE TABLE metro_station (
    id               UUID PRIMARY KEY,
    code             VARCHAR(64)  NOT NULL UNIQUE,
    status           VARCHAR(32)  NOT NULL,
    name_i18n        JSONB        NOT NULL,
    description_i18n JSONB,
    point_geom       geometry(Point, 4326) NOT NULL,
    area_geom        geometry(Polygon, 4326),
    is_transfer      BOOLEAN      NOT NULL DEFAULT false,
    -- Массив признаков доступности: elevator|escalator|ramp|tactile|audio_assist (ТЗ §6.3.1, master data)
    accessibility    JSONB        NOT NULL DEFAULT '[]'::jsonb,
    attrs            JSONB        NOT NULL DEFAULT '{}'::jsonb,
    effective_from   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- Статусы станции — по dev-conventions §4
    CONSTRAINT ck_metro_station_status CHECK (
        status IN ('planned', 'under_construction', 'testing', 'active', 'temporarily_closed', 'decommissioned')
    )
);

-- ---------------------------------------------------------------------------
-- Связь станция-линия (M:N; position_index — порядковый номер станции на линии)
-- ---------------------------------------------------------------------------
CREATE TABLE metro_station_line (
    station_id     UUID        NOT NULL REFERENCES metro_station (id) ON DELETE CASCADE,
    line_id        UUID        NOT NULL REFERENCES metro_line (id) ON DELETE CASCADE,
    position_index INT         NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_metro_station_line PRIMARY KEY (station_id, line_id)
);

-- ---------------------------------------------------------------------------
-- Индексы: GiST по геометриям, GIN по i18n-названиям
-- ---------------------------------------------------------------------------
CREATE INDEX ix_metro_line_geom            ON metro_line USING GIST (geom);
CREATE INDEX ix_metro_station_point_geom   ON metro_station USING GIST (point_geom);
CREATE INDEX ix_metro_station_name_i18n_gin ON metro_station USING GIN (name_i18n);
CREATE INDEX ix_metro_line_name_i18n_gin    ON metro_line USING GIN (name_i18n);
CREATE INDEX ix_metro_station_line_line     ON metro_station_line (line_id, position_index);
