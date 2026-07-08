CREATE TABLE IF NOT EXISTS feature_flag (
    flag_key    VARCHAR(100)    PRIMARY KEY,
    enabled     BOOLEAN         NOT NULL DEFAULT false,
    description VARCHAR(500),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_by  VARCHAR(100)
);

COMMENT ON TABLE  feature_flag IS 'Feature flags for phased rollout without redeploy';
COMMENT ON COLUMN feature_flag.flag_key IS 'Unique flag identifier (e.g., import.async, routing.alternative, schedule.realtime)';
COMMENT ON COLUMN feature_flag.enabled IS 'Whether the feature is currently enabled';

INSERT INTO feature_flag (flag_key, enabled, description) VALUES
    ('import.async',              false, 'Enable async background import jobs'),
    ('routing.alternative',       false, 'Enable alternative route suggestions'),
    ('schedule.realtime',         false, 'Enable real-time arrival predictions'),
    ('schedule.holiday',          true,  'Enable calendar exception / holiday handling'),
    ('admin.audit',               true,  'Enable audit event tracking for admin actions'),
    ('ai.chat',                   true,  'Enable AI agent chat endpoint'),
    ('admin.crud',                true,  'Enable admin CRUD write operations'),
    ('offline.mode',              false, 'Enable full offline mode support');
