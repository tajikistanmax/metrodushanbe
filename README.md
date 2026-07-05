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

## Фазы (по ТЗ v2, раздел 10)

1. **Сейчас:** вертикальный срез — инфраструктура, ядро сети (линии/станции), карта.
2. MVP: карточки станций, новости, service alerts, admin, i18n полный контур.
3. Операционная готовность: импорт GTFS/GeoJSON, интеграции, дашборды.
4. Далее: мультимодальность → билеты/AFC → realtime → запуск.
