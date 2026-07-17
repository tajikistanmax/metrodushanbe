# Миграция на общий слой дизайна

Фундамент институционального редизайна. Старые имена **продолжают работать** —
это сделано намеренно, чтобы редизайн шёл экран за экраном, а не одним
нерабочим коммитом. Ничего из перечисленного не нужно чинить «прямо сейчас».

## Как устроено

```
packages/design/
├─ tokens.mjs      ← ЕДИНСТВЕННЫЙ источник токенов
├─ sync.mjs        ← раскладывает всё по приложениям
├─ shared/         ← ЕДИНСТВЕННЫЙ источник общих компонентов
│  ├─ ThemeProvider.tsx  I18nProvider.tsx  ThemeToggle.tsx
│  ├─ BrandMark.tsx      LangSwitcher.tsx  theme-init.ts
│  └─ ui/          ← примитивы: Button, Card, Field, Badge, Alert, Layout
└─ MIGRATION.md
```

`node packages/design/sync.mjs` записывает:

| Источник | Приёмник |
|---|---|
| `tokens.mjs` | блок между маркерами в `web/src/app/globals.css` и `admin/src/app/globals.css` |
| `shared/**` | `web/src/shared/**` и `admin/src/shared/**` |

> **Сгенерированные файлы править нельзя** — правки затрутся при следующем
> `sync.mjs`. Меняйте источник в `packages/design/`.
> Файлы **вне маркеров** в `globals.css` — обычные, правятся свободно.
>
> После правки токенов: `node packages/design/sync.mjs`
> Проверка синхронности (CI): `node packages/design/sync.mjs --check`

**Почему генерация, а не workspaces.** `web/Dockerfile` и `admin/Dockerfile`
собираются с контекстом `../web` и `../admin` (`infra/docker-compose.full.yml`).
Из контекста `web/` каталог `packages/` не виден: `COPY . .` его не заберёт,
symlink наружу контекста Docker не разыменует, workspace-пакет `npm ci` внутри
контейнера не найдёт. Генерация раскладывает общий слой в обычные файлы внутри
каждого приложения — Docker забирает их штатно, менять Dockerfile не нужно.

---

## Таблица миграции: старое имя → новое

Все старые имена оставлены **алиасами** и работают. Колонка «Когда» — что делать
при следующем касании файла.

### Поверхности: web `--panel-*` и admin `--card-*` → единый словарь

| Было (web) | Было (admin) | Стало | Примечание |
|---|---|---|---|
| `--page-bg` | `--page-bg` | `--surface-page` | фон страницы |
| `--panel-bg` | `--card-bg` | `--surface-raised` | **см. врезку ниже** |
| `--panel-border` | `--card-border` | `--border-subtle` | 1px граница |
| — | `--table-border` | `--border-subtle` | было три имени одного смысла |
| — | `--table-head-bg` | `--surface-sunken` | |
| `--control-hover` | — | `--surface-hover` | ховер контрола (0.07) |
| — | `--table-row-hover` | `--surface-hover-subtle` | ховер строки (0.04) |
| `--popup-bg` | — | `--surface-overlay` | |
| `--field-bg` | — | `--surface-raised` | |
| `--map-bg` | — | `--surface-page` | было равно `--page-bg` |
| — | `--chip-bg` | `--surface-chip` | |
| `--shadow-card` | `--shadow-card` | `--elevation-raised` | **значения разъезжались, см. ниже** |
| — | `--kpi-tint-navy` | `--tint-neutral` | |
| — | `--kpi-tint-red` | `--tint-critical` | |
| — | `--kpi-tint-green` | `--tint-success` | |
| — | `--kpi-tint-info` | `--tint-info` | |
| — | `--kpi-tint-warning` | `--tint-warning` | |

> **`--panel-bg` → `--surface-glass`, а НЕ `--surface-raised`.**
> Это единственное место, где старое и новое имя разошлись по смыслу.
> У web `--panel-bg` было `rgba(255,255,255,.88)` — полупрозрачное «стекло»
> для панелей **поверх карты**; у admin `--card-bg` было непрозрачным `#fff`.
> Свести их в одно значение — значит либо потерять стекло на карте, либо сделать
> все карточки консоли полупрозрачными. Поэтому: алиас `--panel-bg` указывает на
> `--surface-glass` (вид портала сохранён 1:1), а для обычных карточек, которые
> не лежат на карте, при редизайне переходите на `--surface-raised`.

### Магические цвета → токены

| Было | Где | Стало |
|---|---|---|
| `#4ade80` | `web/…/Header.tsx` | `--status-ok` ✅ *(уже заменено)* |
| `#9fb3c8` | `web/…/Header.tsx` | `--status-idle` ✅ *(уже заменено)* |
| `#8aa1b4` | `admin/…/StatusBadge.tsx` | `--status-neutral` ✅ *(уже заменено)* |
| `#6b7785` | `admin/…/StatusBadge.tsx` | `--status-retired` ✅ *(уже заменено)* |
| `#E21B2D` | `BrandMark.tsx` | `var(--brand-red)` ✅ *(уже заменено)* |
| ~20 сырых hex | `admin/…/TajikistanOverviewMap.tsx` | `--map-land-*`, `--map-outline`, `--map-river`, `--map-grid` ✅ *(редизайн консоли)* |
| палитра входа | `admin/…/login/LoginClient.tsx` | `--login-*` ✅ *(редизайн консоли)* |
| флаговые hex | `admin/…/login/LoginClient.tsx` | таблица `FLAG` в самом файле — **намеренно не токены**: цвет чужого флага не решение о теме платформы |

