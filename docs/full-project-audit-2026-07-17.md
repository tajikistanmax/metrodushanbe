# Полный технический аудит Metro Dushanbe — 17 июля 2026

Аудит охватывает `backend`, `web`, `admin`, Flyway/data, Docker Compose, CI/CD,
безопасность, бизнес-правила, PWA, доступность и документацию. Приоритеты:
`P0` — блокирует безопасный запуск, `P1` — высокий риск, `P2` — существенный
технический долг/ошибка, `P3` — улучшение качества.

## Подтверждённые проверки

- `web`: ESLint и production build успешны.
- `admin`: ESLint и production build успешны, включая `/incidents`.
- npm audit: по две moderate-уязвимости вложенного PostCSS; безопасного
  автоматического обновления пока нет, `npm audit fix --force` применять нельзя.
- Backend: найден локальный JDK 21; компиляция успешна, 618 unit-тестов прошли
  без ошибок. Дополнительно прошли 59 целевых тестов webhook/ticketing/admin и
  12 тестов CSV import parser. Checkstyle — 0 нарушений, SpotBugs — 0 замечаний.
- PMD сформировал 364 design/best-practice замечания, CPD — 5 блоков дублирования;
  текущая конфигурация намеренно имеет `failOnViolation=false`, поэтому это backlog,
  а не зелёный quality gate. Docker daemon не отвечает, поэтому PostgreSQL/Flyway/JPA
  integration suites в этой сессии повторно не запускались.

## Выполнено в первой волне после аудита

- Устранена Flyway-коллизия: incidents перенесены на V021 (notification — V022).
- Admin secrets стали обязательными и fail-closed в production; введён минимум
  32 символа, prod bootstrap больше не наследует dev credentials.
- Устранён redirect-loop отозванной cookie; admin backend URL стал server-only.
- Добавлены таймауты admin SSR/login/actions и `public/` в admin Docker image.
- Clock переведён на `Asia/Dushanbe`; auto-day schedule больше не кэшируется
  между датами; сетевые мутации очищают route/schedule caches.
- Закрытие обращения больше не стирает опубликованный ответ гражданину.
- Добавлена WGS84/finite-валидация координат и тесты граничных случаев.
- Rate limiter больше не доверяет XFF по умолчанию и ограничивает число buckets.
- Incident разрешает назначение только на активного оператора.
- Route planner инвалидирует устаревший ответ при изменении from/to/swap.

## Выполнено во второй и третьей волнах

- Server Actions получили проверку ролей; tracking token обращения перенесён из
  постоянного `localStorage` в `sessionStorage`, поле ввода маскируется.
- В web/admin добавлены root `error.tsx`, `loading.tsx`, `not-found.tsx` и базовые
  security headers; поверх них выставлена строгая nonce-CSP с `frame-ancestors`
  и HSTS на TLS-периметре (см. пункт 10). `global-error.tsx` ещё в backlog.
- `/news/[slug]` теперь загружается на сервере, различает backend outage и 404,
  вызывает `notFound()` и формирует metadata/Open Graph.
- CI запускает Maven `verify`, публикует JaCoCo/dependency-check отчёты, OWASP
  threshold исправлен с невозможного 11 на 9; статические анализаторы включены.
- Webhook URL переведён на HTTPS-only: URI проверяется при create/update и повторно
  после DNS resolve перед каждой отправкой; private/link-local/loopback/CGNAT/ULA
  адреса блокируются, redirect отключён, не-2xx не считается доставкой.
- Валидация билета и refund используют отдельные pessimistic write locks. Для
  возврата закреплён порядок блокировок `Ticket → Payment`, закрывающий двойную
  валидацию/двойной вызов refund в пределах одной БД.
- Payment/Refund/Ticket теперь сами проверяют state transitions, обязательные
  provider references/reasons и положительное продлевающее top-up. В V023 добавлены
  CHECK completed-refund reference и unique active refund per payment.
