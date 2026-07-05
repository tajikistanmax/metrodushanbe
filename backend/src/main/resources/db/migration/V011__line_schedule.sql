-- V011: статическое расписание движения линий (модуль schedule).
-- Источник: ТЗ metrodushanbe-v2.md §6.2.4 (SCH-01 — базовые графики по типам дня/периодам;
-- SCH-06 — история изменений через версионирование окон effective_from/effective_to).
-- Одна строка = интервальный график линии для типа дня: первое/последнее отправление
-- и headway (интервал движения). Прогнозные прибытия (SCH-03) считаются в сервисе
-- поверх этих графиков и являются ОЦЕНОЧНЫМИ (headway-based), до появления realtime.

CREATE TABLE line_schedule (
    id              UUID        PRIMARY KEY,
    -- Стабильный код линии (FK на metro_line.code — UNIQUE-колонка), как таргеты alert
    line_code       VARCHAR(32) NOT NULL REFERENCES metro_line (code),
    day_type        VARCHAR(16) NOT NULL,
    first_departure TIME        NOT NULL,
    last_departure  TIME        NOT NULL,
    headway_minutes INTEGER     NOT NULL,
    effective_from  DATE        NOT NULL,
    effective_to    DATE,                    -- NULL = бессрочно
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Типы дня — по контракту API (weekday|weekend|holiday)
    CONSTRAINT ck_line_schedule_day_type CHECK (day_type IN ('weekday', 'weekend', 'holiday')),
    CONSTRAINT ck_line_schedule_headway CHECK (headway_minutes > 0),
    CONSTRAINT ck_line_schedule_window CHECK (last_departure > first_departure),
    CONSTRAINT ck_line_schedule_effective CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

-- Основная выборка публичного API (LineScheduleRepository.findEffective):
-- line_code + day_type + окно effective_from/effective_to на дату «сегодня».
CREATE INDEX ix_line_schedule_lookup ON line_schedule (line_code, day_type, effective_from);

-- Не допускаем двух версий графика линии с одинаковым типом дня и датой начала действия.
CREATE UNIQUE INDEX ux_line_schedule_line_day_from ON line_schedule (line_code, day_type, effective_from);
