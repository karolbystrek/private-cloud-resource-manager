# ---- Stage 1: Install dependencies ----
# Used as a cache layer for node_modules so rebuilds skip reinstalling packages.
FROM node:20-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

# ---- Stage 2: Development ----
# Copies pre-installed deps, exposes port 3000, and starts the Next.js dev server.
# The compose.yaml mounts the host source tree over /app so edits are reflected instantly.
FROM node:20-alpine AS dev
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
# When used with compose.yaml the host source tree is mounted over /app,
# overriding this layer. The COPY is kept so the stage also works standalone.
COPY . .
ENV NODE_ENV=development
EXPOSE 3000
CMD ["npm", "run", "dev"]

# ---- Stage 3: Build ----
FROM node:20-alpine AS builder
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
RUN npm run build

# ---- Stage 4: Production ----
# Minimal image: only the Next.js standalone output and static assets.
FROM node:20-alpine AS production
WORKDIR /app
ENV NODE_ENV=production
RUN addgroup --system --gid 1001 nodejs \
    && adduser --system --uid 1001 nextjs
COPY --from=builder --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=builder --chown=nextjs:nodejs /app/.next/static ./.next/static
COPY --from=builder --chown=nextjs:nodejs /app/public ./public
USER nextjs
EXPOSE 3000
CMD ["node", "server.js"]
