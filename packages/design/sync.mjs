#!/usr/bin/env node
/**
 * Синхронизация общего слоя дизайна в оба приложения.
 *
 * ЗАЧЕМ ИМЕННО ТАК. web/Dockerfile и admin/Dockerfile собираются с контекстом
 * ../web и ../admin (infra/docker-compose.full.yml). Из контекста web/ каталоги
 * packages/ и admin/ НЕ ВИДНЫ: `COPY . .` их не заберёт, symlink наружу контекста
 * Docker не разыменует, а npm-workspace-пакет `npm ci` внутри контейнера не найдёт.
 * Поэтому общий слой не «подключается», а РАСКЛАДЫВАЕТСЯ по приложениям и
 * коммитится: в git — обычные файлы внутри web/src и admin/src, которые Docker
 * забирает штатным `COPY . .`. Dockerfile'ы и контексты сборки менять не нужно.
 *
 * Что делает:
 *   1) переписывает блок токенов между маркерами в <app>/src/app/globals.css;
 *   2) копирует packages/design/shared/** в <app>/src/shared/**.
 *
 * Использование:
 *   node packages/design/sync.mjs          # записать
 *   node packages/design/sync.mjs --check  # проверить синхронность (для CI)
 *
 * ВАЖНО: сгенерированные файлы править нельзя — правки затрутся. Источник —
 * packages/design/. Файлы вне маркеров в globals.css правятся свободно.
 */

import { readFile, writeFile, mkdir, readdir, rm } from "node:fs/promises";
import { existsSync } from "node:fs";
import { dirname, join, relative } from "node:path";
import { fileURLToPath } from "node:url";
import { APPS, renderCss } from "./tokens.mjs";

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = join(HERE, "..", "..");
const SHARED_SRC = join(HERE, "shared");

const BEGIN = "/* >>> НАЧАЛО СГЕНЕРИРОВАННОГО БЛОКА — packages/design/tokens.mjs";
const BEGIN_TAIL = "Правки здесь затрутся: меняйте packages/design/tokens.mjs и";
const BEGIN_TAIL2 = "запускайте `node packages/design/sync.mjs`. <<< */";
const END = "/* >>> КОНЕЦ СГЕНЕРИРОВАННОГО БЛОКА <<< */";

const BANNER = `${BEGIN}\n   ${BEGIN_TAIL}\n   ${BEGIN_TAIL2}`;

const check = process.argv.includes("--check");
/** Накопленные расхождения — в --check печатаются все сразу, а не только первое. */
const problems = [];

/** Заголовок для сгенерированных .tsx/.ts — виден в редакторе и в code review. */
function tsBanner(sourceRel) {
  return `// СГЕНЕРИРОВАНО: packages/design/sync.mjs — НЕ РЕДАКТИРОВАТЬ.
// Источник: ${sourceRel}
// Изменения вносите в источник и запускайте: node packages/design/sync.mjs
`;
}

/** Пишет файл или, в режиме --check, сверяет содержимое. */
async function emit(absPath, content) {
  const rel = relative(ROOT, absPath).replaceAll("\\", "/");
  const current = existsSync(absPath) ? await readFile(absPath, "utf8") : null;
  // Сравнение с нормализацией переводов строк: репозиторий читается и на
  // Windows (CRLF после checkout), и в alpine-контейнере — иначе --check
  // «падал» бы на каждой машине с другим core.autocrlf.
  if (current !== null && current.replaceAll("\r\n", "\n") === content.replaceAll("\r\n", "\n")) {
    return;
  }
  if (check) {
    problems.push(rel);
    return;
  }
  await mkdir(dirname(absPath), { recursive: true });
  await writeFile(absPath, content, "utf8");
  console.log(`  записан ${rel}`);
}

/** Врезает сгенерированный блок токенов в globals.css приложения. */
async function syncCss(app) {
  const cssPath = join(ROOT, app, "src", "app", "globals.css");
  const source = await readFile(cssPath, "utf8");
  const block = `${BANNER}\n${renderCss(app)}\n${END}`;

  const beginIdx = source.indexOf(BEGIN);
  const endIdx = source.indexOf(END);
  if (beginIdx === -1 || endIdx === -1) {
    throw new Error(
      `В ${app}/src/app/globals.css нет маркеров сгенерированного блока. ` +
        `Добавьте их вручную один раз:\n${BEGIN} ... ${END}`,
    );
  }
  const next =
    source.slice(0, beginIdx) + block + source.slice(endIdx + END.length);
  await emit(cssPath, next);
}

/** Рекурсивно копирует shared/** в <app>/src/shared/**. */
async function syncShared(app) {
  const destRoot = join(ROOT, app, "src", "shared");

  // Удаляем то, чего больше нет в источнике: иначе переименованный примитив
  // остался бы «призраком» в приложении и продолжал собираться.
  if (!check && existsSync(destRoot)) {
    await rm(destRoot, { recursive: true, force: true });
  }

  async function walk(dir) {
    const entries = await readdir(dir, { withFileTypes: true });
    for (const entry of entries) {
      const abs = join(dir, entry.name);
      if (entry.isDirectory()) {
        await walk(abs);
        continue;
      }
      const rel = relative(SHARED_SRC, abs);
      const sourceRel = `packages/design/shared/${rel.replaceAll("\\", "/")}`;
      const body = await readFile(abs, "utf8");
      // "use client" обязана остаться ПЕРВОЙ строкой файла — иначе директива
      // не действует и компонент молча станет серверным. Поэтому баннер
      // вставляется после неё, а не в начало.
      const useClient = /^"use client";\r?\n/.exec(body);
      const content = useClient
        ? `${useClient[0]}\n${tsBanner(sourceRel)}${body.slice(useClient[0].length).replace(/^\n/, "")}`
        : `${tsBanner(sourceRel)}\n${body}`;
      await emit(join(destRoot, rel), content);
    }
  }

  await walk(SHARED_SRC);
}

for (const app of APPS) {
  if (!check) console.log(`[${app}]`);
  await syncCss(app);
  await syncShared(app);
}

if (check && problems.length > 0) {
  console.error(
    "Общий слой дизайна рассинхронизирован. Не совпадают:\n" +
      problems.map((p) => `  - ${p}`).join("\n") +
      "\n\nЗапустите: node packages/design/sync.mjs",
  );
  process.exit(1);
}

console.log(check ? "Общий слой синхронизирован." : "Готово.");
