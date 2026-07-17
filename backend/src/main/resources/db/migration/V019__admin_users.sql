-- Пользователи операционной консоли и их роли (ТЗ §6.1.7, §9.2 — RBAC).
--
-- Заменяет dev-схему «один общий пароль из ADMIN_UI_PASSWORD» на именованных
-- операторов: у каждого действия в audit_event появляется реальный актор.
--
-- Пароли хранятся ТОЛЬКО как BCrypt-хеш. Сид пользователя здесь намеренно
-- отсутствует: хеш зависит от соли, поэтому дев-суперадмин создаётся на старте
-- приложения (AdminUserBootstrap) из app.admin.bootstrap.* — так в репозиторий
-- не попадает ни пароль, ни его хеш.
CREATE TABLE admin_user (
    id            uuid PRIMARY KEY,
    username      varchar(64) NOT NULL UNIQUE,
    display_name  varchar(128) NOT NULL,
    password_hash varchar(72) NOT NULL,
    role          varchar(24) NOT NULL,
    is_active     boolean NOT NULL DEFAULT true,
    last_login_at timestamptz NULL,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_admin_user_role CHECK (
        role IN ('superadmin', 'editor', 'operator', 'viewer')
    ),
    CONSTRAINT chk_admin_user_username CHECK (username = lower(username))
);

-- Список консоли сортируется по роли и логину; активность фильтруется при входе.
CREATE INDEX ix_admin_user_listing ON admin_user (role, username);
CREATE INDEX ix_admin_user_active ON admin_user (is_active, username);
