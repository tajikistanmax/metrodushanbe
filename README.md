# Метро Душанбе — национальная цифровая платформа

Монорепозиторий платформы «Метро Душанбе»: публичный портал, backend API, административная панель и мобильное приложение. Техническое задание: [docs/metrodushanbe-v2.md](docs/metrodushanbe-v2.md). Конвенции: [docs/dev-conventions.md](docs/dev-conventions.md).

## Состав

| Каталог | Что это | Стек | Статус |
|---|---|---|---|
| `backend/` | API-платформа (модульный монолит) | Java 25, Spring Boot, PostgreSQL + PostGIS, Flyway, Redis | 🚧 первый срез |
| `web/` | Публичный портал с картой сети | Next.js, TypeScript, MapLibre GL | 🚧 первый срез |
| `admin/` | Админ-панель (operational console) | Next.js, TypeScript | ⏳ следующая итерация |
| `mobile/` | Мобильное приложение | Flutter | ⏳ следующая итерация |
| `infra/` | Локальное окружение | Docker Compose (PostGIS, Redis, Keycloak) | 🚧 первый срез |
| `data/` | Демо-данные сети (placeholder) | GeoJSON | ✅ |

> ⚠️ Данные сети в `data/` — **демонстрационные**. Реальные трассы и станции не утверждены; они будут загружаться через конвейер импорта (ТЗ, раздел 13).

## Требования для разработки

- **Node.js 20+** (web/admin)
- **JDK 25** (backend) — например, [Eclipse Temurin](https://adoptium.net/)
- **Docker Desktop** (PostgreSQL/PostGIS, Redis, Keycloak)
- Flutter SDK (mobile, позже)

## Быстрый старт

```bash
# 1. Инфраструктура (Postgres+PostGIS, Redis)
cd infra && docker compose up -d

# 2. Backend (после установки JDK 25)
cd backend && ./mvnw spring-boot:run
# API:      http://localhost:8080/api/v1/lines
# OpenAPI:  http://localhost:8080/api/swagger-ui.html

# 3. Web-портал
cd web && npm install && npm run dev
# http://localhost:3000  (карта работает и БЕЗ backend — на демо-данных)
```

## Полный стек в контейнерах (Docker)

Два независимых compose-файла в `infra/` под разные задачи:

| Файл | Назначение | Что поднимает |
|---|---|---|
| `infra/docker-compose.yml` | **dev-инфра** — для локального запуска backend/web из исходников | Postgres+PostGIS, Redis (+ Keycloak по профилю `auth`) |
| `infra/docker-compose.full.yml` | **весь контур в образах** — демо/приёмка | postgres + redis + backend + web + admin |

Полный стек собирается и поднимается одной командой (из корня репозитория):

```bash
docker compose -f infra/docker-compose.full.yml up -d --build
# web:     http://localhost:3000
# admin:   http://localhost:3001
# API:     http://localhost:8080/api/v1
# Swagger: http://localhost:8080/api/swagger-ui.html

docker compose -f infra/docker-compose.full.yml config     # проверить конфиг
docker compose -f infra/docker-compose.full.yml down -v     # остановить + удалить данные БД
```

Порты на хосте те же, что в dev (5433/6379/8080/3000/3001), поэтому dev-инфру и
полный стек **одновременно не поднимают**. Образы (`backend/Dockerfile`,
`web/Dockerfile`, `admin/Dockerfile`) — multi-stage: Temurin JDK 25 → JRE 25 для
backend и Next.js standalone для web/admin.

Ключевые env (все значения по умолчанию — dev-заглушки, менять для прода):

| Переменная | Сервис | По умолчанию | Смысл |
|---|---|---|---|
| `POSTGRES_DB/USER/PASSWORD` | postgres | `metro` | учётные данные БД |
| `WEB_API_BASE` | web (build-arg) | `http://localhost:8080/api/v1` | база API из **браузера** (публичный портал) |
| `ADMIN_API_BASE` | admin (build-arg) | `http://backend:8080/api/v1` | база API для **серверных** вызовов админки |
| `ADMIN_API_KEY` | admin + backend | `dev-admin-key-change-me` | секрет `X-Admin-Key` admin-контура (согласован с обеих сторон) |

## Фазы (по ТЗ v2, раздел 10)

1. **Сейчас:** вертикальный срез — инфраструктура, ядро сети (линии/станции), карта.
2. MVP: карточки станций, новости, service alerts, admin, i18n полный контур.
3. Операционная готовность: импорт GTFS/GeoJSON, интеграции, дашборды.
4. Далее: мультимодальность → билеты/AFC → realtime → запуск.
