-- V008: демонстрационные детали станций (выходы и объекты доступности).
-- Данные для трёх существующих демо-станций из V002:
--   ST-L1-01 (Южные ворота), ST-HUB-CENTER (Центр, пересадочный узел), ST-L2-02 (Университет).
-- station_id связывается через подзапрос по стабильному коду (metro_station.code),
-- чтобы не дублировать UUID-литералы V002 и гарантировать совпадение с реальными станциями.
-- Координаты выходов заданы малыми смещениями от точки станции (демо-placeholder).
--
-- ВНИМАНИЕ: данные демонстрационные (placeholder), реальные выходы/объекты доступности
-- будут импортированы через конвейер импорта (ТЗ, раздел 13).

-- ---------------------------------------------------------------------------
-- Выходы станций
-- ---------------------------------------------------------------------------
INSERT INTO station_exit (id, station_id, code, name_i18n, point_geom, is_accessible, sort_order) VALUES
-- ST-L1-01 «Южные ворота»: два выхода, выход A безбарьерный
('33333333-3333-4333-8333-333333330101', (SELECT id FROM metro_station WHERE code = 'ST-L1-01'),
 'EX-ST-L1-01-A', '{"tg":"Баромади A","ru":"Выход A","en":"Exit A"}'::jsonb,
 ST_GeomFromText('POINT(68.8182 38.5212)', 4326), true, 1),
('33333333-3333-4333-8333-333333330102', (SELECT id FROM metro_station WHERE code = 'ST-L1-01'),
 'EX-ST-L1-01-B', '{"tg":"Баромади B","ru":"Выход B","en":"Exit B"}'::jsonb,
 ST_GeomFromText('POINT(68.8178 38.5208)', 4326), false, 2),
-- ST-HUB-CENTER «Центр» (пересадочный): три выхода, все безбарьерные
('33333333-3333-4333-8333-333333330501', (SELECT id FROM metro_station WHERE code = 'ST-HUB-CENTER'),
 'EX-ST-HUB-CENTER-A', '{"tg":"Баромади A","ru":"Выход A","en":"Exit A"}'::jsonb,
 ST_GeomFromText('POINT(68.7862 38.5739)', 4326), true, 1),
('33333333-3333-4333-8333-333333330502', (SELECT id FROM metro_station WHERE code = 'ST-HUB-CENTER'),
 'EX-ST-HUB-CENTER-B', '{"tg":"Баромади B","ru":"Выход B","en":"Exit B"}'::jsonb,
 ST_GeomFromText('POINT(68.7858 38.5735)', 4326), true, 2),
('33333333-3333-4333-8333-333333330503', (SELECT id FROM metro_station WHERE code = 'ST-HUB-CENTER'),
 'EX-ST-HUB-CENTER-C', '{"tg":"Баромади C","ru":"Выход C","en":"Exit C"}'::jsonb,
 ST_GeomFromText('POINT(68.7860 38.5741)', 4326), true, 3),
-- ST-L2-02 «Университет»: два выхода
('33333333-3333-4333-8333-333333331002', (SELECT id FROM metro_station WHERE code = 'ST-L2-02'),
 'EX-ST-L2-02-A', '{"tg":"Баромади A","ru":"Выход A","en":"Exit A"}'::jsonb,
 ST_GeomFromText('POINT(68.7482 38.5662)', 4326), true, 1),
('33333333-3333-4333-8333-333333331003', (SELECT id FROM metro_station WHERE code = 'ST-L2-02'),
 'EX-ST-L2-02-B', '{"tg":"Баромади B","ru":"Выход B","en":"Exit B"}'::jsonb,
 ST_GeomFromText('POINT(68.7478 38.5658)', 4326), false, 2);

-- ---------------------------------------------------------------------------
-- Объекты доступности станций
-- ---------------------------------------------------------------------------
INSERT INTO accessibility_feature (id, station_id, type, description_i18n, status) VALUES
-- ST-L1-01: лифт и тактильная плитка
('44444444-4444-4444-8444-444444440101', (SELECT id FROM metro_station WHERE code = 'ST-L1-01'),
 'elevator', '{"tg":"Лифт то платформа","ru":"Лифт до платформы","en":"Elevator to platform"}'::jsonb, 'available'),
('44444444-4444-4444-8444-444444440102', (SELECT id FROM metro_station WHERE code = 'ST-L1-01'),
 'tactile', '{"tg":"Роҳи ламсӣ","ru":"Тактильная плитка","en":"Tactile paving"}'::jsonb, 'available'),
-- ST-HUB-CENTER: полный набор безбарьерной среды
('44444444-4444-4444-8444-444444440501', (SELECT id FROM metro_station WHERE code = 'ST-HUB-CENTER'),
 'elevator', '{"tg":"Лифт байни сатҳҳо","ru":"Лифт между уровнями","en":"Inter-level elevator"}'::jsonb, 'available'),
('44444444-4444-4444-8444-444444440502', (SELECT id FROM metro_station WHERE code = 'ST-HUB-CENTER'),
 'escalator', '{"tg":"Эскалатор","ru":"Эскалатор","en":"Escalator"}'::jsonb, 'available'),
('44444444-4444-4444-8444-444444440503', (SELECT id FROM metro_station WHERE code = 'ST-HUB-CENTER'),
 'ramp', '{"tg":"Пандус","ru":"Пандус","en":"Ramp"}'::jsonb, 'available'),
('44444444-4444-4444-8444-444444440504', (SELECT id FROM metro_station WHERE code = 'ST-HUB-CENTER'),
 'tactile', '{"tg":"Роҳи ламсӣ","ru":"Тактильная плитка","en":"Tactile paving"}'::jsonb, 'available'),
('44444444-4444-4444-8444-444444440505', (SELECT id FROM metro_station WHERE code = 'ST-HUB-CENTER'),
 'audio_assist', '{"tg":"Эълони овозӣ","ru":"Аудиоинформирование","en":"Audio assistance"}'::jsonb, 'available'),
('44444444-4444-4444-8444-444444440506', (SELECT id FROM metro_station WHERE code = 'ST-HUB-CENTER'),
 'accessible_toilet', '{"tg":"Ҳоҷатхонаи дастрас","ru":"Доступный туалет","en":"Accessible toilet"}'::jsonb, 'available'),
-- ST-L2-02: лифт временно не работает, эскалатор и тактильная плитка в строю
('44444444-4444-4444-8444-444444441001', (SELECT id FROM metro_station WHERE code = 'ST-L2-02'),
 'elevator', '{"tg":"Лифт то платформа","ru":"Лифт до платформы","en":"Elevator to platform"}'::jsonb, 'out_of_service'),
('44444444-4444-4444-8444-444444441002', (SELECT id FROM metro_station WHERE code = 'ST-L2-02'),
 'escalator', '{"tg":"Эскалатор","ru":"Эскалатор","en":"Escalator"}'::jsonb, 'available'),
('44444444-4444-4444-8444-444444441003', (SELECT id FROM metro_station WHERE code = 'ST-L2-02'),
 'tactile', '{"tg":"Роҳи ламсӣ","ru":"Тактильная плитка","en":"Tactile paving"}'::jsonb, 'available');
