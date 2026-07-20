# Metro Dushanbe — Backend

Модульный монолит на Java 21 LTS и Spring Boot 3.4. Хранение — PostgreSQL/PostGIS,
миграции — Flyway, кэш — Redis с graceful degradation.

## Возможности

- каталог линий, станций, выходов и геометрии сети;
- сервисные уведомления и новости;
- маршрутизация и статическое расписание;
- импорт GeoJSON с журналом ошибок;
- календарные исключения и feature flags;
- обращения граждан с SLA, tracking token и admin workflow;
- тарифные продукты и admin CRUD;
- аудит операций, OpenAPI, health/readiness и единый error envelope.

## Требования и запуск

- JDK 21 LTS;
- Docker для PostgreSQL/PostGIS, Redis и интеграционных тестов;
- Maven Wrapper из репозитория.

```bash
cd ../infra
docker compose up -d

cd ../backend
./mvnw spring-boot:run
```

На Windows используйте `mvnw.cmd`.

## Адреса

| Что | URL / endpoint |
|---|---|
| API base | `http://localhost:8080/api/v1` |
| Lines | `GET /api/v1/lines` |
| Stations | `GET /api/v1/stations` |
| Network GeoJSON | `GET /api/v1/network/geojson` |
| Alerts | `GET /api/v1/alerts` |
| News | `GET /api/v1/news` |
| Route | `GET /api/v1/routes` |
| Schedule | `GET /api/v1/lines/{code}/schedule`, `GET /api/v1/stations/{code}/arrivals` |
| Fares | `GET /api/v1/fares` |
| Create request | `POST /api/v1/requests` |
| Track request | `POST /api/v1/requests/{code}/track` |
| Admin requests | `/api/v1/admin/requests` |
| Admin fares | `/api/v1/admin/fares` |
| Swagger UI | `http://localhost:8080/api/swagger-ui.html` |
| Health | `http://localhost:8080/api/actuator/health` |

Admin endpoints требуют `X-Admin-Key`; значение задаётся только через server-side env.

## Миграции

Миграции разделены на **два каталога с разным смыслом**:

| Каталог | Содержимое | Где применяется |
|---|---|---|
| `src/main/resources/db/migration` | только схема БД | во всех средах, включая production |
| `src/main/resources/db/seed` | демонстрационные данные (placeholder) | dev, тесты, `docker-compose.full.yml` — **никогда в production** |

Схема (`db/migration`):

- `V001`, `V003`, `V005`, `V007` — сеть, сервисные уведомления, новости, детали станций;
- `V009–V011`, `V013–V015` — аудит, импорт, расписания, индексы, календарь, feature flags;
- `V017–V019` — обращения граждан, тарифные продукты, admin-пользователи;
- `V020–V025` — сессии, инциденты, уведомления, билеты, интеграции, форматы импорта;
- `V026` — гасит demo-тарифы из `V018` (см. ниже).

Демо-сид (`db/seed`): `V002` (сеть), `V004` (алерты), `V006` (новости), `V008` (выходы
и доступность), `V012` (графики движения), `V016` (календарь праздников),
`V027` (активация demo-тарифов).

Нумерация версий **сквозная через оба каталога**: Flyway объединяет все `locations`
и сортирует миграции по версии глобально, поэтому dev получает V001, V002, V003…,
а production — ту же последовательность без seed-версий. Одна и та же версия не
должна встречаться в двух каталогах — Flyway падает с `Found more than one migration`.

Flyway автоматически валидирует историю при старте. Hibernate работает с
`ddl-auto=validate`, поэтому схема меняется только миграциями. Разделение каталогов
не трогает содержимое и версии файлов, поэтому контрольные суммы прежние и **уже
существующие dev/демо-базы продолжают проходить `flyway validate` без `repair`**.

### Демо-данные и production

`db/seed` содержит ВЫДУМАННЫЕ линии, станции, выходы, новости, расписания и тарифы.
Публикация их на государственном портале как официальных данных недопустима, поэтому:

- `application.yml` (dev/тесты/демо): `spring.flyway.locations=classpath:db/migration,classpath:db/seed`;
- `application-prod.yml`: `spring.flyway.locations=classpath:db/migration`;
- `DemoSeedGuard` пишет на старте однозначную строку о режиме данных
  (`Данные: ДЕМОНСТРАЦИОННЫЕ` / `Данные: только реальные`) и в профиле `prod`
  **не даёт приложению подняться**, если seed-каталог всё-таки попал в `locations`
  (например, через `SPRING_FLYWAY_LOCATIONS`).

Следствия для production, которые нужно закрыть вводом реальных данных:

- сеть, станции, выходы, новости и расписания вводятся через admin-консоль и
  конвейер импорта (ТЗ, раздел 13);
- **календарь праздников** (`V016`) в production пуст. Данные там реальные
  (госпраздники Республики Таджикистан), но лежат в seed-каталоге, поэтому
  официальный календарь обязан быть загружен через admin CRUD календарных
  исключений — иначе тип дня определяется только как будни/выходной;
- demo-тарифы `DEMO-SINGLE`/`DEMO-MONTHLY` из `V018` деактивированы миграцией
  `V026` (не удалены — на них ссылаются билеты и платежи из `V023`), поэтому
  публичный `GET /v1/fares` в production вернёт пустой список до ввода
  утверждённых тарифов.

## Безопасность обращений

Публичный ответ при создании содержит номер обращения и случайный tracking token. В БД
хранится только SHA-256 hash токена; сравнение выполняется constant-time. Номер без токена
не раскрывает персональные данные или текст обращения.

## Redis

Кэшируются публичные линии, станции, GeoJSON, новости, уведомления и тарифы. Ошибка Redis
не обрывает запрос: backend пишет предупреждение и продолжает работу через PostgreSQL.
Изменения admin-контура инвалидируют соответствующие кэши.

## Тесты и анализ

```bash
./mvnw test
./mvnw -DskipTests checkstyle:check spotbugs:check pmd:check pmd:cpd-check
```

Последний полный прогон: `292` теста, без failures/errors/skipped. Интеграционные тесты
поднимают PostgreSQL/PostGIS через Testcontainers и проверяют все 18 миграций, публичные и
admin endpoints, soft delete и JPA validation.

PMD/CPD выводят неблокирующий зарегистрированный техдолг старых модулей маршрутизации и
сети; настроенная команда при этом завершается успешно.

## Пакеты

```text
tj.metro.dushanbe
├─ admin/        admin controllers, services и X-Admin-Key security
├─ alert/        сервисные уведомления
├─ audit/        аудит операций
├─ citizen/      обращения граждан и tracking
├─ content/      новости
├─ fare/         тарифные продукты
├─ featureflag/  feature flags
├─ imports/      GeoJSON import jobs
├─ network/      линии, станции, выходы и GeoJSON
├─ routing/      построение маршрута
├─ schedule/     расписания и календарь
├─ config/       cache, CORS, OpenAPI, health и logging
└─ common/       общие ошибки и инфраструктурные типы
```

Проект не использует Lombok: DTO реализованы records, доменные сущности — явными классами.
