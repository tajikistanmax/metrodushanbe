-- Модуль уведомлений (ТЗ §6.2.7, NTF-01…06).
--
-- Отличие от service_alert: service_alert — это СОСТОЯНИЕ сети («на L1 сбой»),
-- которое висит, пока действует, и которое пассажир видит на карте. Уведомление
-- здесь — СОБЫТИЕ рассылки: единичный факт «текст ушёл получателям по каналам».
-- Алерт живёт и снимается, уведомление отправляется и остаётся в истории. Один
-- алерт может породить рассылку, но не обязан, и наоборот (промо-рассылка не
-- имеет алерта). Поэтому таблицы раздельные, связь — alert_code без FK.
--
-- Каналы (NTF-01) и таргеты (NTF-02) вынесены в jsonb/дочернюю таблицу по разным
-- причинам, и это не непоследовательность:
--   channels — короткий фиксированный набор, выбираемый один раз на рассылку и
--     всегда читаемый целиком вместе с сообщением; отдельная таблица дала бы
--     join ради двух строк.
--   targets — переменной длины, по ним идёт ПОИСК (какие рассылки касаются
--     станции X), и им нужен индекс — как и service_alert_target.

CREATE TABLE notification_template (
    id            uuid PRIMARY KEY,
    code          varchar(64) NOT NULL UNIQUE,
    name          varchar(200) NOT NULL,
    type          varchar(24) NOT NULL,

    -- Тексты шаблона публикуемые, поэтому i18n обязателен (NTF-04).
    title_i18n    jsonb NOT NULL,
    body_i18n     jsonb NOT NULL,

    -- Массив кодов каналов, напр. ["in_app","push"]. Состав проверяется в
    -- сервисном слое: CHECK по элементам jsonb-массива нечитаем и не переживёт
    -- добавления канала.
    channels      jsonb NOT NULL,

    active        boolean NOT NULL DEFAULT true,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT chk_notification_template_type CHECK (
        type IN ('info', 'warning', 'incident', 'maintenance', 'promo')
    )
);

COMMENT ON TABLE notification_template IS
    'Заготовки текстов рассылок (NTF-05). Шаблон копируется в сообщение при создании.';

CREATE TABLE notification_message (
    id             uuid PRIMARY KEY,
    code           varchar(64) NOT NULL UNIQUE,

    -- Шаблон-источник. Без FK и без каскада: шаблон могут вывести из
    -- употребления, но история рассылки обязана пережить это — тексты уже
    -- скопированы в title_i18n/body_i18n и от шаблона больше не зависят.
    template_code  varchar(64),

    -- Код связанного service_alert, если рассылка сделана по алерту. Без FK по
    -- той же причине, что и в incident.public_alert_code.
    alert_code     varchar(64),

    type           varchar(24) NOT NULL,
    title_i18n     jsonb NOT NULL,
    body_i18n      jsonb NOT NULL,
    channels       jsonb NOT NULL,
    status         varchar(16) NOT NULL,

    -- Отложенная публикация (NTF-05): момент, с которого рассылку разрешено
    -- отправлять. NULL для черновика и для немедленной отправки.
    scheduled_at   timestamptz,
    sent_at        timestamptz,

    created_by     varchar(64) NOT NULL,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT chk_notification_message_type CHECK (
        type IN ('info', 'warning', 'incident', 'maintenance', 'promo')
    ),
    CONSTRAINT chk_notification_message_status CHECK (
        status IN ('draft', 'scheduled', 'sending', 'sent', 'cancelled')
    ),

    -- Инварианты состояния: запланированная рассылка обязана знать своё время,
    -- отправленная — свой факт отправки. Без этих проверок отчётность по
    -- срокам рассылок молча разъедется (тот же приём, что chk_incident_resolution).
    CONSTRAINT chk_notification_message_scheduled CHECK (
        status <> 'scheduled' OR scheduled_at IS NOT NULL
    ),
    CONSTRAINT chk_notification_message_sent CHECK (
        status <> 'sent' OR sent_at IS NOT NULL
    )
);

COMMENT ON TABLE notification_message IS
    'Рассылка: что, кому, по каким каналам и в каком состоянии (NTF-01…06).';

