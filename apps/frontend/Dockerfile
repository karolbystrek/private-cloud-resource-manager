# ---- Stage 1: Install dependencies ----
FROM node:20-alpine AS deps
RUN apk add --no-cache libc6-compat
WORKDIR /app

RUN corepack enable && corepack prepare pnpm@latest --activate
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY apps/frontend/package.json ./apps/frontend/
RUN pnpm i --frozen-lockfile

# ---- Stage 2: Development ----
FROM node:20-alpine AS dev
WORKDIR /app
RUN corepack enable && corepack prepare pnpm@latest --activate

COPY --from=deps /app/node_modules ./node_modules
COPY --from=deps /app/apps/frontend/node_modules ./apps/frontend/node_modules
COPY . .

ENV NODE_ENV=development
EXPOSE 3000
CMD ["pnpm", "--filter", "frontend", "run", "dev"]

# ---- Stage 3: Build ----
FROM node:20-alpine AS builder
WORKDIR /app
RUN corepack enable && corepack prepare pnpm@latest --activate

COPY --from=deps /app/node_modules ./node_modules
COPY --from=deps /app/apps/frontend/node_modules ./apps/frontend/node_modules
COPY . .
RUN pnpm --filter frontend run build

# ---- Stage 4: Production ----
FROM node:20-alpine AS production
WORKDIR /app/apps/frontend
ENV NODE_ENV=production

RUN addgroup --system --gid 1001 nodejs \
    && adduser --system --uid 1001 nextjs

COPY --from=builder --chown=nextjs:nodejs /app/apps/frontend/.next/standalone ./
COPY --from=builder --chown=nextjs:nodejs /app/apps/frontend/.next/static ./.next/static
COPY --from=builder --chown=nextjs:nodejs /app/apps/frontend/public ./public

USER nextjs
EXPOSE 3000
CMD ["node", "server.js"]
