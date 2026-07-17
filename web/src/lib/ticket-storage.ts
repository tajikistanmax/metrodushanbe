/**
 * Локальный реестр билетов пассажира (экран «мои билеты»).
 *
 * ЭТО УСТРОЙСТВО, А НЕ АККАУНТ. Контура идентификации пассажиров в системе нет
 * (внешний блокер), поэтому «мои билеты» — это ровно то, что лежит в
 * localStorage этого браузера. Здесь нет и не может быть личного кабинета:
 * другой браузер, другое устройство или очистка данных — другой список. Всё, что
 * этот модуль обещает, — не потерять уже сохранённое.
 *
 * ФОРМАТ. Исторически билет хранился одним ключом на билет:
 *
 *     metro-dushanbe.ticket.{CODE} -> одноразовый токен
 *
 * Списка при этом не было: пассажир мог открыть билет, только вспомнив номер.
 * Реестр (REGISTRY_KEY) добавлен СВЕРХУ, а не вместо: токены остаются лежать по
 * прежним ключам и не переписываются. Билеты, купленные до появления реестра,
 * подбираются сканированием ключей с префиксом TOKEN_PREFIX и доливаются в
 * реестр (savedAt у них неизвестен — врать датой покупки нельзя). Так старые
 * оплаченные билеты попадают в список без разрушающей миграции: даже если реестр
 * не прочитается или будет затёрт, билеты никуда не денутся — их источник истины
 * по-прежнему сами ключи токенов.
 *
 * Токен — одноразовый секрет, и в списке он не показывается: список ходит за
 * статусом по коду билета (GET /v1/tickets/{code}), токен нужен только для
 * проверки билета «как на турникете».
 */

/** Префикс ключа токена. Исторический формат, менять нельзя. */
const TOKEN_PREFIX = "metro-dushanbe.ticket.";

/**
 * Ключ реестра. Намеренно НЕ начинается с TOKEN_PREFIX: иначе сканирование
 * старых ключей приняло бы сам реестр за билет с кодом «S.INDEX».
 */
const REGISTRY_KEY = "metro-dushanbe.my-tickets";

/** Билет, сохранённый на этом устройстве. Токена здесь нет намеренно. */
export type SavedTicket = {
  /** Код билета — единственное, что показывается пассажиру в списке. */
  code: string;
  /** Когда билет сохранён, ISO-8601; null — билет из старого формата. */
  savedAt: string | null;
  /** Сохранён ли токен: без него проверка билета недоступна. */
  hasToken: boolean;
};

type RegistryEntry = { code: string; savedAt: string | null };

/** Коды билетов регистронезависимы; ключи храним в верхнем регистре. */
function normalizeCode(code: string): string {
  return code.trim().toUpperCase();
}

/** Ключ токена билета. */
export function tokenStorageKey(code: string): string {
  return `${TOKEN_PREFIX}${normalizeCode(code)}`;
}

/**
 * Реестр из localStorage. Любая порча содержимого — не повод падать: билеты всё
 * равно будут найдены сканированием ключей токенов.
 */
function readRegistry(): RegistryEntry[] {
  try {
    const raw = window.localStorage.getItem(REGISTRY_KEY);
    if (!raw) return [];
    const parsed: unknown = JSON.parse(raw);
    if (!Array.isArray(parsed)) return [];
    return parsed.flatMap((item) => {
      if (typeof item !== "object" || item === null) return [];
      const entry = item as { code?: unknown; savedAt?: unknown };
      if (typeof entry.code !== "string" || entry.code.trim() === "") return [];
      return [
        {
          code: normalizeCode(entry.code),
          savedAt: typeof entry.savedAt === "string" ? entry.savedAt : null,
        },
      ];
    });
  } catch {
    // Приватный режим или битый JSON — работаем по ключам токенов
    return [];
  }
}

function writeRegistry(entries: readonly RegistryEntry[]): void {
  try {
    window.localStorage.setItem(REGISTRY_KEY, JSON.stringify(entries));
  } catch {
    // Хранилище недоступно: список останется на время сессии, но не переживёт
    // перезагрузку. Молчаливо — это не ошибка пассажира.
  }
}

/** Есть ли сохранённый токен билета. */
function hasToken(code: string): boolean {
  try {
    return window.localStorage.getItem(tokenStorageKey(code)) !== null;
  } catch {
    return false;
  }
}

/**
 * Билеты этого устройства: реестр плюс билеты старого формата, найденные по
 * ключам токенов. Найденное дописывается в реестр, но токены не трогаются.
 *
 * Порядок: сначала недавно сохранённые, билеты без даты (старый формат) —
 * в конце, по коду.
 */
export function listSavedTickets(): SavedTicket[] {
  const known = new Map<string, string | null>();
  for (const entry of readRegistry()) {
    known.set(entry.code, entry.savedAt);
  }

  let discovered = false;
  try {
    for (let index = 0; index < window.localStorage.length; index += 1) {
      const key = window.localStorage.key(index);
      if (key === null || !key.startsWith(TOKEN_PREFIX)) continue;
      const code = key.slice(TOKEN_PREFIX.length);
      if (code === "" || known.has(code)) continue;
      // Дату покупки взять неоткуда: старый формат её не хранил
      known.set(code, null);
      discovered = true;
    }
  } catch {
    // Хранилище недоступно — список пуст, экран объяснит это пассажиру
  }

  const entries: RegistryEntry[] = [...known].map(([code, savedAt]) => ({ code, savedAt }));
  entries.sort((a, b) => {
    if (a.savedAt !== null && b.savedAt !== null) return b.savedAt.localeCompare(a.savedAt);
    if (a.savedAt !== null) return -1;
    if (b.savedAt !== null) return 1;
    return a.code.localeCompare(b.code);
  });

  // Билеты старого формата закрепляем в реестре, чтобы список был стабильным
  if (discovered) writeRegistry(entries);

  return entries.map((entry) => ({ ...entry, hasToken: hasToken(entry.code) }));
}

/**
 * Запомнить купленный билет: токен по прежнему ключу плюс запись в реестре.
 * Хранение необязательно — в приватном режиме остаётся одноразовый показ токена
 * на карточке покупки.
 */
export function rememberTicket(code: string, token: string): void {
  const normalized = normalizeCode(code);
  try {
    window.localStorage.setItem(tokenStorageKey(normalized), token);
  } catch {
    // Токен не сохранён; в реестр билет всё равно добавим — статус по коду
    // посмотреть можно и без токена
  }
  const entries = readRegistry().filter((entry) => entry.code !== normalized);
  writeRegistry([{ code: normalized, savedAt: new Date().toISOString() }, ...entries]);
}

/**
 * Убрать билет с устройства. ЭТО НЕ ВОЗВРАТ: билет остаётся выпущенным на
 * сервере, деньги (в демо — имитированные) не двигаются, а вместе с записью
 * теряется одноразовый токен. Возврат оформляется отдельно, на вкладке возврата.
 */
export function forgetTicket(code: string): void {
  const normalized = normalizeCode(code);
  try {
    window.localStorage.removeItem(tokenStorageKey(normalized));
  } catch {
    // Хранилище недоступно — удалять нечего
  }
  writeRegistry(readRegistry().filter((entry) => entry.code !== normalized));
}

/** Сохранённый токен билета; null — токена нет (приватный режим, старое устройство). */
export function readTicketToken(code: string): string | null {
  try {
    return window.localStorage.getItem(tokenStorageKey(code));
  } catch {
    return null;
  }
}