- Устранено единственное замечание SpotBugs: nullable CSV boolean теперь выражен
  через `Optional<Boolean>`; parser tests и повторный SpotBugs прошли.
- Design token scripts существуют, проходят `node --check`, drift-check подключён к CI.

## Выполнено в четвёртой волне

- Временной cache активных alerts удалён: `startsAt/endsAt` теперь вычисляются по
  текущему `Clock` на каждом запросе и не могут устареть на десять минут.
- Изменение/удаление последнего активного superadmin сериализуется pessimistic
  lock всех активных владельцев роли в стабильном порядке.
- Admin import перенесён с Server Actions в same-origin Route Handler: исходный
  `File` отправляется без `file.text()`/FormData, body ограничен 8/16 MiB даже без
  `Content-Length`, добавлены JSON 401/403, CSRF origin-check и отдельный timeout.
- Notification scheduler забирает по одной due-рассылке через `FOR UPDATE SKIP LOCKED`;
  ручная отправка блокирует сообщение, а БД запрещает дубли recipient/channel,
  отрицательные attempts и некорректные временные состояния доставки.
- Webhook dispatcher получил атомарный claim token/lease, статус `processing` и
  optimistic version. Сетевая операция выполняется вне транзакции; expired lease
  восстанавливается, а поздний worker не может перезаписать результат нового.
- Тело webhook, event type, aggregate и trace фиксируются immutable snapshot при
  fan-out. Retry больше не зависит от retention outbox и подписывает те же байты.

## P0 — критические блокеры

1. **[Закрыто] Коллизия Flyway V020.** Incidents перенесены на V021; проверка
   текущего дерева подтверждает 25 уникальных Flyway-версий.
2. **[Закрыто] Известные production-секреты.** Compose и server config теперь
   требуют отдельные секреты, production работает fail-closed и проверяет длину.
3. **[Закрыто] Известный production bootstrap-superadmin.** Prod больше не
   наследует dev login/password из `application.yml`.
4. **[Открыто] Production OIDC неработоспособен.** Нет стандартной настройки/bean
   `JwtDecoder`, нет claim→role converter, JWT одновременно конфликтует с
   обязательным `X-Admin-Key`, admin не передаёт bearer token, CSRF остаётся
   включённым для stateless API.

## P1 — высокий приоритет

### Безопасность и авторизация

5. [Закрыто] Актор аудита привязан к сессии подписанным токеном. Консоль присылает
   `X-Admin-Actor-Token` (HMAC-SHA256 на отдельном секрете `app.admin.actor-token.secret`
   с username, sessionVersion, issuedAt, nonce и TTL 2 мин). `AdminKeyAuthFilter`
   проверяет подпись через `MessageDigest.isEqual`, сверяет `sessionVersion` с
   `admin_user.session_version` (отозванная сессия отклоняется сразу) и подменяет
   `X-Admin-Actor` подтверждённым логином — присланное имя игнорируется. Strict-режим
   (`app.admin.actor-token.required`) включён в prod-профиле, fail-closed при пустом
   секрете; в dev/интеграционных тестах остаётся фолбэк на голый `X-Admin-Actor`.
   ОГРАНИЧЕНИЕ: секрет общий симметричный — это НЕ OIDC (замена эмитента — открытый
   P0 №4), replay в пределах TTL возможен.
6. [Закрыто] Отозванная admin-cookie больше не создаёт цикл `/login → / → /login`.
7. [Закрыто] XFF по умолчанию не доверяется, число IP-buckets ограничено.
8. [Частично] Server Actions проверяют роли; полную route/method/UI матрицу всё
   ещё нужно закрепить table-driven тестом.
