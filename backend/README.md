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

В `src/main/resources/db/migration` находятся 18 последовательных миграций:

- `V001–V004` — сеть и сервисные уведомления;
- `V005–V010` — расширенная модель станций, расписания, новости и аудит;
- `V011–V016` — импорт, feature flags и служебные возможности;
- `V017` — обращения граждан, SLA и tracking secret hash;
- `V018` — тарифные продукты и явно маркированные demo seed-данные.

Flyway автоматически валидирует историю при старте. Hibernate работает с
`ddl-auto=validate`, поэтому схема меняется только миграциями.

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
