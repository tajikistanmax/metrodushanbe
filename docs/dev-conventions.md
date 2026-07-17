# Конвенции разработки и контракт интеграции (v0.1)

Единый источник соглашений для всех частей платформы. Любое изменение здесь — сквозное и согласуется в PR.

## 1. Структура монорепозитория

```
MetroDushanbe/
├─ backend/     # Java 21 LTS + Spring Boot (модульный монолит, Maven)
├─ web/         # Next.js + TypeScript — публичный портал
├─ admin/       # Next.js + TypeScript — реализованная operational console
├─ packages/    # design — единый источник токенов и UI-примитивов (см. §5)
├─ mobile/      # Flutter — отдельная продуктовая фаза
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
| Admin (Next.js) | `3001` | локальный dev-вход задаётся через env |

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
  - `GET /api/v1/alerts` — активные сервисные уведомления (фильтры `?lineCode=`, `?stationCode=`, `?severity=info|warning|critical`; пустой массив `targets` = вся сеть; невалидный `severity` → 400 `alert.severity_invalid`). Семантика таргет-фильтров: network-wide уведомления (без таргетов) попадают в выдачу всегда; `lineCode` — уведомления, таргетированные этой линией (станционные таргеты линию не расширяют); `stationCode` — таргетированные этой станцией ИЛИ любой линией, которой станция принадлежит (по связи станция-линия); оба фильтра сразу — объединение: уведомление попадает, если проходит хотя бы один фильтр («не потерять уведомление» важнее строгости)
  - `GET /api/v1/fares` — активные тарифные продукты; demo-цены обязаны быть явно маркированы.
  - `POST /api/v1/requests`, `POST /api/v1/requests/{code}/track` — создание и безопасное отслеживание обращения.
  - `GET /api/v1/notifications` — лента in-app рассылок (NTF-01); фильтры `?lineCode=`, `?stationCode=` с ТОЙ ЖЕ семантикой, что у `/alerts` (см. выше): пустой набор таргетов = вся сеть.
  - `POST /api/v1/tickets/purchase|{code}/topup|validate|{code}/refund`, `GET /api/v1/tickets/{code}` — билеты (U-CIT-08). Токен билета отдаётся **один раз** при покупке, в БД только SHA-256 hash. Отказ платежа — `402` со штатным телом (`ticket: null`), а не ошибка.
  - `POST /api/v1/telemetry/positions`, `GET /api/v1/telemetry/positions?lineCode=` — realtime-позиции поездов (U-INT-04). Публикация закрыта машинным ключом (временно `X-Admin-Key`; прод обязан перейти на OAuth2 client credentials).
- Коды ошибок — литеральные строки вида `<домен>.<snake_case>` в месте броска (`fare.not_found`, `notification.frozen`, `ticket.not_refundable`, `webhook_delivery.retry_not_allowed`…). Отдельного реестра нет; новый код документируется в `@Operation.description`.
- Карта переходов состояния отдаётся клиенту вычисленной (`allowedTransitions` у `IncidentDto`, `NotificationDto`, `WebhookDeliveryDto`). Дублировать её в UI **нельзя**: две копии одного правила разъезжаются, и консоль начнёт предлагать действие, которое backend отклонит.
- OpenAPI UI: `http://localhost:8080/api/swagger-ui.html` (springdoc).
- CORS (dev): разрешён `http://localhost:3000`.

## 4. Модель данных (первый срез)

Основана на ТЗ §6.3.1 (DDL): `metro_line`, `metro_station`, `metro_station_line`;
сервисные уведомления (ТЗ §6.2.6): `service_alert`, `service_alert_target`
(пустой таргетинг = уведомление на всю сеть).
- ID — UUID; `code` — стабильный внешний идентификатор (никогда не меняется).
- Локализуемые тексты — JSONB вида `{"tg":"…","ru":"…","en":"…"}` (`name_i18n`,
  а также `title_i18n`/`body_i18n` уведомлений); обязательные языки: tg, ru, en.
- Геометрии — PostGIS `geometry(...,4326)` + GiST-индексы.
- Статусы линий: `planned|under_construction|testing|active|suspended|decommissioned`;
  станций: `planned|under_construction|testing|active|temporarily_closed|decommissioned`.
- Миграции — только Flyway (`backend/src/main/resources/db/migration`), нумерация `V001__`, `V002__`… Занято по `V025` включительно. **Номер обязан быть уникальным**: две миграции с одной версией роняют старт приложения (`Found more than one migration with version N`) — так уже случалось с задвоенной `V020`.
- Enum'ы — `varchar` + именованный `CONSTRAINT chk_<table>_<field> CHECK (...)`; значения обязаны совпадать с `code()` соответствующего Java-enum (см. вложенный `Persistence`-конвертер).
- Инварианты состояния выражаются CHECK-констрейнтом, а не только кодом (`chk_incident_resolution`, `chk_notification_delivery_error`): «провал без причины» и «resolved без разбора» не должны существовать в БД физически.
- Одноразовые секреты (tracking token обращения, токен билета, секрет вебхука) хранятся **только** как SHA-256 hash; сравнение — constant-time (`MessageDigest.isEqual`).
- `data/demo-network.geojson` — канонический контракт демо-данных: web использует его как офлайн-fallback, backend — как источник seed-миграции. Схемы должны совпадать.

