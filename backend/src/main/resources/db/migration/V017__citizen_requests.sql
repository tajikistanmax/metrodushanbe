-- V017: обращения граждан (REQ-01/03/04, ТЗ §6.2.15).
-- Публичное отслеживание использует отдельный случайный токен; в БД хранится
-- только SHA-256 хэш. Контактные данные не попадают в публичные списки.

CREATE TABLE citizen_request (
    id                      UUID PRIMARY KEY,
    public_code             VARCHAR(24)  NOT NULL UNIQUE,
    tracking_token_hash     VARCHAR(64)  NOT NULL,
    request_type            VARCHAR(32)  NOT NULL,
    priority                VARCHAR(16)  NOT NULL,
    status                  VARCHAR(24)  NOT NULL,
    subject                 VARCHAR(200) NOT NULL,
    message                 TEXT         NOT NULL,
    contact_name            VARCHAR(160),
    contact_email           VARCHAR(254),
    contact_phone           VARCHAR(40),
    line_code               VARCHAR(64),
    station_code            VARCHAR(64),
    response_text           TEXT,
    assigned_to             VARCHAR(128),
    sla_response_due_at     TIMESTAMPTZ  NOT NULL,
    sla_resolution_due_at   TIMESTAMPTZ  NOT NULL,
    created_at              TIMESTAMPTZ  NOT NULL,
    updated_at              TIMESTAMPTZ  NOT NULL,
    resolved_at             TIMESTAMPTZ,

    CONSTRAINT ck_citizen_request_type CHECK (
        request_type IN ('complaint', 'suggestion', 'incident', 'question', 'lost_item')
    ),
    CONSTRAINT ck_citizen_request_priority CHECK (
        priority IN ('low', 'normal', 'high', 'critical')
    ),
    CONSTRAINT ck_citizen_request_status CHECK (
        status IN ('new', 'in_progress', 'awaiting_info', 'resolved', 'closed', 'reopened')
    )
);

CREATE INDEX ix_citizen_request_status_created
    ON citizen_request (status, created_at DESC);
CREATE INDEX ix_citizen_request_sla_response
    ON citizen_request (sla_response_due_at) WHERE status = 'new';
CREATE INDEX ix_citizen_request_sla_resolution
    ON citizen_request (sla_resolution_due_at)
    WHERE status NOT IN ('resolved', 'closed');
