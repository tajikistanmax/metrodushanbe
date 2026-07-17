-- Билеты, платежи, возвраты и антифрод (ТЗ TKT-03…06, U-CIT-08).
--
-- ============================ ДЕМО-КОНТУР ============================
-- Реального эквайринга, платёжной сертификации (PCI DSS) и фискализации в
-- проекте НЕТ — см. «Внешние блокеры» в docs/implementation-status.md. Отсюда
-- три ограничения, заложенные прямо в схему:
--
--   1. Карточных данных здесь нет и быть не может. Ни PAN, ни CVV, ни срока
--      действия, ни имени держателя, ни маски карты, ни платёжного токена
--      эмитента. Приём карточных данных без сертификации PCI DSS недопустим
--      даже в demo, поэтому колонок под них не предусмотрено намеренно:
--      отсутствующую колонку нельзя заполнить «по ошибке».
--   2. Каждая запись несёт признак is_demo. Платёж, которого не было, и билет,
--      за который не заплачено, обязаны быть отличимы от настоящих — иначе при
--      подключении боевого провайдера demo-строки станут неотличимы от
--      реальных, и сверка с банком не сойдётся.
--   3. payment.provider — 'demo'. Значение CHECK-а расширяется той же миграцией,
--      которая подключит реального провайдера; до тех пор любой иной провайдер
--      в БД — ошибка.
-- =====================================================================
--
-- Отличие от fare_product (V018): fare_product — это ПРАЙС-ЛИСТ, изменяемый
-- справочник «сколько стоит проездной сегодня». Ticket — ВЫПУЩЕННЫЙ документ,
-- у которого цена уже зафиксирована в момент покупки. Поэтому здесь дублируются
-- price_amount/price_currency/rider_category: они обязаны остаться такими, какими
-- были при оплате, даже если тариф завтра подорожает или его снимут с продажи.

CREATE TABLE ticket (
    id                uuid PRIMARY KEY,
    code              varchar(64) NOT NULL UNIQUE,

    -- Стабильный fare_product.code. FK намеренно НЕТ — по той же причине, что у
    -- incident.line_code: тариф снимают с продажи и удаляют, а выпущенный билет
    -- обязан пережить это. Цена уже зафиксирована ниже и от справочника больше
    -- не зависит, так что «висячая» ссылка ничего не ломает.
    fare_product_code varchar(64) NOT NULL,

    -- single | pass. Признак выводится при покупке из validity_minutes тарифа:
    -- в fare_product нет колонки типа продукта, а поведение валидации у разовой
    -- поездки и проездного разное (разовый гасится, проездной — нет). Хранится
    -- на билете, а не вычисляется каждый раз, чтобы правило вывода можно было
    -- поменять, не переписав уже выпущенные билеты.
    kind              varchar(16) NOT NULL,

    -- Копия fare_product.rider_category на момент покупки. Льгота в demo НЕ
    -- верифицируется: реестра льготников нет (внешний блокер).
    rider_category    varchar(24) NOT NULL,

    -- Идентификатор покупателя для антифрода и чёрного списка. В demo это
    -- непроверенный идентификатор приложения/устройства, НЕ персональные данные
    -- и НЕ учётная запись: контура идентификации пассажиров ещё нет. В проде
    -- сюда придёт subject из JWT.
    rider_ref         varchar(64),

    status            varchar(16) NOT NULL,

    -- TKT-04. Хранится ТОЛЬКО SHA-256 (hex, 64 символа) от токена QR — сам токен
    -- показывается покупателю один раз в ответе на purchase и в БД не попадает.
    -- Тот же приём, что citizen_request.tracking_token_hash: утечка дампа не
    -- должна давать возможность предъявить чужой билет на турникете.
    token_hash        varchar(64) NOT NULL UNIQUE,

    valid_from        timestamptz NOT NULL,
    valid_until       timestamptz NOT NULL,

    -- Зафиксированная цена покупки. См. шапку: за справочником не едет.
    price_amount      numeric(10, 2) NOT NULL CHECK (price_amount >= 0),
    price_currency    varchar(3) NOT NULL,

    -- Сумма, внесённая на проездной пополнениями, нарастающим итогом (U-CIT-08).
    -- Это УЧЁТНАЯ величина, а не расходуемый кошелёк: списаний с неё нет.
    -- Проездной времязависимый — пополнение продлевает valid_until, а поездки
    -- баланс не уменьшают. Потарифная тарификация по остатку не утверждена
    -- (внешний блокер); заводить здесь списания значило бы закрепить в схеме
    -- ещё не принятое правило.
    -- Валюта — price_currency: мультивалютного кошелька в MVP нет, и вторая
    -- колонка валюты допустила бы пополнение в валюте, отличной от цены билета.
    balance_amount    numeric(10, 2) NOT NULL DEFAULT 0 CHECK (balance_amount >= 0),

    used_at           timestamptz,
    validation_count  integer NOT NULL DEFAULT 0 CHECK (validation_count >= 0),
    last_validated_at timestamptz,

    -- См. шапку, п.2. DEFAULT true, а не false: пока боевого эквайринга нет,
    -- «не-демо» билет — это ошибка, и безопасный дефолт должен быть строгим.
    is_demo           boolean NOT NULL DEFAULT true,

    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT chk_ticket_kind CHECK (kind IN ('single', 'pass')),
    CONSTRAINT chk_ticket_rider_category CHECK (
        rider_category IN ('all', 'adult', 'child', 'student', 'senior')
    ),
    CONSTRAINT chk_ticket_status CHECK (
        status IN ('issued', 'active', 'used', 'expired', 'refunded', 'blocked')
    ),
    -- Погашенный билет обязан знать, когда его погасили: без отметки разбор спора
    -- «я не проходил» невозможен, а used без времени неотличим от порчи данных.
    CONSTRAINT chk_ticket_used_at CHECK (status <> 'used' OR used_at IS NOT NULL),
    -- Окно действия должно быть окном, а не точкой: билет с valid_until <=
    -- valid_from невалиден с рождения, такую запись нельзя создавать молча.
    CONSTRAINT chk_ticket_validity_window CHECK (valid_until > valid_from)
);

