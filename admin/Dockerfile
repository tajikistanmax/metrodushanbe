# Админ-панель «Метро Душанбе» (Next.js 16, standalone output).
# Multi-stage: npm ci → next build → минимальный node-рантайм (без node_modules).
# Контекст сборки — каталог admin/:
#   docker build -f admin/Dockerfile -t metro-admin admin
#
# Особенности admin:
#  - слушает порт 3001 (dev-скрипты и рантайм согласованы);
#  - данные тянет ТОЛЬКО серверно (Server Components/Actions), поэтому база API по
#    умолчанию — внутреннее имя сервиса backend в compose-сети;
#  - секрет X-Admin-Key (ADMIN_API_KEY) читается СЕРВЕРНО В РАНТАЙМЕ (не NEXT_PUBLIC_),
#    поэтому передаётся через env контейнера, а не через build-arg.

# ---- Stage 1: зависимости ----
FROM node:24-alpine AS deps
WORKDIR /app
RUN apk add --no-cache libc6-compat
COPY package.json package-lock.json ./
RUN npm ci

# ---- Stage 2: сборка ----
FROM node:24-alpine AS build
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
ENV NEXT_TELEMETRY_DISABLED=1
RUN npm run build

# ---- Stage 3: рантайм ----
FROM node:24-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
ENV PORT=3001
ENV HOSTNAME=0.0.0.0

RUN addgroup --system --gid 1001 nodejs \
 && adduser --system --uid 1001 nextjs

COPY --from=build --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=build --chown=nextjs:nodejs /app/.next/static ./.next/static
COPY --from=build --chown=nextjs:nodejs /app/public ./public

USER nextjs
EXPOSE 3001
CMD ["node", "server.js"]
