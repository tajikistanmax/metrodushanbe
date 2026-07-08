-- Calendar exceptions for Tajikistan public holidays 2026
INSERT INTO calendar_exception (exception_date, day_type, description_tg, description_ru, description_en, is_recurring)
VALUES
    ('2026-01-01', 'holiday', 'Соли нав', 'Новый год', 'New Year', true),
    ('2026-03-08', 'holiday', 'Рӯзи занон', 'Международный женский день', 'International Women''s Day', true),
    ('2026-03-21', 'holiday', 'Наврӯз', 'Навруз', 'Navruz', true),
    ('2026-03-22', 'holiday', 'Наврӯз', 'Навруз', 'Navruz', true),
    ('2026-05-01', 'holiday', 'Рӯзи байналмилалии меҳнаткашон', 'Праздник труда', 'Labour Day', true),
    ('2026-05-09', 'holiday', 'Рӯзи Ғалаба', 'День Победы', 'Victory Day', true),
    ('2026-06-27', 'holiday', 'Рӯзи ваҳдати миллӣ', 'День национального единства', 'National Unity Day', true),
    ('2026-09-09', 'holiday', 'Рӯзи истиқлолият', 'День независимости', 'Independence Day', true),
    ('2026-11-06', 'holiday', 'Рӯзи Конститутсия', 'День Конституции', 'Constitution Day', true),
    ('2026-11-24', 'holiday', 'Рӯзи Президент', 'День Президента', 'President Day', true)
ON CONFLICT (exception_date) DO NOTHING;