9. [Частично] Добавлены строгий лимит и lockout входа; MFA сознательно не делается
   (требует внешнего провайдера — решение владельца). Два независимых счётчика неудач
   в БД (`admin_login_attempt`, миграция V026, переживает перезапуск): по учётной
   записи (порог 5) и по паре IP+username (порог 10), миграция V028 (номер V026 уже
   был занят параллельной работой), окно и блокировка по 15 мин,
   успех сбрасывает счётчик. Перечисление учёток закрыто: неизвестный логин и неверный
   пароль дают один код `auth.invalid_credentials` и одинаковое время (сверка с
   `dummyHash` для несуществующего пользователя), а счётчик ведётся по логину
   независимо от его существования. Каждая неудача (`auth.login_failed`) и каждый
   lockout (`auth.account_locked`) пишутся через `auditService.recordIndependently`
   (REQUIRES_NEW, переживает откат 401). Блокировка — 429 `auth.too_many_attempts` с
   `Retry-After`. ОСТАЁТСЯ: MFA (внешний блокер).
10. [Закрыто] Базовые nosniff/frame/referrer/permissions headers дополнены
    строгой Content-Security-Policy. В web и admin CSP собирается на каждый
    запрос в `src/proxy.ts` с одноразовым nonce (`src/lib/csp.ts`): скрипты —
    `'nonce-…' 'strict-dynamic'` без `'unsafe-inline'`, включая анти-FOUC скрипт
    темы (nonce пробрасывается в `layout.tsx` через `headers()`); `frame-ancestors
    'none'` закрывает clickjacking; карта разрешена точечно (`connect-src`/`img-src`
    хоста стиля, `worker-src blob:`). Для style-атрибутов React (`style={{…}}`)
    оставлен `'unsafe-inline'` только в `style-src-attr` — сами таблицы стилей
    грузятся со своего origin. У backend `SecurityHeadersFilter` отдаёт API-CSP
    `default-src 'none'; frame-ancestors 'none'` (Swagger UI в dev исключён).
    HSTS выставляется только на настоящем TLS (scheme https или доверенный
    `X-Forwarded-Proto`), на http-localhost — никогда. Опциональный
    `CSP_REPORT_ONLY=1` переводит фронтовую политику в режим наблюдения без
    внешнего report-uri.

### Бизнес-логика и кэш

11. [Закрыто] Мутации линий/станций/import инвалидируют `routes` и `schedules`.
12. [Закрыто] Операционный календарь использует `Asia/Dushanbe`.
13. [Закрыто] Auto day type не попадает в междатный cache без resolved day type.
14. [Закрыто] Active alerts больше не кэшируются через временные границы.
15. [Закрыто] Проверка последнего superadmin использует pessimistic write lock
    всех активных владельцев роли и сериализует параллельные update/delete.
16. Holiday schedule seed есть только для L1; L2 в праздник остаётся без графика.
17. Feature flags в основном декоративные: из заявленных флагов реально
    применяется только `import.async`.
18. `import.async` использует недолговечную in-process очередь без lease,
    recovery, retry/idempotency и обработки переполнения.

### Frontend и интеграция

19. [Закрыто для текущего backend-контракта] Admin отправляет исходный `File` в
    bounded Route Handler; глобальный 16 MB Server Actions limit удалён. Полный
    streaming потребует заменить Java `String`/`byte[]` controller contract.
20. [Закрыто] Route planner инвалидирует старый A→B результат при смене маршрута.
21. Закрыто: динамическая новость теперь имеет настоящий 404, metadata/OG и не
    маскирует backend outage под «не найдено».
