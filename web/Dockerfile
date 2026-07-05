# Публичный портал «Метро Душанбе» (Next.js 16, standalone output).
# Multi-stage: npm ci → next build → минимальный node-рантайм (без node_modules).
# Контекст сборки — каталог web/:
#   docker build -f web/Dockerfile -t metro-web web

# ---- Stage 1: зависимости ----
FROM node:24-alpine AS deps
WORKDIR /app
# libc6-compat — на случай нативных биндингов в toolchain Next на Alpine.
RUN apk add --no-cache libc6-compat
COPY package.json package-lock.json ./
RUN npm ci

# ---- Stage 2: сборка ----
FROM node:24-alpine AS build
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
# NEXT_PUBLIC_* инлайнится в бандл на этапе build, поэтому база API задаётся здесь.
# Для браузера это должен быть адрес, доступный ИЗ браузера (host-порт backend).
ARG NEXT_PUBLIC_API_BASE=http://localhost:8080/api/v1
ENV NEXT_PUBLIC_API_BASE=$NEXT_PUBLIC_API_BASE
ENV NEXT_TELEMETRY_DISABLED=1
RUN npm run build

# ---- Stage 3: рантайм ----
FROM node:24-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
ENV PORT=3000
ENV HOSTNAME=0.0.0.0

# Непривилегированный пользователь.
RUN addgroup --system --gid 1001 nodejs \
 && adduser --system --uid 1001 nextjs

# Standalone-вывод + статика + public (server.js обслуживает их сам).
COPY --from=build --chown=nextjs:nodejs /app/public ./public
COPY --from=build --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=build --chown=nextjs:nodejs /app/.next/static ./.next/static

USER nextjs
EXPOSE 3000
CMD ["node", "server.js"]