COMMENT ON TABLE ticket IS
    'Выпущенный билет (TKT-04). ДЕМО: билет не даёт права проезда — платежа за ним не было.';

CREATE TABLE payment (
    id             uuid PRIMARY KEY,
    code           varchar(64) NOT NULL UNIQUE,

    -- Стабильный ticket.code. NULL допустим и означает ровно одно: платёж
    -- отклонён, билет не выпускался. Мы обязаны сохранить отклонённую попытку
    -- (она нужна антифроду TKT-06 и разбору жалоб), но не имеем права выпускать
    -- под неё билет. FK нет по той же причине, что и у ticket.fare_product_code:
    -- финансовая история не удаляется каскадом.
    ticket_code    varchar(64),

    -- purchase | topup. Возврату (TKT-03) подлежит покупка; пополнение проездного
    -- в demo не возвращается — правил перерасчёта остатка ещё нет.
    kind           varchar(16) NOT NULL,

    amount         numeric(10, 2) NOT NULL CHECK (amount > 0),
    currency       varchar(3) NOT NULL,
    status         varchar(16) NOT NULL,

    -- См. шапку, п.3.
    provider       varchar(24) NOT NULL DEFAULT 'demo',

    -- Идентификатор операции на стороне провайдера. Для demo — синтетический.
    provider_ref   varchar(128),

    failure_reason text,
    is_demo        boolean NOT NULL DEFAULT true,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT chk_payment_kind CHECK (kind IN ('purchase', 'topup')),
    CONSTRAINT chk_payment_status CHECK (
        status IN ('pending', 'authorized', 'captured', 'failed', 'refunded')
    ),
    -- Пока провайдер один. Расширяется миграцией, подключающей боевой эквайринг.
    CONSTRAINT chk_payment_provider CHECK (provider IN ('demo')),

    -- Провал обязан быть объяснён — тот же инвариант и та же причина, что у
    -- chk_notification_delivery_error: отказ без причины бесполезен и для
    -- пассажира («почему не прошло?»), и для разбора с провайдером.
    CONSTRAINT chk_payment_error CHECK (
        status <> 'failed' OR failure_reason IS NOT NULL
    ),
    -- Зеркальный инвариант: успешный платёж обязан быть привязан к билету и
    -- нести ссылку провайдера, иначе деньги списаны «в никуда» и сверка не
    -- сойдётся.
    CONSTRAINT chk_payment_settled CHECK (
        status NOT IN ('captured', 'refunded')
        OR (ticket_code IS NOT NULL AND provider_ref IS NOT NULL)
    )
);

COMMENT ON TABLE payment IS
    'Платёж за билет/пополнение (TKT-05). ДЕМО: денег не списывалось, PAN/CVV не хранятся и не принимаются.';

CREATE TABLE refund (
    id             uuid PRIMARY KEY,
    code           varchar(64) NOT NULL UNIQUE,

    -- Стабильный payment.code. Без FK — см. выше.
    payment_code   varchar(64) NOT NULL,

    amount         numeric(10, 2) NOT NULL CHECK (amount > 0),
    currency       varchar(3) NOT NULL,
    status         varchar(16) NOT NULL,

    -- Основание возврата. Обязательно: возврат без причины неразбираем.
    reason         text NOT NULL,

    provider_ref   varchar(128),
    failure_reason text,

    -- Кто инициировал: 'public' для возврата пассажиром из приложения, логин
    -- оператора — для ручного возврата из консоли (TKT-03).
    created_by     varchar(64) NOT NULL,

    is_demo        boolean NOT NULL DEFAULT true,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT chk_refund_status CHECK (status IN ('pending', 'completed', 'failed')),
    CONSTRAINT chk_refund_error CHECK (
        status <> 'failed' OR failure_reason IS NOT NULL
    ),
    CONSTRAINT chk_refund_provider_ref CHECK (
        status <> 'completed' OR provider_ref IS NOT NULL
    )
);

