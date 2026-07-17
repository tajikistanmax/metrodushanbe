# Статус реализации Metro Dushanbe

Дата актуализации: 16 июля 2026 года.

## Итог

Публичный портал, backend и админ-панель готовы как локально запускаемый MVP. Полный
приёмочный контур поднимается через `infra/docker-compose.full.yml` и сохраняет данные в
именованных томах.

«Готовый MVP» здесь означает завершённые пользовательские и операторские сценарии на
демонстрационных данных. Это не означает production-ввод реального метро без официальных
данных, внешних доступов, сертификации и эксплуатационного контура.

## Матрица возможностей

| Область | Public web | Backend | Admin | Статус |
|---|---:|---:|---:|---|
| Линии, станции, GeoJSON | ✅ | ✅ | ✅ | готово |
| Карта и list-mode | ✅ | ✅ API | — | готово |
| Построение маршрута | ✅ | ✅ | — | готово |
| Расписание и arrivals | ✅ | ✅ | ✅ календарь | готово на статическом расписании |
| Service alerts | ✅ | ✅ | ✅ | готово |
| Новости | ✅ | ✅ | ✅ | готово |
| Обращения граждан | ✅ create/track | ✅ SLA/workflow | ✅ queue/edit | готово |
| Тарифы | ✅ | ✅ + Redis | ✅ CRUD | готово на demo-ценах |
| Импорт GeoJSON | — | ✅ | ✅ | готово |
| Feature flags | — | ✅ | ✅ | готово |
| Аудит | — | ✅ | ✅ | готово |
| TG/RU/EN и темы | ✅ | i18n data | ✅ | готово |
| PWA/offline | ✅ | graceful degradation | — | готово |
| AI briefing | — | deterministic local fallback | ✅ | пилотный локальный режим |
| Flutter mobile | — | API готов | — | отдельная фаза |

## Безопасность локального MVP

- admin UI защищён HttpOnly cookie с HMAC-подписью и server-side проверкой;
- admin API защищён `X-Admin-Key`, который не попадает в browser bundle;
- tracking token обращения выдаётся один раз, в БД хранится только SHA-256 hash;
- сравнение tracking secret выполняется constant-time;
- операции admin-контура создают audit events;
- production-секреты не зашиты: compose содержит только явно обозначенные dev defaults.

Локальный пароль `admin / metro2026` предназначен только для разработки.

## Приёмочные доказательства

- backend: Maven/Java 21, `292` теста, `0` failures/errors/skipped;
- Flyway: применяются и валидируются `V001–V018`;
- web/admin: ESLint и Next.js production build проходят;
- Docker: семь сервисов запускаются, backend и зависимости healthy;
- E2E API: health, lines, fares, create/track request, admin update и audit проходят;
- Redis: `fares::SimpleKey []` создаётся и повторно читается без ошибок сериализации;
- браузер: public fares/news/requests и admin fares/requests проверены визуально;
- mobile viewport: форма обращения и горизонтальная навигация проверены;
- console: production hydration mismatch и другие ошибки отсутствуют.

## Известный технический долг

- PMD сообщает архитектурные предупреждения старых классов routing/network (сложность,
  coupling, Law of Demeter); настроенная сборка считает их неблокирующими;
- CPD сообщает одну небольшую дубликацию между alert-сервисами;
- demo seed-данные намеренно остаются в репозитории для автономной демонстрации;
- локальный AI briefing не является подключением production LLM provider.

## Внешние блокеры production

Следующие пункты нельзя корректно «доделать заглушкой» без решений владельца системы:

1. утверждённые трассы, станции, расписания и источник мастер-данных;
2. утверждённые тарифы, льготы, AFC/эквайринг и платёжная сертификация;
3. production OAuth2/OIDC/Keycloak realm, роли и интеграция с гос-ID;
4. realtime feeds поездов, диспетчеризация и операционные SLA;
5. контакт-центр, правила хранения персональных данных и вложения к обращениям;
6. домены, TLS, secrets manager, monitoring/alerting, backups и disaster recovery;
7. Flutter-приложение, store accounts и отдельная мобильная приёмка.

До получения этих входов платформа должна оставаться маркированным demo/local MVP.
