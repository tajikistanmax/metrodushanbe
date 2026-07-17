# web — публичный портал «Метро Душанбе»

Next.js App Router + TypeScript + Tailwind + MapLibre GL. Порт разработки: `3000`.

## Маршруты

| URL | Назначение |
|---|---|
| `/` | карта и list-mode сети |
| `/route` | построение маршрута |
| `/news` | новости |
| `/requests` | создание и отслеживание обращения |
| `/fares` | тарифы и проездные |
| `/offline` | offline shell PWA |

Карточка станции на карте показывает линии, доступность и ближайшие отправления.

## Запуск и проверка

```bash
npm ci
npm run dev

npm run lint
npm run build
```

`NEXT_PUBLIC_API_BASE` по умолчанию равен `http://localhost:8080/api/v1`.

## Offline/PWA

Портал регистрирует service worker и кэширует app shell и успешные публичные GET JSON.
Если API или сеть недоступны:

- карта использует `public/data/demo-network.geojson`;
- тарифы и расписание используют явно обозначенные fallback-данные;
- offline shell остаётся доступным;
- формы не имитируют успешную отправку без backend.

Demo GeoJSON синхронизирован с `data/demo-network.geojson` в корне монорепозитория.

## i18n и время

Языки: `tg` по умолчанию, `ru`, `en`. Выбор хранится в `localStorage`, а `<html lang>`
обновляется. Даты и время форматируются в `Asia/Dushanbe` детерминированно, чтобы SSR и
браузер не расходились из-за разных ICU locale fallback.

## Дизайн и доступность

- Montserrat подключён self-host через `@fontsource`, внешние font/CDN не нужны;
- светлая, тёмная и системная темы;
- skip links, keyboard focus, list-mode карты и `prefers-reduced-motion`;
- адаптивная горизонтальная навигация без видимой системной полосы прокрутки;
- TG/RU/EN тексты и `aria`-подписи для основных сценариев.

## Проверенный пользовательский поток

Production-сборка проверена в desktop и mobile viewport:

1. открыть тарифы и получить данные API;
2. создать обращение с согласием;
3. сохранить выданные номер и tracking token;
4. открыть вкладку отслеживания и получить статус;
5. убедиться в отсутствии hydration mismatch и console errors.
