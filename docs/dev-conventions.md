# Конвенции разработки и контракт интеграции (v0.1)

Единый источник соглашений для всех частей платформы. Любое изменение здесь — сквозное и согласуется в PR.

## 1. Структура монорепозитория

```
MetroDushanbe/
├─ backend/     # Java 21 + Spring Boot (модульный монолит, Maven)
├─ web/         # Next.js + TypeScript — публичный портал
├─ admin/       # Next.js + TypeScript — админ-панель (следующая итерация)
├─ mobile/      # Flutter (следующая итерация)
├─ infra/       # docker-compose, конфиги окружений
├─ data/        # канонические демо/импорт-данные (GeoJSON)
└─ docs/        # ТЗ и проектная документация
```

## 2. Порты и адреса (dev)

| Сервис | Порт | Примечание |
|---|---|---|
| PostgreSQL + PostGIS | `5433` | db=`metro`, user=`metro`, pass=`metro` (только dev!); 5433 — чтобы не конфликтовать с нативным PostgreSQL на 5432 |
| Redis | `6379` | |
| Keycloak | `8081` | compose-профиль `auth`, подключается на фазе admin |
| Backend (Spring Boot) | `8080` | API base: `http://localhost:8080/api/v1` |
| Web (Next.js) | `3000` | |

## 3. Контракт API

- База: `/api/v1/...`, формат `application/json`; геослои — GeoJSON (RFC 7946).
- Ошибки — единый envelope (ТЗ §7.5): `{timestamp, requestId, error:{code, message, details}}`.
- Локализация: query `?lang=tg|ru|en` + заголовок `Accept-Language`; поля `name` в ответах — либо весь i18n-объект, либо разрешённая строка по `lang` (эндпоинт документирует).
- Эндпоинты первого среза:
  - `GET /api/v1/lines` — список линий (фильтр `?status=`)
  - `GET /api/v1/lines/{code}` — карточка линии
  - `GET /api/v1/stations` — список станций (фильтры `?lineCode=`, `?status=`)
  - `GET /api/v1/stations/{code}` — карточка станции
  - `GET /api/v1/network/geojson` — FeatureCollection всей сети для карты (совместим по схеме с `data/demo-network.geojson`)
- OpenAPI UI: `http://localhost:8080/api/swagger-ui.html` (springdoc).
- CORS (dev): разрешён `http://localhost:3000`.

## 4. Модель данных (первый срез)

Основана на ТЗ §6.3.1 (DDL): `metro_line`, `metro_station`, `metro_station_line`.
- ID — UUID; `code` — стабильный внешний идентификатор (никогда не меняется).
- Названия — JSONB `name_i18n` вида `{"tg":"…","ru":"…","en":"…"}`; обязательные языки: tg, ru, en.
- Геометрии — PostGIS `geometry(...,4326)` + GiST-индексы.
- Статусы линий: `planned|under_construction|testing|active|suspended|decommissioned`;
  станций: `planned|under_construction|testing|active|temporarily_closed|decommissioned`.
- Миграции — только Flyway (`backend/src/main/resources/db/migration`), нумерация `V001__`, `V002__`…
- `data/demo-network.geojson` — канонический контракт демо-данных: web использует его как офлайн-fallback, backend — как источник seed-миграции. Схемы должны совпадать.

## 5. Дизайн-токены бренда (из `photo/dushanbe_metro_logo.svg`)

| Токен | Значение |
|---|---|
| `--brand-navy` (primary) | `#082742` |
| `--brand-red` (accent/L1) | `#E21B2D` |
| `--brand-green` (secondary/L2) | `#138A3D` |
| `--surface-light` | `#FFFFFF` |
| `--surface-muted` | `#F2F5F8` |
| `--surface-dark` | `#0B1622` |
| `--text-secondary` | `#3A4A5A` |
| `--warning` | `#E08600` |
| `--info` | `#0E5A8A` |
| Шрифт бренда | Montserrat (self-host, без внешних CDN в проде) |

Правила: контраст WCAG 2.2 AA; цвет не единственный носитель смысла; у карты всегда есть list-mode.

## 6. i18n

- Языки: `tg` (по умолчанию), `ru`, `en`. Все строки UI — в словарях, без хардкода.
- Порядок определения: профиль → `Accept-Language` → default `tg`.

## 7. Качество

- Backend: JDK 21, без Lombok (records/конструкторы), Testcontainers для интеграционных тестов, ошибки — через `@ControllerAdvice` в единый envelope.
- Web: TypeScript strict, ESLint; `npm run build` обязан проходить.
- Секреты — только через env; в репозитории только dev-значения compose.
- Git: ветка `main`, коммиты по Conventional Commits (`feat:`, `fix:`, `docs:`…).

## 8. Офлайн-принцип (ТЗ §6.2.18)

Web/mobile обязаны деградировать без сети/бэкенда: карта первого среза рендерит
`data/demo-network.geojson` (бандлится в `web/public/data/`), а при доступном API
переключается на `GET /api/v1/network/geojson`.