CREATE TABLE notification_target (
    id           uuid PRIMARY KEY,

    -- Здесь каскад уместен, в отличие от alert_code выше: таргет не имеет смысла
    -- в отрыве от своей рассылки и никакой самостоятельной истории не несёт.
    message_id   uuid NOT NULL REFERENCES notification_message (id) ON DELETE CASCADE,

    target_type  varchar(16) NOT NULL,

    -- metro_line.code / metro_station.code / код сегмента / код роли.
    target_code  varchar(64) NOT NULL,

    CONSTRAINT chk_notification_target_type CHECK (
        target_type IN ('line', 'station', 'segment', 'role')
    ),
    CONSTRAINT uq_notification_target UNIQUE (message_id, target_type, target_code)
);

COMMENT ON TABLE notification_target IS
    'Адресация рассылки (NTF-02). ПУСТОЙ набор таргетов = вся сеть — та же '
    'семантика, что у service_alert_target, чтобы операторы не учили два правила.';

CREATE TABLE notification_delivery (
    id            uuid PRIMARY KEY,
    message_id    uuid NOT NULL REFERENCES notification_message (id) ON DELETE CASCADE,
    channel       varchar(16) NOT NULL,

    -- Адрес в терминах канала: email, номер, push-токен, либо код роли/сегмента
    -- для in_app. Хранится как есть — контакт-центр и правила ПДн ещё не заданы
    -- (см. внешние блокеры), поэтому здесь только demo-получатели.
    recipient     varchar(254) NOT NULL,

    status        varchar(16) NOT NULL,

    -- Счётчик попыток для повторной отправки (NTF-06).
    attempts      integer NOT NULL DEFAULT 0,
    last_error    text,
    sent_at       timestamptz,
    delivered_at  timestamptz,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT chk_notification_delivery_channel CHECK (
        channel IN ('in_app', 'push', 'email', 'sms')
    ),
    CONSTRAINT chk_notification_delivery_status CHECK (
        status IN ('pending', 'sent', 'delivered', 'failed')
    ),
    CONSTRAINT uq_notification_delivery_recipient UNIQUE (message_id, channel, recipient),
    CONSTRAINT chk_notification_delivery_attempts CHECK (attempts >= 0),
    CONSTRAINT chk_notification_delivery_timestamps CHECK (
        (status <> 'sent' OR (sent_at IS NOT NULL AND delivered_at IS NULL))
        AND (status <> 'delivered' OR (sent_at IS NOT NULL AND delivered_at IS NOT NULL))
        AND (status <> 'pending' OR (sent_at IS NULL AND delivered_at IS NULL))
        AND (delivered_at IS NULL OR sent_at IS NOT NULL)
        AND (delivered_at IS NULL OR delivered_at >= sent_at)
    ),

    -- Провал обязан быть объяснён: очередь ошибок доставки (OPS-04) без причины
    -- бесполезна — оператор не поймёт, что чинить перед повтором.
    CONSTRAINT chk_notification_delivery_error CHECK (
        status <> 'failed' OR last_error IS NOT NULL
    )
);

COMMENT ON TABLE notification_delivery IS
    'Факт доставки по одному каналу одному получателю (NTF-06): подтверждение и повтор.';

-- Лента рассылок в консоли и публичный in-app-фид: всегда «свежие сверху».
CREATE INDEX ix_notification_message_status_created
    ON notification_message (status, created_at DESC);

-- Планировщик спрашивает ровно одно: «что пора отправлять». Частичный индекс —
-- чтобы не тащить в него отправленные и черновики, которых со временем большинство.
CREATE INDEX ix_notification_message_due
    ON notification_message (scheduled_at)
    WHERE status = 'scheduled';

-- Поиск рассылок по объекту сети (какие уведомления касаются станции X).
CREATE INDEX ix_notification_target_lookup
    ON notification_target (target_type, target_code);

CREATE INDEX ix_notification_target_message
    ON notification_target (message_id);

-- Очередь ошибок доставки (OPS-04): выборка «что не доставлено» + повтор.
CREATE INDEX ix_notification_delivery_failed
    ON notification_delivery (status, updated_at DESC)
    WHERE status IN ('pending', 'failed');

CREATE INDEX ix_notification_delivery_message
    ON notification_delivery (message_id);

-- Отложенная публикация (NTF-05) — за флагом и по умолчанию ВЫКЛЮЧЕНА. Фоновый
-- процесс, который сам рассылает сообщения пассажирам, обязан включаться осознанно,
-- а не появляться вместе с деплоем: цена ошибки здесь — рассылка, которую уже не
-- отозвать. ON CONFLICT — идемпотентность повторного прогона (стиль V015).
INSERT INTO feature_flag (flag_key, enabled, description) VALUES
    ('notification.scheduler', false, 'Enable scheduled delivery of due notifications (NTF-05)')
ON CONFLICT (flag_key) DO NOTHING;
