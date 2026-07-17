-- V025: формат источника у заданий импорта (INT-04 «импорт GeoJSON/GTFS/CSV», MAP-08, U-OPS-06).
-- Источник: ТЗ metrodushanbe-v2.md §6.2.8 (INT-04), §13.2 (IMP-01 источник/формат/hash),
-- расширение схемы V010 (import_job/import_error).
--
-- Зачем отдельная колонка, если формат «виден» в type (network_geojson/network_gtfs/network_csv):
-- type отвечает на вопрос «ЧТО импортировали» (сеть; в перспективе — тарифы), format — «В КАКОМ
-- ВИДЕ пришёл источник». Смешивать их в одной строке значит заставлять и БД, и админку разбирать
-- составной идентификатор ради простого вопроса «покажи все GTFS-импорты»; при появлении второго
-- вида импорта (тарифы из GTFS Fares v2) комбинации размножатся, а фильтр по формату сломается.
--
-- Конвейер импорта до этой миграции работал только с GeoJSON, поэтому все существующие строки
-- по определению geojson — бэкфилл однозначен и не теряет информацию.

-- Шаг 1: колонка добавляется nullable — иначе ALTER упал бы на существующих строках.
ALTER TABLE import_job ADD COLUMN format VARCHAR(16);

-- Шаг 2: бэкфилл истории (до V025 иных форматов не существовало).
UPDATE import_job SET format = 'geojson' WHERE format IS NULL;

-- Шаг 3: только теперь колонка обязательна. DEFAULT намеренно НЕ ставим: формат обязан приходить
-- из запроса осознанно, а не «доставаться» молча при ошибке в коде.
ALTER TABLE import_job ALTER COLUMN format SET NOT NULL;

-- Значения обязаны совпадать с ImportFormat.codes() (Java-константы).
ALTER TABLE import_job ADD CONSTRAINT ck_import_job_format CHECK (
    format IN ('geojson', 'gtfs', 'csv')
);

-- Лента джобов фильтруется по формату и сортируется по времени (новые сверху) — покрываем
-- обе операции одним составным индексом.
CREATE INDEX ix_import_job_format_created_at ON import_job (format, created_at DESC);
