-- V007: детали станции — выходы и объекты доступности (NET-03).
-- Источник: ТЗ metrodushanbe-v2.md §5.1 (термины «выход», «доступность»),
-- §6.2.2 (NET-03: станция имеет выходы и доступность), ER-диаграмма §6.3:
-- STATION ||--o{ STATION_EXIT, STATION ||--o{ ACCESSIBILITY_FEATURE.
-- Таблицы: station_exit, accessibility_feature (FK на metro_station).

-- ---------------------------------------------------------------------------
-- Выходы станции (точки входа/выхода с координатой и признаком безбарьерности)
-- ---------------------------------------------------------------------------
CREATE TABLE station_exit (
    id            UUID PRIMARY KEY,
    station_id    UUID         NOT NULL REFERENCES metro_station (id) ON DELETE CASCADE,
    code          VARCHAR(64)  NOT NULL UNIQUE,
    name_i18n     JSONB        NOT NULL,
    point_geom    geometry(Point, 4326) NOT NULL,
    is_accessible BOOLEAN      NOT NULL DEFAULT false,
    sort_order    INT          NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- Объекты доступности станции (элементы безбарьерной среды со статусом)
-- ---------------------------------------------------------------------------
CREATE TABLE accessibility_feature (
    id               UUID PRIMARY KEY,
    station_id       UUID         NOT NULL REFERENCES metro_station (id) ON DELETE CASCADE,
    type             VARCHAR(32)  NOT NULL,
    description_i18n JSONB        NOT NULL,
    status           VARCHAR(32)  NOT NULL DEFAULT 'available',
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- Типы объектов доступности (надмножество тегов metro_station.accessibility)
    CONSTRAINT ck_accessibility_feature_type CHECK (
        type IN ('elevator', 'escalator', 'ramp', 'tactile', 'audio_assist', 'accessible_toilet')
    ),
    -- Статус работоспособности объекта доступности
    CONSTRAINT ck_accessibility_feature_status CHECK (
        status IN ('available', 'out_of_service', 'planned')
    )
);

-- ---------------------------------------------------------------------------
-- Индексы: FK-выборки по station_id, GiST по геометрии выхода, GIN по названию
-- ---------------------------------------------------------------------------
CREATE INDEX ix_station_exit_station           ON station_exit (station_id, sort_order);
CREATE INDEX ix_station_exit_point_geom        ON station_exit USING GIST (point_geom);
CREATE INDEX ix_station_exit_name_i18n_gin     ON station_exit USING GIN (name_i18n);
CREATE INDEX ix_accessibility_feature_station  ON accessibility_feature (station_id);
