-- Модуль интеграций (INT-02/03/05, U-INT-03/04, U-OPS-04, ADM-06).
--
-- Задача модуля: городской портал и другие внешние подписчики должны узнавать о
-- событиях сети, а GPS/диспетчерская платформа — публиковать положение поездов.
--
-- ПОЧЕМУ OUTBOX, А НЕ ПРЯМОЙ HTTP-ВЫЗОВ ИЗ БИЗНЕС-КОДА (INT-03).
-- Если слать вебхук прямо в момент изменения, возможны две одинаково плохие
-- развязки: (а) HTTP прошёл, а транзакция откатилась — подписчик узнал о
-- событии, которого не было; (б) HTTP не прошёл — и мы либо теряем событие,
-- либо держим транзакцию открытой на время сетевого таймаута. Поэтому событие
-- пишется в outbox_event В ТОЙ ЖЕ ТРАНЗАКЦИИ, что и бизнес-изменение (либо оба,
-- либо ни одного), а доставку отдельно и асинхронно выполняет WebhookDispatcher.
--
-- ПОЧЕМУ DLQ — ЭТО СТАТУС 'dead', А НЕ ОТДЕЛЬНАЯ ТАБЛИЦА (INT-05).
-- Отдельная таблица dead_letter означала бы, что «умершая» доставка физически
-- переезжает из webhook_delivery. Тогда: (1) история попыток разрывается — код
-- доставки, счётчик attempts и last_error нужно дублировать, и два источника
-- истины неизбежно разъедутся; (2) ручной повтор (U-OPS-04) превращается в
-- перенос строки обратно, то есть в ещё одну операцию, способную упасть на
-- полпути; (3) операторская очередь ошибок вынуждена читать UNION двух таблиц,
-- и любой новый фильтр надо писать дважды. DLQ по своей сути — не другое
-- хранилище, а КОНЕЧНОЕ СОСТОЯНИЕ той же доставки: «retry исчерпан, дальше
-- только руками». Состояние и выражается статусом. Выборка очереди ошибок —
-- это WHERE status IN ('failed','dead'), а возврат в работу — смена статуса на
-- 'pending', без перемещения данных. Отдельная таблица оправдана, только когда
-- у DLQ другой жизненный цикл хранения (например, ретенция в годы против
-- недель) — здесь этого нет: и живые, и мёртвые доставки чистятся одинаково.

-- Подписчик вебхуков (INT-02, U-INT-03, ADM-06).
CREATE TABLE webhook_subscription (
    id                    uuid PRIMARY KEY,
    code                  varchar(64) NOT NULL UNIQUE,
    name                  varchar(200) NOT NULL,
    target_url            varchar(2048) NOT NULL,

    -- Секрет подписчика в открытом виде НЕ хранится — только SHA-256, тем же
    -- приёмом, что citizen_request.tracking_token_hash: дамп БД, бэкап или
    -- случайный лог не должны отдавать пригодный к повторному использованию
    -- секрет. Показывается он ровно один раз — при создании и при ротации.
    -- Подписью тела служит HMAC на этом же хеше как на производном ключе
    -- (см. WebhookSignature): подписчик знает исходный секрет и выводит ключ у
    -- себя. Это защищает от утечки секрета наружу, но не от компрометации самой
    -- БД — прод обязан хранить ключ в KMS/vault (внешний блокер).
    secret_hash           varchar(64) NOT NULL,

    -- Массив кодов событий, напр. ["alert_published","incident_resolved"].
    -- По тем же соображениям, что notification_template.channels: набор короткий,
    -- читается всегда целиком вместе с подпиской, и CHECK по элементам jsonb
    -- нечитаем и не переживёт добавления типа события. Состав проверяет сервис
    -- по WebhookEventType.codes().
    event_types           jsonb NOT NULL,

    active                boolean NOT NULL DEFAULT true,

    -- Лимит доставок в минуту (ADM-06): защищает не нас, а подписчика —
    -- городской портал не обязан переваривать всплеск событий при массовом сбое.
    rate_limit_per_minute integer NOT NULL DEFAULT 60,

    created_at            timestamptz NOT NULL DEFAULT now(),
    updated_at            timestamptz NOT NULL DEFAULT now(),
    created_by            varchar(64) NOT NULL,

    CONSTRAINT chk_webhook_subscription_rate_limit CHECK (rate_limit_per_minute > 0),
    CONSTRAINT chk_webhook_subscription_url CHECK (target_url LIKE 'https://%')
);

COMMENT ON TABLE webhook_subscription IS
    'Внешний подписчик событий сети (INT-02, U-INT-03). Секрет — только хеш.';

-- Фан-аут при публикации события читает ровно активные подписки.
CREATE INDEX ix_webhook_subscription_active ON webhook_subscription (active) WHERE active;