### Радиусы

| Было | Стало | Утилита |
|---|---|---|
| `rounded-lg` (8) на карточках | `--corner-panel` (8) | `rounded-panel` |
| `rounded-xl` (12), `rounded-2xl` (16), `rounded-[22px]`, `rounded-[2rem]` | `--corner-panel` (8) | `rounded-panel` |
| `rounded-lg` (8) на кнопках/полях, `7px`, `14px` | `--corner-control` (4) | `rounded-control` |
| бейджи, теги | `--corner-chip` (2) | `rounded-chip` |
| полосы во всю ширину | `--corner-flat` (0) | `rounded-flat` |
| точки, аватары | `--corner-pill` | `rounded-full` |

Встроенные `rounded-sm/md/lg/xl/2xl` **не переопределены** намеренно: это молча
изменило бы вид ~60 файлов. Старая разметка живёт, новая пишется ролевыми именами.

### Типографика

| Было | Стало | Утилита |
|---|---|---|
| `text-[9px]`, `text-[10px]` (×31), `text-[11px]` (×24) | 12px | `text-caption` |
| `text-xs`/`text-[13px]` | 14px | `text-small` |
| `text-sm`/`text-base` | 16px | `text-body` |
| — | 19px | `text-lead` |
| `text-[20px]` | 20px | `text-title-s` |
| `text-2xl` (24) — максимум портала | 24px | `text-title-m` |
| `text-[28px]` | 32px | `text-title-l` |
| — | 40px | `text-title-xl` |
| `text-[42px]` | 48px | `text-display` |

Веса — только загруженные: `font-normal` (400) · `font-semibold` (600) ·
`font-bold` (700) · `font-extrabold` (800).
**`font-medium` (500) использовать нельзя** — вес не загружен через
`@fontsource/montserrat`, браузер синтезирует его или отдаёт 400.

---

## Что осталось сделать руками

Это фундамент; сам редизайн — поверх него.

> **Консоль (`admin/`) — редизайн выполнен.** Пункты 1, 2, 6 закрыты, 3 и 4 —
> закрыты в части `admin/`. Алиасы консоли (`--card-bg`, `--table-*`,
> `--chip-bg`, `--shadow-card`, `--kpi-tint-*`, `--page-bg`) и её theme-ключи
> (`--color-card`, `--color-sidebar`, `--color-page`) **удалены из `tokens.mjs`**:
> условие «grep не находит старое имя» выполнено. Алиасы портала не тронуты.

1. ~~`TajikistanOverviewMap.tsx` — ~20 сырых hex.~~ ✅ Переведён на `--map-land-*`,
   `--map-outline`, `--map-river`, `--map-grid` (добавлены в `adminExtra*`,
   с тёмным вариантом: раньше карта оставалась светлым пятном на `#0b1826`).
2. ~~`LoginClient.tsx` — собственная палитра.~~ ✅ Палитра вынесена в `--login-*`.
   Экран **осознанно остаётся вне общей темы** (карточка всегда светлая, CTA
   синий `#2563eb`, радиус карточки 28px) — так утверждён фото-макет.
3. **`font-medium`** — в `admin/` не осталось (проверка: `font-weight:500` нет и
   в собранном CSS). В портале ещё есть: `web/…/SearchBox.tsx` (в `placeholder:`),
   `web/…/StationPanel.tsx`.
4. **Перевод экранов на примитивы.** В `admin/` выполнено: `Button`, `Card`,
   `Badge`, `Alert` подключены, сырых кнопок-копий не осталось (сырой `<button>`
   сохранён только там, где нужна ARIA-семантика, которую примитив не
   пробрасывает: `role="tab"`, `aria-pressed` у чипов-фильтров, `role="listbox"`).
   **Карточки портала всё ещё скопированы по 6 файлам** — это задача редизайна `web/`.
5. **`admin/src/components/admin/fields.tsx`** оставлен как есть: он завязан на
   `@/lib/admin-forms` (`I18nInput`) и словарь консоли, т.е. непереносим в общий
   слой без разрыва этих связей. Общий `@/shared/ui/Field` — для новых форм и для
   портала; формы консоли мигрировать по мере надобности, API у них совместим.
   Приведён к общему виду только визуально (радиусы, шкала, `Button`, `Alert`).
6. ~~`.console-card`~~ ✅ Класс удалён из `admin/src/app/globals.css`, все 22
   употребления переведены на `<Card>` либо на те же токены напрямую (там, где
   нужен свой `overflow`/ARIA: `DataTable`, поповеры `Topbar`).
7. **`--shadow-card` стал заметно слабее.** Было `0 8px 32px rgba(8,39,66,.18)`
   (web) / `0 6px 24px rgba(8,39,66,.1)` (admin) → стало `0 1px 2px rgba(8,39,66,.08)`.
   Это намеренный шаг к институциональному виду (плоскости разделяет граница), но
   он **меняет внешний вид уже существующих карточек**. Если где-то нужен прежний
   «висящий» вид — это `--elevation-overlay`, и только для того, что реально висит
   над контентом.
8. **CI-проверка синхронности не подключена.** Добавьте в пайплайн
   `node packages/design/sync.mjs --check` — иначе источник и приёмники разъедутся
   ровно так же, как разъехались два `:root` до этой работы.
