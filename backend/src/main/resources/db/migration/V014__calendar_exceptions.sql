CREATE TABLE IF NOT EXISTS calendar_exception (
    id              BIGSERIAL       PRIMARY KEY,
    exception_date  DATE            NOT NULL,
    day_type        VARCHAR(20)     NOT NULL DEFAULT 'holiday'
                        CHECK (day_type IN ('holiday', 'special', 'weekend_override', 'weekday_override')),
    description_tg  TEXT,
    description_ru  TEXT,
    description_en  TEXT,
    is_recurring    BOOLEAN         NOT NULL DEFAULT false,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    UNIQUE (exception_date)
);

COMMENT ON TABLE  calendar_exception IS 'Calendar exceptions for schedule day-type overrides';
COMMENT ON COLUMN calendar_exception.exception_date IS 'The specific date being overridden';
COMMENT ON COLUMN calendar_exception.day_type IS 'Override day type: holiday, special, weekend_override, weekday_override';
COMMENT ON COLUMN calendar_exception.is_recurring IS 'If true, applies every year on this date (e.g., Navruz, Independence Day)';