## 5. Дизайн: токены и примитивы

**Единый источник — `packages/design/`.** Токены (`tokens.mjs`) и примитивы (`shared/`)
раскладываются генератором по обоим приложениям:

```
node packages/design/sync.mjs          # записать
node packages/design/sync.mjs --check  # проверить синхронность (джоба CI Design)
```

**Почему генерация, а не npm-workspace.** `web/Dockerfile` и `admin/Dockerfile` собираются
с контекстами `../web` и `../admin` (`infra/docker-compose.full.yml`). Из контекста `web/`
каталог `packages/` не виден: `COPY . .` его не заберёт, symlink наружу Docker не
разыменует, workspace-пакет `npm ci` в контейнере не найдёт. Поэтому общий слой не
подключается, а раскладывается и коммитится обычными файлами внутри приложений.

**Правки в `web/src/shared/**`, `admin/src/shared/**` и в блоке между маркерами
`>>> НАЧАЛО СГЕНЕРИРОВАННОГО БЛОКА` в `globals.css` затираются.** Меняешь токен или
примитив — правь `packages/design/`, запусти `sync.mjs`, убедись, что `--check` проходит.
Остальная часть `globals.css` правится свободно. Полные таблицы токенов и миграции старых
имён — в `packages/design/MIGRATION.md`.

Фирменные цвета (из `photo/dushanbe_metro_logo.svg`): `--brand-navy` `#082742` (primary),
`--brand-red` `#e21b2d` (accent/L1), `--brand-green` `#138a3d` (secondary/L2).
Шрифт — Montserrat, self-host через `@fontsource` (без внешних CDN в проде), загружены
веса **400/600/700/800**: `font-medium` (500) использовать нельзя — браузер синтезирует
начертание.

Стиль — строгий институциональный (ориентир GOV.UK): сдержанная палитра, крупная
типографика, воздух, чёткая сетка. Градиенты, свечения, blur и «подпрыгивания» из системы
удалены осознанно — плоскости разделяются границей и цветом. Радиусы — шкала `0/2/4/8`.
Лента флага РТ (`.ribbon-flag`) — государственная сигнатура, а не декор: сохраняется.

Правила: контраст WCAG 2.2 AA; **цвет не единственный носитель смысла**; у карты всегда
есть list-mode. Цветной текст на цветной подложке запрещён — `Badge`/`Alert` кладут
`--text-primary` на тинт, а тон несут подложка, точка и слова (в тёмной теме
`text-brand-green` на `--tint-success` давал ≈1.6:1 — это уже ловили).
Зелёной кнопки в системе нет намеренно: белый на `#138a3d` даёт ≈4.4:1 и не проходит AA.
Красный означает **опасное действие** (`Button variant="danger"`), а не «главное».

## 6. i18n

- Языки: `tg` (по умолчанию), `ru`, `en`. Все строки UI — в словарях, без хардкода.
- Порядок определения: профиль → `Accept-Language` → default `tg`.

## 7. Качество

- Backend: JDK 21 LTS, без Lombok (records/конструкторы), Testcontainers для интеграционных тестов, ошибки — через `@ControllerAdvice` в единый envelope.
- Тесты backend — четыре уровня: доменные (`<module>/domain/`), сервисные на Mockito, web-срезы (`@WebMvcTest` + `@MockitoBean`, не `@MockBean`) и интеграционные на Testcontainers (`<Domain>ApiIntegrationTest` **плоско в корне** пакета `tj.metro.dushanbe`, пути без `/api`).
- Интеграционным тестам нужен рабочий Docker (`postgis/postgis:16-3.4`). Прогонять отдельно: `./mvnw -B test -Dtest='*IntegrationTest'`. Актор admin-запросов в них обязан существовать в `admin_user` — берите бутстрап-суперадмина `admin`, иначе 401 `auth.session_revoked` (на этом уже спотыкались).
- Web/admin: TypeScript strict, ESLint; `npm run lint`, `npx tsc --noEmit` и `npm run build` обязаны проходить.
- Общий слой дизайна: `node packages/design/sync.mjs --check` обязан проходить (джоба CI Design).
- Секреты — только через env; в репозитории только dev-значения compose.
- Git: ветка `main`, коммиты по Conventional Commits (`feat:`, `fix:`, `docs:`…).

## 8. Офлайн-принцип (ТЗ §6.2.18)

Web/mobile обязаны деградировать без сети/бэкенда: карта первого среза рендерит
`data/demo-network.geojson` (бандлится в `web/public/data/`), а при доступном API
переключается на `GET /api/v1/network/geojson`.