-- Событие для доставки (INT-03).
CREATE TABLE outbox_event (
    id             uuid PRIMARY KEY,

    -- Идемпотентность НА СТОРОНЕ ПОДПИСЧИКА: уходит в заголовке X-Metro-Event-Id
    -- и не меняется между повторами. Подписчик, увидев знакомый event_id,
    -- обязан отбросить дубль — при retry мы неизбежно доставляем «как минимум
    -- один раз», а не «ровно один раз», и это единственный способ свести их.
    event_id       uuid NOT NULL UNIQUE,

    event_type     varchar(48) NOT NULL,
    payload        jsonb NOT NULL,

    -- Что именно изменилось: тип и стабильный код агрегата (alert/ALERT-1).
    -- FK нет намеренно — как в incident.line_code: событие обязано пережить
    -- удаление или переименование породившей его сущности, история не каскадится.
    aggregate_type varchar(32) NOT NULL,
    aggregate_code varchar(64) NOT NULL,

    occurred_at    timestamptz NOT NULL,

    -- Сквозная трассировка (INT-05): тот же requestId, что проставляет
    -- RequestIdFilter в MDC и возвращает в заголовке X-Request-Id. Позволяет
    -- пройти цепочку «запрос оператора → событие → доставка → лог подписчика»
    -- по одному значению. Nullable: событие может родиться в планировщике, вне
    -- HTTP-запроса — тогда трасса генерируется на месте.
    trace_id       varchar(64),

    created_at     timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT chk_outbox_event_type CHECK (
        event_type IN ('alert_published', 'alert_cleared', 'incident_opened',
                       'incident_resolved', 'station_status_changed',
                       'schedule_changed', 'train_delayed')
    )
);

COMMENT ON TABLE outbox_event IS
    'Событие сети, записанное в одной транзакции с бизнес-изменением (INT-03).';

-- Разбор инцидента «что мы отправляли по этому объекту» и хронология ленты.
CREATE INDEX ix_outbox_event_aggregate ON outbox_event (aggregate_type, aggregate_code, occurred_at DESC);
-- Поиск по трассе при разборе жалобы «портал не получил событие» (INT-05).
CREATE INDEX ix_outbox_event_trace ON outbox_event (trace_id) WHERE trace_id IS NOT NULL;

-- Попытка доставки конкретного события конкретному подписчику (INT-05).
CREATE TABLE webhook_delivery (
    id                uuid PRIMARY KEY,
    code              varchar(64) NOT NULL UNIQUE,

    -- outbox_event.event_id, а не outbox_event.id: наружу в заголовке уходит
    -- именно он, и при разборе оператор ищет по тому значению, которое видел
    -- подписчик. FK нет — ретенция outbox и доставок может расходиться, и
    -- чистка старых событий не должна уносить историю ошибок.
    event_id          uuid NOT NULL,
    subscription_code varchar(64) NOT NULL,
    event_type_snapshot varchar(48) NOT NULL,
    aggregate_type_snapshot varchar(32) NOT NULL,
    aggregate_code_snapshot varchar(64) NOT NULL,
    trace_id_snapshot varchar(64),
    body_snapshot     text NOT NULL,

    status            varchar(16) NOT NULL,
    attempts          integer NOT NULL DEFAULT 0,
    claim_token       uuid,
    claim_until       timestamptz,
    version           bigint NOT NULL DEFAULT 0,

    -- Когда доставку следует попробовать (снова). Для pending — сразу, для
    -- failed — момент, рассчитанный экспоненциальной задержкой.
    next_attempt_at   timestamptz,

    last_error        text,
    response_status   integer,

    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT chk_webhook_delivery_status CHECK (
        status IN ('pending', 'processing', 'sent', 'failed', 'dead')
    ),
    CONSTRAINT chk_webhook_delivery_attempts CHECK (attempts >= 0),
    CONSTRAINT chk_webhook_delivery_body CHECK (length(body_snapshot) > 0),
    CONSTRAINT chk_webhook_delivery_claim CHECK (
        (status = 'processing') = (claim_token IS NOT NULL AND claim_until IS NOT NULL)
    ),
    CONSTRAINT chk_webhook_delivery_schedule CHECK (
        (status IN ('pending', 'failed') AND next_attempt_at IS NOT NULL)
        OR (status = 'processing' AND next_attempt_at IS NULL)
        OR (status IN ('sent', 'dead') AND next_attempt_at IS NULL)
    ),
    -- Провалившаяся доставка обязана нести причину: очередь ошибок U-OPS-04 без
    -- причины бесполезна — оператор не поймёт, повторять или чинить подписчика.
    CONSTRAINT chk_webhook_delivery_error CHECK (
        status NOT IN ('failed', 'dead') OR last_error IS NOT NULL
    ),
    CONSTRAINT chk_webhook_delivery_response_status CHECK (
        response_status IS NULL OR response_status BETWEEN 100 AND 599
    ),
    CONSTRAINT chk_webhook_delivery_sent_response CHECK (
        status <> 'sent' OR response_status BETWEEN 200 AND 299
    ),
    -- Один и тот же подписчик не должен получить одно событие двумя строками:
    -- это дало бы двойную доставку уже на нашей стороне, до всякого retry.
    CONSTRAINT uq_webhook_delivery_event_subscription UNIQUE (event_id, subscription_code)
);