22. [Закрыто] Admin fetch/login/SSR запросы получили timeout.
23. [Закрыто] Demo seed-миграции больше не применяются в production. Шесть seed-миграций
    (V002, V004, V006, V008, V012, V016) перенесены из `db/migration` в отдельный каталог
    `db/seed` без изменения версий и содержимого, поэтому контрольные суммы прежние и
    существующие dev/демо-базы проходят `flyway validate` без `repair`. `application.yml`
    (dev/тесты/демо) включает оба каталога, `application-prod.yml` — только `db/migration`.
    Демо-тарифы `DEMO-SINGLE`/`DEMO-MONTHLY`, вставленные прямо в схемную V018, гасятся
    новой V026 из `db/migration` и возвращаются в активное состояние только V027 из
    `db/seed`. Режим данных виден на старте: `DemoSeedGuard` пишет
    `Данные: ДЕМОНСТРАЦИОННЫЕ` / `Данные: только реальные` и в профиле `prod` не даёт
    приложению подняться, если seed-каталог всё-таки попал в `spring.flyway.locations`.
    Следствие, которое нужно закрыть операционно: чистая production-база стартует без
    сети, новостей, расписаний, календаря праздников и активных тарифов — их вводят
    через admin-консоль и конвейер импорта.

## P2 — существенные ошибки и технический долг

24. Arrival для всех станций линии строится одной сеткой без направления,
    позиции станции и времени хода; это не реальные прибытия.
25. [Закрыто] `resolved → closed` больше не стирает ранее сохранённый ответ.
26. `assignedTo` обращения — произвольная строка; incident проверяет существование,
    но не активность оператора.
27. Incident принимает несуществующие line/station codes.
28. Генератор `INC-YYYY-NNNN` через `MAX(code)` имеет race, после 9999 нарушает
    лексикографический порядок, конфликт превращается в 500.
29. Импорт перепривязывает станцию неатомарно; сбой способен оставить частичные связи.
30. Неожиданная ошибка импорта не создаёт `ImportError` с безопасной причиной/requestId.
31. [Закрыто] Геометрия проверяет finite values и диапазоны WGS84 lon/lat.
32. DTO часто не ограничивают длину по размеру колонок БД; PostgreSQL exception
    превращается в 500 вместо 400/409.
33. Calendar exception CRUD не пишет audit и не очищает schedule cache.
34. [Закрыто] Production admin Docker image копирует `public/`.
35. Runtime-валидация TypeScript API контрактов неполная; битые ответы иногда
    кастуются, отбрасываются частично или превращаются в координаты `[0,0]`.
36. Offline routing выводит порядок станций из ближайшей точки LineString вместо
    канонического `positionIndex`/ordered stops.
37. Offline holiday resolver фактически не умеет определить праздник.
38. Service worker использует ручную cache version, не связанную со сборкой;
    нет ревизионного precache и PNG icons 192/512.
39. Tabs, combobox, dropdown и modal focus management не полностью соответствуют
    WAI-ARIA keyboard patterns.
40. SSR всегда начинает с таджикского языка, затем читает localStorage: языковой
    flash, неверные metadata/hreflang и индексация.
41. Root `error.tsx`, `loading.tsx`, `not-found.tsx` добавлены. Нет `global-error.tsx`,
    локализации аварийных экранов и frontend unit/E2E/axe tests.
42. Martin DATABASE_URL hardcodes `metro:metro`, игнорируя Compose DB credentials.
43. MinIO/Martin задекларированы как готовая часть контура, но приложение их не использует.
44. Images MinIO/Martin используют `latest`, нет digest pin/SBOM/image scanning.
45. CI теперь запускает `verify`, JaCoCo и dependency-check; OWASP threshold — 9.
    Coverage threshold всё ещё отсутствует, а PMD/CPD при 364/5 findings остаются
    report-only из-за `failOnViolation=false`.
46. Нет production monitoring/tracing/log shipping/SLO alerts/backup/PITR/restore drill.

## P3 — качество и сопровождение

47. Rate-limit 429 не соответствует общему `ApiError` и не содержит requestId.
48. Request ID почти не ограничен по длине; возможно раздувание логов/ответов.
49. Audit append-only обеспечен соглашением, не DB permissions/trigger.
50. [Закрыто] Server-only admin backend использует `ADMIN_API_BASE` и
    `import "server-only"`.
51. API transport/schemas/timeouts дублируются; нужны generated OpenAPI client и
    единые runtime schemas.
