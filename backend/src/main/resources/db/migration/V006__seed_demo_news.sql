-- V006: демонстрационные новости модуля контента.
-- Тематика согласована с демо-сетью (V002) и демо-алертами (V004): строительство метро.
-- Даты публикации заданы относительно now(), чтобы демо-данные оставались «живыми»:
--   NEWS-001 (metro-construction-launch) — published, самая новая;
--   NEWS-002 (line-1-tunnel-progress)    — published, старше;
--   NEWS-003 (accessibility-standards)   — published, ещё старше;
--   NEWS-004 (winter-schedule-draft)     — draft, публичным API НЕ отдаётся
--     (в т.ч. проверяет фильтр status=published и гейт полноты языков BR-CMS-1).
--
-- ВНИМАНИЕ: данные являются демонстрационными (placeholder), как и сеть из V002.

-- ---------------------------------------------------------------------------
-- (a) Старт строительства метрополитена — самая свежая публикация
-- ---------------------------------------------------------------------------
INSERT INTO news_article (id, slug, status, title_i18n, body_i18n, cover_media_url, published_at) VALUES
('44444444-4444-4444-8444-444444440001', 'metro-construction-launch', 'published',
 '{"tg":"Оғози сохтмони метрополитени Душанбе","ru":"Старт строительства метрополитена Душанбе","en":"Dushanbe Metro construction begins"}'::jsonb,
 '{"tg":"Дар пойтахти Тоҷикистон корҳои омодагӣ ба сохтмони хатти якуми метрополитен оғоз ёфтанд. Лоиҳа боиси беҳтар шудани ҳаракати нақлиёти ҷамъиятӣ хоҳад шуд.","ru":"В столице Таджикистана начались подготовительные работы к строительству первой линии метрополитена. Проект призван улучшить работу общественного транспорта.","en":"Preparatory works for the first metro line have begun in the capital of Tajikistan. The project aims to improve public transport."}'::jsonb,
 'https://cdn.metro.tj/news/construction-launch.jpg', now() - interval '2 days');

-- ---------------------------------------------------------------------------
-- (b) Ход проходки тоннелей Линии 1
-- ---------------------------------------------------------------------------
INSERT INTO news_article (id, slug, status, title_i18n, body_i18n, cover_media_url, published_at) VALUES
('44444444-4444-4444-8444-444444440002', 'line-1-tunnel-progress', 'published',
 '{"tg":"Пешрафти корҳо дар тоннелҳои Хати 1","ru":"Ход проходки тоннелей Линии 1","en":"Line 1 tunnelling progress"}'::jsonb,
 '{"tg":"Корҳои проходкаи тоннелҳо дар қитъаи марказии Хати 1 тибқи ҷадвал идома доранд. Аввалин истгоҳҳо дар марҳилаи лоиҳакашӣ қарор доранд.","ru":"Работы по проходке тоннелей на центральном участке Линии 1 идут по графику. Первые станции находятся на стадии проектирования.","en":"Tunnelling on the central section of Line 1 is on schedule. The first stations are in the design stage."}'::jsonb,
 NULL, now() - interval '9 days');

-- ---------------------------------------------------------------------------
-- (c) Стандарты доступности будущих станций
-- ---------------------------------------------------------------------------
INSERT INTO news_article (id, slug, status, title_i18n, body_i18n, cover_media_url, published_at) VALUES
('44444444-4444-4444-8444-444444440003', 'accessibility-standards', 'published',
 '{"tg":"Стандартҳои дастрасӣ дар истгоҳҳои оянда","ru":"Стандарты доступности будущих станций","en":"Accessibility standards for future stations"}'::jsonb,
 '{"tg":"Ҳамаи истгоҳҳои метрополитен бо лифтҳо, роҳҳои ҳамвор ва роҳнамоҳои ламсӣ барои шаҳрвандони дорои имкониятҳои маҳдуд муҷаҳҳаз мешаванд.","ru":"Все станции метрополитена будут оснащены лифтами, пандусами и тактильными указателями для маломобильных граждан.","en":"All metro stations will be equipped with lifts, ramps and tactile guides for people with reduced mobility."}'::jsonb,
 NULL, now() - interval '20 days');

-- ---------------------------------------------------------------------------
-- (d) Черновик: не опубликован, публичным API не отдаётся
-- ---------------------------------------------------------------------------
INSERT INTO news_article (id, slug, status, title_i18n, body_i18n, cover_media_url, published_at) VALUES
('44444444-4444-4444-8444-444444440004', 'winter-schedule-draft', 'draft',
 '{"tg":"Ҷадвали зимистонаи корҳои сохтмонӣ","ru":"Зимний график строительных работ","en":"Winter schedule of construction works"}'::jsonb,
 '{"tg":"Лоиҳаи ҷадвали зимистонаи корҳо дар ҳоли омодасозист.","ru":"Проект зимнего графика работ находится в стадии подготовки.","en":"The winter works schedule draft is being prepared."}'::jsonb,
 NULL, NULL);
