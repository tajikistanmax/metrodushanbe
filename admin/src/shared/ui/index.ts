// СГЕНЕРИРОВАНО: packages/design/sync.mjs — НЕ РЕДАКТИРОВАТЬ.
// Источник: packages/design/shared/ui/index.ts
// Изменения вносите в источник и запускайте: node packages/design/sync.mjs

/**
 * Публичный API слоя UI-примитивов.
 *
 * Импорт в приложениях — из «@/shared/ui»:
 *   import { Button, Card, Badge } from "@/shared/ui";
 */

export { default as Button } from "./Button";
export type { ButtonVariant, ButtonSize } from "./Button";

export { default as Card } from "./Card";
export type { CardPadding, CardElevation } from "./Card";

export { default as Badge } from "./Badge";
export type { BadgeTone } from "./Badge";

export { default as Alert } from "./Alert";
export type { AlertTone } from "./Alert";

export { Field, Input, Textarea, Select, Checkbox } from "./Field";

export { Container, Stack } from "./Layout";
export type {
  ContainerWidth,
  StackGap,
  StackAlign,
  StackJustify,
} from "./Layout";
