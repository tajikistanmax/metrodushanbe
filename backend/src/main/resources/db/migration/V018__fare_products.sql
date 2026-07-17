-- MVP-справочник тарифов. Значения сидов демонстрационные и не являются
-- утверждёнными тарифами метро Душанбе.
CREATE TABLE fare_product (
    id               uuid PRIMARY KEY,
    code             varchar(64) NOT NULL UNIQUE,
    name_i18n        jsonb NOT NULL,
    description_i18n jsonb NOT NULL,
    amount           numeric(10, 2) NOT NULL CHECK (amount >= 0),
    currency         varchar(3) NOT NULL DEFAULT 'TJS',
    rider_category   varchar(24) NOT NULL,
    validity_minutes integer NULL CHECK (validity_minutes IS NULL OR validity_minutes > 0),
    is_active        boolean NOT NULL DEFAULT false,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_fare_category CHECK (
        rider_category IN ('all', 'adult', 'child', 'student', 'senior')
    )
);

CREATE INDEX ix_fare_product_public
    ON fare_product (is_active, amount, code);

INSERT INTO fare_product (
    id, code, name_i18n, description_i18n, amount, currency,
    rider_category, validity_minutes, is_active
) VALUES
(
    'f0000000-0000-0000-0000-000000000001',
    'DEMO-SINGLE',
    '{"tg":"Сафари яккарата (намоишӣ)","ru":"Разовая поездка (демо)","en":"Single ride (demo)"}',
    '{"tg":"Нархи намунавӣ то тасдиқи тарофаи расмӣ.","ru":"Демонстрационная цена до утверждения официального тарифа.","en":"Illustrative price pending approval of the official fare."}',
    3.00, 'TJS', 'all', 90, true
),
(
    'f0000000-0000-0000-0000-000000000002',
    'DEMO-MONTHLY',
    '{"tg":"Роҳхати моҳона (намоишӣ)","ru":"Месячный проездной (демо)","en":"Monthly pass (demo)"}',
    '{"tg":"Маҳсулоти намунавӣ; шартҳо баъд аз тасдиқ нав мешаванд.","ru":"Демонстрационный продукт; условия будут обновлены после утверждения.","en":"Illustrative product; terms will be updated after approval."}',
    50.00, 'TJS', 'all', 43200, true
);
