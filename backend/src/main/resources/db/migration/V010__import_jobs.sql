-- V010: модуль импорта данных сети (Data Intake Pipeline, MVP).
-- Источник: ТЗ metrodushanbe-v2.md §6.2.8 (INT-04 «импорт GeoJSON/GTFS/CSV»),
-- §13.2 (IMP-01 источник/файл/hash/версия/оператор; IMP-02 идемпотентность;
-- IMP-03 ошибки построчно/посущностно), ER §6.3.1 / §15.3 (import_job, import_error).
--
-- Схема под MVP-импорт сети из GeoJSON FeatureCollection (линии/станции). Каждый
-- запуск импорта = строка import_job со счётчиками (created/updated/failed) и статусом;
-- невалидные/проблемные фичи фиксируются построчно в import_error (IMP-03).
-- Применение импорта выполняется синхронно (PERF-05: фоновые джобы — TODO), но модель
-- уже готова под асинхронный жизненный цикл (pending → running → success|partial|failed).

-- ---------------------------------------------------------------------------
-- Задание импорта (одна строка на запуск конвейера)
-- ---------------------------------------------------------------------------
CREATE TABLE import_job (
    id            UUID PRIMARY KEY,
    type          VARCHAR(32)  NOT NULL,            -- вид импорта, напр. network_geojson (задел под gtfs/csv)
    status        VARCHAR(16)  NOT NULL,            -- pending|running|success|partial|failed
    source_name   VARCHAR(256),                     -- имя источника/файла (IMP-01), из заголовка X-Import-Source
    source_hash   VARCHAR(64),                      -- SHA-256 тела импорта в hex (IMP-01, идемпотентность IMP-02)
    feature_count INT          NOT NULL DEFAULT 0,  -- сколько фич распознано во входе
    created_count INT          NOT NULL DEFAULT 0,  -- создано сущностей
    updated_count INT          NOT NULL DEFAULT 0,  -- обновлено сущностей
    failed_count  INT          NOT NULL DEFAULT 0,  -- фич отклонено (невалидны/ошибка применения)
    started_at    TIMESTAMPTZ,                      -- начало выполнения (NULL пока pending)
    finished_at   TIMESTAMPTZ,                      -- завершение (NULL пока не финализирован)
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_import_job_status CHECK (
        status IN ('pending', 'running', 'success', 'partial', 'failed')
    )
);

-- ---------------------------------------------------------------------------
-- Ошибки импорта (построчно/посущностно, IMP-03)
-- ---------------------------------------------------------------------------
CREATE TABLE import_error (
    id          UUID PRIMARY KEY,
    job_id      UUID         NOT NULL REFERENCES import_job (id) ON DELETE CASCADE,
    feature_ref VARCHAR(160) NOT NULL,              -- ссылка на фичу (её code/id), либо '$' для ошибок верхнего уровня
    message     TEXT         NOT NULL,              -- человекочитаемое описание нарушения
    severity    VARCHAR(16)  NOT NULL DEFAULT 'error',  -- error (фича отклонена) | warning (применена с оговоркой)
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_import_error_severity CHECK (severity IN ('error', 'warning'))
);

-- ---------------------------------------------------------------------------
-- Индексы: лента джобов по времени; ошибки конкретного джоба
-- ---------------------------------------------------------------------------
CREATE INDEX ix_import_job_created_at ON import_job (created_at DESC);
CREATE INDEX ix_import_error_job      ON import_error (job_id);
