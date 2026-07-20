# Метро Душанбе — локально готовый MVP

Монорепозиторий цифровой платформы «Метро Душанбе»: публичный PWA-портал, backend API,
операционная админ-панель и локальный приёмочный Docker-контур.

- Техническое задание: [docs/metrodushanbe-v2.md](docs/metrodushanbe-v2.md)
- Конвенции: [docs/dev-conventions.md](docs/dev-conventions.md)
- Фактическая готовность и границы MVP: [docs/implementation-status.md](docs/implementation-status.md)
- Аудит референсов `photo` и сценарий демонстрации: [docs/reference-audit-2026-07-16.md](docs/reference-audit-2026-07-16.md)
- Международный benchmark и решения нового интерфейса: [docs/international-metro-benchmark-2026-07-17.md](docs/international-metro-benchmark-2026-07-17.md)

## Статус

| Компонент | Стек | Состояние |
|---|---|---|
| `backend/` | Java 21, Spring Boot, PostgreSQL/PostGIS, Redis, Flyway | ✅ локальный MVP |
| `web/` | Next.js, TypeScript, Tailwind, MapLibre, PWA | ✅ локальный MVP |
| `admin/` | Next.js, TypeScript | ✅ локальный MVP |
| `infra/` | Docker Compose, PostGIS, Redis, MinIO, Martin | ✅ приёмочный контур |
| `data/` | GeoJSON и seed-данные | ✅ демонстрационные данные |
| `mobile/` | Flutter | ⏳ отдельная продуктовая фаза |

> Данные линий, станций и цены с пометкой «демо» не являются утверждёнными данными
> метрополитена. Перед production-запуском их нужно заменить официальным импортом и
> утверждёнными тарифами.

## Что входит в MVP

Публичный портал:

- карта сети и доступный list-mode;
- построение маршрута, карточки станций, ближайшие отправления и расписание;
- новости и сервисные уведомления;
- обращения граждан: создание, секретный tracking token и просмотр статуса;
- тарифы и проездные с явной маркировкой демонстрационных цен;
- TG/RU/EN, светлая/тёмная тема, адаптивность, PWA и offline fallback.

Админ-панель:

- защищённый вход и серверная проверка сессии;
- линии, станции, уведомления, новости, обращения и тарифы;
- импорт GeoJSON, календарные исключения, feature flags, аудит и аналитика;
- SLA обращений, назначение исполнителя, ответ гражданину;
- CRUD тарифов с аудитом и инвалидацией Redis-кэша.

Backend:

- публичный и admin REST API, OpenAPI, единый error envelope и request ID;
- 18 Flyway-миграций, soft delete, аудит и Redis graceful degradation;
- маршрутизация, расписания, импорт, feature flags, citizen requests и fares;
- Testcontainers-интеграция с PostgreSQL/PostGIS.

## Быстрый запуск всего контура

Требуется Docker Desktop. Из корня репозитория:

```bash
# Задайте два разных случайных секрета длиной не менее 32 символов.
export ADMIN_API_KEY='replace-with-random-admin-api-key'
export ADMIN_SESSION_SECRET='replace-with-random-session-secret'
docker compose -f infra/docker-compose.full.yml up -d --build
docker compose -f infra/docker-compose.full.yml ps
```

Адреса:

- портал: <http://localhost:3000>
- тарифы: <http://localhost:3000/fares>
- обращения: <http://localhost:3000/requests>
- админка: <http://localhost:3001> — dev-вход `admin` / `metro2026`
- интерактивная карта в админке: <http://localhost:3001/map>
- API: <http://localhost:8080/api/v1>
- Swagger UI: <http://localhost:8080/api/swagger-ui.html>
- health: <http://localhost:8080/api/actuator/health>
- векторные тайлы Martin: <http://localhost:3003>
- MinIO: <http://localhost:9001>

Остановка без удаления данных:

```bash
docker compose -f infra/docker-compose.full.yml down
```

Удаление именованных томов выполняйте только когда данные действительно больше не нужны:

```bash
docker compose -f infra/docker-compose.full.yml down -v
```

## Запуск из исходников

Требования: Node.js 20+, JDK 21 LTS и Docker Desktop.

