# syntax=docker/dockerfile:1
# ----------------------------------------------------------------
# Stage 1: Build React Frontend
# ----------------------------------------------------------------
FROM node:22-alpine AS builder

WORKDIR /app

# Copy dependency manifests
COPY package.json package-lock.json ./

# Install dependencies cleanly
RUN npm ci

# Copy frontend source and build production bundle
COPY . .
RUN npm run build

# ----------------------------------------------------------------
# Stage 2: Minimal Nginx Production Runner
# ----------------------------------------------------------------
FROM nginx:alpine AS runner

# Remove default configuration
RUN rm -rf /etc/nginx/conf.d/default.conf

# Copy custom Nginx proxy & SPA config
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Copy static assets from builder
COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=10s --timeout=3s --retries=3 \
  CMD wget -q -O - http://127.0.0.1/ || exit 1

CMD ["nginx", "-g", "daemon off;"]