COMMENT ON TABLE webhook_delivery IS
    'Доставка события подписчику. Статус dead = DLQ: retry исчерпан (INT-05).';

-- Главный запрос диспетчера «что пора доставлять»: частичный индекс покрывает
-- ровно живые доставки. Полный индекс по (status, next_attempt_at) со временем
-- распух бы на sent-строках, которых будет 99% таблицы и которые диспетчер не
-- читает никогда.
CREATE INDEX ix_webhook_delivery_due ON webhook_delivery (next_attempt_at)
    WHERE status IN ('pending', 'failed');

-- Упавший worker не оставляет строку навечно: другой экземпляр заберёт её после lease.
CREATE INDEX ix_webhook_delivery_expired_claim ON webhook_delivery (claim_until)
    WHERE status = 'processing';

-- Очередь ошибок оператора (U-OPS-04): свежие сверху.
CREATE INDEX ix_webhook_delivery_failures ON webhook_delivery (status, updated_at DESC)
    WHERE status IN ('failed', 'dead');

-- Учёт лимита rate_limit_per_minute и карточка подписчика в консоли (ADM-06).
CREATE INDEX ix_webhook_delivery_subscription ON webhook_delivery (subscription_code, updated_at DESC);

-- Realtime-телеметрия положения поездов (U-INT-04).
--
-- Стабильного code здесь нет намеренно: это не справочник, а поток замеров.
-- Строка адресуется парой (train_code, reported_at), никакой REST-путь на
-- отдельный замер не ходит — читается всегда срез «где поезда сейчас».
CREATE TABLE train_position (
    id                uuid PRIMARY KEY,
    train_code        varchar(64) NOT NULL,
    line_code         varchar(64) NOT NULL,

    -- Где поезд был последний раз и куда идёт. FK нет — те же соображения, что
    -- в incident: замер обязан пережить правку справочника сети.
    station_code      varchar(64),
    next_station_code varchar(64),

    -- Геометрия — как в metro_station.point_geom: geometry(Point, 4326) вместо
    -- пары double. Так карта строится тем же кодом, что и вся сеть, и
    -- пространственные запросы («какие поезда в радиусе от станции») не требуют
    -- пересчёта на лету.
    geom              geometry(Point, 4326) NOT NULL,

    heading           integer,
    speed_kmh         numeric(5, 1),
    delay_seconds     integer,
    occupancy         varchar(16),

    -- reported_at — время замера по часам GPS-платформы, received_at — когда
    -- его приняли мы. Разделены осознанно: расхождение этих двух отметок и есть
    -- лаг телеметрии, по нему определяют, что платформа отстала или зависла.
    -- Слить их в одну колонку — значит потерять единственный признак деградации.
    reported_at       timestamptz NOT NULL,
    received_at       timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT chk_train_position_heading CHECK (heading IS NULL OR (heading >= 0 AND heading < 360)),
    CONSTRAINT chk_train_position_speed CHECK (speed_kmh IS NULL OR speed_kmh >= 0),
    CONSTRAINT chk_train_position_occupancy CHECK (
        occupancy IS NULL OR occupancy IN ('low', 'medium', 'high', 'full')
    ),
    -- Платформа не должна дважды прислать один и тот же замер: при повторе
    -- пакета (сеть моргнула) это идемпотентность приёма, а не новая точка.
    CONSTRAINT uq_train_position_train_reported UNIQUE (train_code, reported_at)
);

COMMENT ON TABLE train_position IS
    'Замер положения поезда от GPS/диспетчерской платформы (U-INT-04).';

-- Основная выборка U-INT-04: «где поезда линии сейчас» — свежий замер по поезду.
CREATE INDEX ix_train_position_line_reported ON train_position (line_code, train_code, reported_at DESC);

-- Пространственные запросы по срезу — тем же приёмом, что ix_metro_station_point_geom.
CREATE INDEX ix_train_position_geom ON train_position USING GIST (geom);

-- Флаг модуля (ADM-06). По умолчанию ВЫКЛЮЧЕН: пока подписчики не заведены и
-- их URL не проверены, диспетчер не должен ходить наружу — в том числе из
-- интеграционных тестов и с машин разработчиков.
INSERT INTO feature_flag (flag_key, enabled, description) VALUES
    ('integration.webhooks', false, 'Enable outbound webhook delivery to external subscribers')
ON CONFLICT (flag_key) DO NOTHING;
