-- Индексы производительности (PERF-03): ускорение частых запросов и пагинации.
--
-- ВНИМАНИЕ: имена колонок сверены с фактической схемой (V001, V003, V005, V007,
-- V009, V010, V011). Часть «наивных» индексов из первой редакции ссылалась на
-- несуществующие колонки (line_code в metro_station_line, station_code в
-- station_exit/accessibility_feature, action_type/created_at в audit_event,
-- name_tg/ru/en в metro_station) и/или дублировала уже существующие индексы —
-- такие удалены или исправлены. Оставлены только валидные, не дублирующие индексы.

-- Линии: поиск по статусу + сортировка (публичный API)
CREATE INDEX IF NOT EXISTS idx_metro_line_status_sort
    ON metro_line (status, sort_order);

-- Станции: публичный список (только active, сортировка по коду)
CREATE INDEX IF NOT EXISTS idx_metro_station_status_code
    ON metro_station (status, code);

-- Уведомления: активные по времени (основной запрос AlertService.activeAlerts)
CREATE INDEX IF NOT EXISTS idx_service_alert_active_period
    ON service_alert (starts_at, ends_at, severity);

-- Новости: опубликованные по дате (основной запрос публичного API)
CREATE INDEX IF NOT EXISTS idx_news_article_published
    ON news_article (status, published_at DESC);

-- Аудит: события по действию и времени (админка/фильтры).
-- Колонки в audit_event (V009): action, at — НЕ action_type/created_at.
CREATE INDEX IF NOT EXISTS idx_audit_event_action_at
    ON audit_event (action, at DESC);

-- Импорт: джобы по статусу (мониторинг импорта)
CREATE INDEX IF NOT EXISTS idx_import_job_status
    ON import_job (status, created_at DESC);

-- Расписания: поиск по линии и дню (ScheduleService)
CREATE INDEX IF NOT EXISTS idx_line_schedule_line_day
    ON line_schedule (line_code, day_type);

-- Примечание. Уже покрыты существующими индексами и потому здесь НЕ создаются:
--   * выборка станций по линии — ix_metro_station_line_line (line_id, position_index), V001;
--   * выходы по станции        — ix_station_exit_station (station_id, sort_order), V007;
--   * доступность по станции    — ix_accessibility_feature_station (station_id), V007;
--   * поиск по названиям (i18n) — ix_metro_station_name_i18n_gin GIN(name_i18n), V001.
