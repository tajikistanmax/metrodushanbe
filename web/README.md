# web — публичный портал «Метро Душанбе»

Next.js (App Router) + TypeScript + Tailwind + MapLibre GL. Порт dev-сервера: **3000** (см. `docs/dev-conventions.md`, §2).

## Запуск (dev)

```bash
npm install
npm run dev       # http://localhost:3000
```

Проверки качества:

```bash
npm run build     # прод-сборка, обязана проходить
npm run lint      # ESLint
```

## Переменные окружения

Скопируйте `.env.example` в `.env.local` при необходимости:

| Переменная | Значение по умолчанию | Назначение |
|---|---|---|
| `NEXT_PUBLIC_API_BASE` | `http://localhost:8080/api/v1` | База API бэкенда |

## Офлайн-принцип (dev-conventions.md, §8)

Портал деградирует без сети и без бэкенда:

- при старте запрашивается `GET {NEXT_PUBLIC_API_BASE}/network/geojson` с таймаутом 2 с;
- при любой ошибке используется бандл-копия демо-данных `public/data/demo-network.geojson` (канонический источник — `data/demo-network.geojson` в корне монорепозитория, копируется без изменений);
- индикатор под картой показывает фактический источник: «API» или «демо (офлайн)»;
- карта не использует внешние тайлы, глифы и CDN: стиль — background-слой + GeoJSON-слои; подписи станций — Popup по клику; шрифты — системные (Montserrat будет добавлен self-host позже).

## i18n

Языки: `tg` (по умолчанию), `ru`, `en` — словари в `src/lib/i18n.ts`, без сторонних библиотек. Выбор языка сохраняется в `localStorage`, `<html lang>` обновляется. Названия станций/линий берутся из `name[lang]` с фолбэком tg → ru → en.

## Доступность

Skip-link к списку станций, list-mode для карты (A11Y-06), фокус-стили `:focus-visible`, `prefers-reduced-motion` отключает анимацию flyTo, контраст по брендовой палитре.
