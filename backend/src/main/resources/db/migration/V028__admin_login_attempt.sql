-- Блокировка входа при подборе пароля (аудит-пункт 9, full-project-audit-2026-07-17).
--
-- Номер: задание резервировало V026, но к моменту работы этот номер уже занял
-- параллельный агент (V026__demo_fare_products_inactive.sql), а V027 — seed. Взят
-- первый свободный V028: дублирование версии в объединённых locations db/migration+
-- db/seed валит Flyway («Found more than one migration»), поэтому коллизию нельзя было
-- оставить. Схема, не seed — файл в db/migration, применяется во всех средах.
--
-- ПОЧЕМУ таблица, а не память процесса. Счётчик неудач в ConcurrentHashMap
-- обнуляется при перезапуске: атакующему достаточно дождаться деплоя или вызвать
-- OOM, и перебор продолжается с нуля. Блокировка обязана переживать рестарт и быть
-- общей для всех инстансов за балансировщиком, поэтому состояние живёт в БД.
--
-- ПОЧЕМУ этого не делает app.rate-limit. Тот считает ЛЮБЫЕ запросы по IP, не
-- различает успешные и неудачные, ничего не знает о логине и тоже in-memory. Здесь
-- нужен именно строгий счётчик неудач по паре IP+логин и по учётной записи.
--
-- ДВЕ ОБЛАСТИ (scope):
--   account  — subject = логин. Медленный распределённый подбор одной учётки.
--   ip_user  — subject = 'ip|логин'. Быстрый перебор с одного адреса.
--
-- Счётчик заводится и для НЕсуществующего логина — это требование неразличимости
-- учёток: если бы блокировка появлялась только у реальных записей, ответ «слишком
-- много попыток» доказывал бы существование логина и перебор шёл бы через lockout.
CREATE TABLE admin_login_attempt (
    id               uuid PRIMARY KEY,
    scope            varchar(16) NOT NULL,
    subject          varchar(160) NOT NULL,
    failure_count    integer NOT NULL DEFAULT 0,
    first_failure_at timestamptz,
    last_failure_at  timestamptz,
    locked_until     timestamptz,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT chk_admin_login_attempt_scope CHECK (scope IN ('account', 'ip_user')),
    -- Счётчик не может быть отрицательным ни при какой гонке обновления.
    CONSTRAINT chk_admin_login_attempt_count CHECK (failure_count >= 0)
);

-- Один счётчик на пару (область, ключ). Уникальность и обслуживает upsert, и
-- сериализует параллельные попытки входа по одному ключу (SELECT ... FOR UPDATE).
CREATE UNIQUE INDEX ux_admin_login_attempt_scope_subject
    ON admin_login_attempt (scope, subject);
