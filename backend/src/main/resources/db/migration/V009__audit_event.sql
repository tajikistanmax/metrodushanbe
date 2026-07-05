-- V009: модуль аудита действий (append-only audit log).
-- Источник: ТЗ metrodushanbe-v2.md §6.2.10 (ADM-05 «Аудит действий (append-only)»,
-- BR-ADM-1 «изменение ролей/прав, публикация alert, импорт, изменение тарифов/
-- расписаний — всегда с записью в аудит: актор, время, до/после»), §15.3 (audit_event).
--
-- Таблица append-only: строки только вставляются (UPDATE/DELETE не выполняются
-- прикладным кодом). before_state/after_state — снимки сущности до/после операции
-- (JSONB); для создания before_state = NULL, для soft-delete after_state отражает
-- удалённое состояние.
--
-- Дополнительно: колонки soft-delete deleted_at для линий и станций (BR-NET-2:
-- удаление станции/линии — только soft-delete; исторические версии сохраняются
-- для аудита). Публичные read-эндпоинты в этой фазе колонку не используют
-- (см. отчёт: временный admin-write контур).

-- ---------------------------------------------------------------------------
-- Журнал аудита (append-only)
-- ---------------------------------------------------------------------------
CREATE TABLE audit_event (
    id           UUID PRIMARY KEY,
    actor        VARCHAR(128) NOT NULL,          -- субъект действия (dev: из заголовка X-Admin-Actor)
    action       VARCHAR(64)  NOT NULL,          -- машиночитаемый код действия, напр. line.create
    entity_type  VARCHAR(64)  NOT NULL,          -- тип сущности: line|station|station_exit|accessibility_feature|alert|news
    entity_id    VARCHAR(160) NOT NULL,          -- стабильный код/слаг сущности
    before_state JSONB,                          -- снимок до операции (NULL для создания)
    after_state  JSONB,                          -- снимок после операции (NULL, если сущность удалена без снимка)
    at           TIMESTAMPTZ  NOT NULL DEFAULT now()  -- момент фиксации события
);

-- ---------------------------------------------------------------------------
-- Индексы под выборки журнала:
--  * по сущности (entity_type, entity_id) — история изменений конкретного объекта;
--  * по времени (at DESC) — общая лента аудита с пагинацией.
-- ---------------------------------------------------------------------------
CREATE INDEX ix_audit_event_entity ON audit_event (entity_type, entity_id);
CREATE INDEX ix_audit_event_at     ON audit_event (at DESC);

-- ---------------------------------------------------------------------------
-- Soft-delete для линий и станций (BR-NET-2). NULL = запись активна.
-- ---------------------------------------------------------------------------
ALTER TABLE metro_line    ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE metro_station ADD COLUMN deleted_at TIMESTAMPTZ;