52. Огромные i18n, map/manager и routing service файлы затрудняют тестирование.
53. Документация устарела: число миграций/тестов, старые ADMIN_UI_* env и пароль.
54. Три копии demo network совпадают сейчас, но CI не проверяет синхронизацию с seed SQL.
55. CI дублирует cache setup; Actions не pin по commit SHA.
56. Toast timers не очищаются, некоторые React list keys используют index.
57. Нет table-driven теста полной RBAC route/method matrix и реального OIDC chain.
58. Новый `packages/design/tokens.mjs` первоначально содержал преждевременные
    `*/` в комментариях; текущий файл уже синтаксически исправлен.
59. Закрыто для текущего дерева: `tokens.mjs`/`sync.mjs` синтаксически валидны,
    migration guide/generated markers и CI drift-check присутствуют.
60. Новые notification/ticketing/integration модули появились во время аудита;
    до отдельной проверки их нельзя считать завершёнными функциями продукта.
61. Прикладной SSRF-контур закрыт частично: V024 и DTO теперь HTTPS-only, URL/DNS/IP
    проверяются при сохранении и перед dispatch, redirects запрещены. Для абсолютной
    защиты от DNS rebinding всё ещё нужны exact-host allowlist и outbound egress policy.
62. Webhook `secret_hash` используется как HMAC key: утечка БД позволяет
    подделывать подписи. Нужен зашифрованный/KMS signing key с key-id и ротацией.
63. [Частично закрыто] Immutable payload/event/aggregate/trace snapshot хранится в
    delivery, поэтому outbox retention не ломает retry. URL и signing key намеренно
    берутся из текущей активной subscription для немедленного revoke/rotation;
    KMS key-id/version остаётся внешней security-задачей из пункта 62.
64. [Закрыто] Notification использует `FOR UPDATE SKIP LOCKED`, webhook — короткий
    atomic claim/lease с optimistic version и recovery просроченного claim.
65. [Закрыто] Добавлены unique message/channel/recipient, attempts и строгие
    state/timestamp CHECK constraints; те же переходы защищены в domain entity.
66. Исправлено в service/repository: validation и refund используют pessimistic locks
    с порядком `Ticket → Payment`. Реальный двухтранзакционный PostgreSQL concurrency
    test ещё нужен, потому что Docker daemon недоступен.
67. Для текущего full-refund продукта добавлены unique active refund per payment,
    обязательный provider reference и row locks. Внешний idempotency key, durable
    provider intent и reconciliation после «provider success / DB rollback» ещё нужны.
68. [Закрыто] Ticket/Payment/Refund/Notification/Webhook защищают transitions,
    ссылки/причины/время/claim-инварианты внутри агрегатов и на уровне БД.
69. Latest train-position query может показывать старую линию после перехода и
    не имеет freshness cutoff; telemetry не имеет retention/partitioning.
70. Новые модули покрыты unit/domain-проверками, multi-instance claim/lease и
    immutable webhook snapshot. До production-ready всё ещё не хватает платёжной
    reconciliation, PostgreSQL concurrency/Flyway и полноценных E2E tests.

## Продуктовые ограничения, не являющиеся локальным багом

- Нет утверждённых официальных трасс, тарифов и master-data.
- Нет realtime/диспетчерского feed, поэтому arrivals остаются оценочными.
- Нет AFC/платёжного контура и утверждённых правил продажи.
- Нет production IdP/MFA/гос-ID параметров и эксплуатационных SLO.
- Flutter-клиент остаётся отдельной продуктовой фазой.

## Порядок исправления

1. Flyway и fail-closed production secrets/bootstrap.
2. Рабочая end-to-end OIDC/JWT модель и session/actor binding.
3. Redirect loop, frontend/backend RBAC parity, rate limiting.
4. Cache/date/time correctness и критические workflow race/data-loss ошибки.
5. Streaming import, API timeouts/contracts, Docker/CI hardening.
6. PWA/a11y/i18n/SEO, observability и документация.