```bash
# инфраструктура
cd infra
docker compose up -d

# backend
cd ../backend
./mvnw spring-boot:run

# public web
cd ../web
npm ci
npm run dev

# admin (в другом терминале)
cd ../admin
npm ci
npm run dev
```

## Ключевые переменные окружения

| Переменная | Dev-значение | Назначение |
|---|---|---|
| `POSTGRES_DB/USER/PASSWORD` | `metro` | локальная БД |
| `WEB_API_BASE` | `http://localhost:8080/api/v1` | API из браузера публичного портала |
| `NEXT_PUBLIC_MAP_STYLE_URL` | `https://tiles.openfreemap.org/styles/liberty` | стиль реальной базовой карты; при недоступности включается локальный fallback |
| `ADMIN_API_BASE` | `http://backend:8080/api/v1` | серверные вызовы админки в compose |
| `ADMIN_API_KEY` | обязательное значение ≥32 символов | серверный секрет admin API |
| `ADMIN_BOOTSTRAP_USER` | `admin` только в dev | первичный локальный superadmin |
| `ADMIN_BOOTSTRAP_PASSWORD` | `metro2026-dev-only` только в dev | первичный локальный пароль |
| `ADMIN_SESSION_SECRET` | обязательное значение ≥32 символов | подпись сессионной cookie |
| `CSP_REPORT_ONLY` | не задана (`1` — включить) | переводит Content-Security-Policy портала и консоли в режим наблюдения (`Content-Security-Policy-Report-Only`): нарушения пишутся в консоль DevTools, но не блокируются; внешний report-uri сознательно не подключён |

Все dev-секреты обязательно заменяются в production.

### Заголовки безопасности и CSP

Портал (`web`) и консоль (`admin`) выставляют строгую Content-Security-Policy на
каждый запрос в `src/proxy.ts` (Next 16 переименовал middleware → proxy) с
одноразовым nonce из `src/lib/csp.ts`. Скрипты идут под `'nonce-… ' 'strict-dynamic'`
без `'unsafe-inline'`; `frame-ancestors 'none'` запрещает встраивание в iframe.
Карта MapLibre учтена: `connect-src`/`img-src` хоста `NEXT_PUBLIC_MAP_STYLE_URL`,
`worker-src blob:`. HSTS выставляется только на настоящем TLS (не на http-localhost).
Backend отдаёт API-CSP `default-src 'none'; frame-ancestors 'none'` из
`SecurityHeadersFilter`. Для отладки политики используйте `CSP_REPORT_ONLY=1`.

## Проверка качества

Актуальный приёмочный прогон от 16 июля 2026 года:

- backend: `292` теста, `0` failures, `0` errors, `0` skipped;
- web: `npm run lint` и `npm run build` — успешно;
- admin: `npm run lint` и `npm run build` — успешно;
- Checkstyle, SpotBugs, PMD и CPD — настроенная Maven-команда завершилась успешно;
- Docker E2E: health, сеть, тарифы, создание/трекинг обращения, admin-обновление статуса — успешно;
- браузерная production-проверка desktop/mobile — без console errors и hydration mismatch.

Команды:

```bash
cd backend
./mvnw test
./mvnw -DskipTests checkstyle:check spotbugs:check pmd:check pmd:cpd-check

cd ../web
npm run lint && npm run build

cd ../admin
npm run lint && npm run build
```

PMD продолжает показывать архитектурные предупреждения старого кода (в частности сложность
классов маршрутизации), а CPD — одну небольшую дубликацию. Они не ломают настроенную сборку,
но остаются зарегистрированным техническим долгом.

## Что требуется перед production

- официальные трассы, станции, расписания и тарифы;
- production OAuth2/OIDC/Keycloak realm и ротация всех секретов;
- реальные realtime/диспетчерские источники;
- платёжный/AFC-контур и юридически утверждённые правила продаж;
- внешняя система контакт-центра и политика вложений к обращениям;
- production-домены, TLS, мониторинг, резервное копирование и эксплуатационные SLO;
- отдельная реализация и публикация Flutter-приложения.

Эти пункты требуют внешних данных, доступов или продуктовых решений и не подменяются
локальными заглушками.
