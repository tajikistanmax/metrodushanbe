-- Версия безопасности учётной записи. Увеличивается при смене пароля, роли
-- или активности и тем самым отзывает ранее выпущенные Next-сессии.
ALTER TABLE admin_user
    ADD COLUMN session_version bigint NOT NULL DEFAULT 0;
