# Metro Dushanbe — Backend

Модульный монолит на **Java 25 + Spring Boot 3.4** (Maven). Первый срез — модуль
`network`: сетевой каталог (линии, станции) и GeoJSON-слой сети; модуль `alert` —
публичный контур сервисных уведомлений (ТЗ §6.2.6).

Контракт API, порты, статусы и модель данных — в [docs/dev-conventions.md](../docs/dev-conventions.md);
модель данных подробно — ТЗ [docs/metrodushanbe-v2.md](../docs/metrodushanbe-v2.md), §6.3.1.

## Предварительные требования

- **JDK 25+** (Temurin/Oracle). Проверка: `java -version`. Maven Wrapper требует
  переменную окружения `JAVA_HOME` (установщик JDK её не прописывает).
- **Docker** — для PostgreSQL + PostGIS (compose в [`../infra`](../infra))
- Maven ставить не нужно — используется Maven Wrapper (`mvnw` / `mvnw.cmd`)

## Запуск

```bash
# 1) Поднять инфраструктуру (PostgreSQL+PostGIS, Redis)
cd ../infra
docker compose up -d

# 2) Собрать и запустить backend (из каталога backend/)
./mvnw spring-boot:run          # Linux/macOS/Git Bash
mvnw.cmd spring-boot:run        # Windows (cmd/PowerShell)
```

Миграции Flyway (`src/main/resources/db/migration`) применяются автоматически:
`V001` — схема (metro_line, metro_station, metro_station_line), `V002` — демо-сиды,
согласованные с каноническим файлом [data/demo-network.geojson](../data/demo-network.geojson),
`V003` — схема сервисных уведомлений (service_alert, service_alert_target),
`V004` — демо-сиды alerts (окна действия заданы относительно `now()`).

## Адреса (dev)

| Что | URL |
|---|---|
| База API | `http://localhost:8080/api/v1` |
| Линии | `GET /api/v1/lines`, `GET /api/v1/lines/{code}` |
| Станции | `GET /api/v1/stations?lineCode=&status=`, `GET /api/v1/stations/{code}` |
| GeoJSON сети | `GET /api/v1/network/geojson` |
| Уведомления | `GET /api/v1/alerts?lineCode=&stationCode=&severity=` |
| Swagger UI | `http://localhost:8080/api/swagger-ui.html` |
| Health | `http://localhost:8080/api/actuator/health` |

Ошибки — единый envelope `{timestamp, requestId, error:{code, message, details}}`;
requestId берётся из заголовка `X-Request-Id` (или генерируется) и возвращается в ответе.

## Тесты

```bash
./mvnw test
```

- `GeoJsonBuilderTest` — юнит-тест сборки FeatureCollection из фикстур (без БД).
- `AlertServiceTest` — юнит-тест окна действия/сортировки/фильтров alerts
  (репозиторий — mock, «сейчас» — `Clock.fixed`, без БД).
- `NetworkApiIntegrationTest`, `AlertApiIntegrationTest` — интеграционные тесты
  с Testcontainers (PostGIS); требуют запущенный Docker. Testcontainers зафиксирован
  на ≥1.21.4 (Docker Engine 29 требует API ≥1.44 — старые версии падают с 400 Bad Request).

## Структура пакетов

```
tj.metro.dushanbe
├─ config/          # CORS (localhost:3000), OpenAPI, Clock (systemUTC, подменяем в тестах)
├─ common/error/    # envelope ошибок, @RestControllerAdvice, X-Request-Id фильтр
├─ network/         # модуль «сетевой каталог»
│  ├─ domain/       # JPA-сущности (JSONB name_i18n, геометрии JTS/PostGIS)
│  ├─ repository/   # Spring Data JPA
│  ├─ service/      # бизнес-логика + ручная сборка GeoJSON
│  └─ web/          # REST-контроллеры /v1/**, DTO-records
└─ alert/           # модуль «сервисные уведомления» (публичный контур, ТЗ §6.2.6)
   ├─ domain/       # ServiceAlert (JSONB title/body_i18n) + AlertTarget (@ElementCollection)
   ├─ repository/   # Spring Data JPA (published + fetch join таргетов)
   └─ service/, web/ # окно действия/сортировка/фильтры; GET /v1/alerts
```

Без Lombok — Java records и явные конструкторы (см. dev-conventions §7).