COMMENT ON TABLE refund IS
    'Возврат платежа (TKT-03). ДЕМО: реального перевода средств не происходит.';

CREATE TABLE ticket_blocklist (
    id           uuid PRIMARY KEY,
    code         varchar(64) NOT NULL UNIQUE,

    -- ticket | token | rider — что именно заблокировано.
    subject_type varchar(16) NOT NULL,

    -- Значение зависит от subject_type: ticket.code, SHA-256(токен) либо
    -- ticket.rider_ref. Для 'token' здесь ХЕШ, а не токен: сам токен в системе
    -- не хранится нигде (см. ticket.token_hash), и чёрный список не имеет права
    -- становиться единственным местом, где он лежит открытым.
    subject_code varchar(64) NOT NULL,

    reason       text NOT NULL,
    created_by   varchar(64) NOT NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT chk_ticket_blocklist_subject_type CHECK (
        subject_type IN ('ticket', 'token', 'rider')
    ),
    -- Один субъект — одна запись. Дубли превратили бы снятие блокировки в
    -- «снял одну, а вторая всё ещё держит» — классический источник жалоб.
    CONSTRAINT uq_ticket_blocklist_subject UNIQUE (subject_type, subject_code)
);

COMMENT ON TABLE ticket_blocklist IS
    'Чёрный список билетов/токенов/покупателей (TKT-06). Проверяется при каждой валидации и покупке.';

-- Горячий путь турникета (TKT-04): валидация ищет билет строго по хешу токена.
-- Отдельный индекс не нужен — UNIQUE на token_hash уже даёт его.

-- Антифрод (TKT-06): «сколько билетов купил этот покупатель за последний час».
-- Частичный — билеты без rider_ref в правило не входят и индекс не засоряют.
CREATE INDEX ix_ticket_rider_recent
    ON ticket (rider_ref, created_at DESC)
    WHERE rider_ref IS NOT NULL;

-- Консоль оператора: лента билетов с фильтром по статусу, свежие сверху.
CREATE INDEX ix_ticket_status_created ON ticket (status, created_at DESC);

-- Фоновое истечение срока и отчёт «действующие проездные»: выбирать только то,
-- что ещё может истечь, — терминальные статусы в индекс не тащим.
CREATE INDEX ix_ticket_expiry
    ON ticket (valid_until)
    WHERE status IN ('issued', 'active');

-- Карточка билета в консоли: все платежи по билету (покупка + пополнения).
CREATE INDEX ix_payment_ticket ON payment (ticket_code) WHERE ticket_code IS NOT NULL;

-- Финансовая лента консоли и разбор отказов эквайринга (TKT-05).
CREATE INDEX ix_payment_status_created ON payment (status, created_at DESC);

-- Возвраты по платежу: проверка «этот платёж уже возвращали» перед новым возвратом.
CREATE INDEX ix_refund_payment ON refund (payment_code);

-- Current product supports one full refund per captured purchase. Failed attempts remain
-- in history, while a second pending/completed refund for the same payment is impossible.
CREATE UNIQUE INDEX uq_refund_active_payment ON refund (payment_code)
    WHERE status IN ('pending', 'completed');

-- Лента возвратов в консоли (TKT-03), свежие сверху.
CREATE INDEX ix_refund_status_created ON refund (status, created_at DESC);

-- Чёрный список в консоли: свежие сверху. Поиск по субъекту идёт через
-- uq_ticket_blocklist_subject, отдельного индекса не требует.
CREATE INDEX ix_ticket_blocklist_created ON ticket_blocklist (created_at DESC);

-- Флаги билетного контура (TKT-03). Заводятся явно, чтобы их было видно и можно
-- было выключить из консоли: код читает их с defaultValue=true, то есть без этих
-- строк контур молча работал бы, а рубильника в UI не было бы вовсе.
--
-- Значение true здесь безопасно ровно потому, что платёж имитируется
-- DemoPaymentGateway и каждый билет помечен как демонстрационный. При включении
-- реального эквайринга оба флага обязаны быть выключены до прохождения
-- платёжной сертификации.
INSERT INTO feature_flag (flag_key, enabled, description) VALUES
    ('ticketing.purchase', true, 'Enable demo ticket purchase and top-up (TKT-02, U-CIT-08)'),
    ('ticketing.refund', true, 'Enable demo ticket refunds (TKT-03)')
ON CONFLICT (flag_key) DO NOTHING;
