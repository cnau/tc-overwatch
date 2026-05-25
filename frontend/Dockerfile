# Frontend runtime image: Nginx serving the production Vite bundle.
#
# Build locally (from repo root):
#   docker build -t tc-overwatch-frontend:dev -f frontend/Dockerfile .
#
# To bump a base image: `docker pull <ref>` and copy the printed digest into
# the FROM line below.

# --- Stage 1: build the Vite bundle ---
FROM node:22-alpine@sha256:968df39aedcea65eeb078fb336ed7191baf48f972b4479711397108be0966920 AS builder

WORKDIR /app

# Install deps in a separate layer so source-only changes don't bust the cache.
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund

# Source tree (build context is repo root; .dockerignore strips node_modules/dist).
COPY frontend/ ./
RUN npm run build

# --- Stage 2: Nginx runtime ---
FROM nginx:1.27-alpine@sha256:65645c7bb6a0661892a8b03b89d0743208a18dd2f3f17a54ef4b76fb8e2f2a10

LABEL org.opencontainers.image.source="https://github.com/cnau/tc-overwatch"
LABEL org.opencontainers.image.description="tc-overwatch frontend (Nginx + static bundle)"

COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 80
