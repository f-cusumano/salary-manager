# syntax=docker/dockerfile:1.7
# =====================================================================================================
# Immagine del frontend Salary Manager: build della SPA con Node 22, servita da nginx (stable, alpine).
# Contesto di build: la cartella frontend/ (es. "docker build -t salary-manager-frontend frontend").
#
# nginx serve dist/ e inoltra /api, /v3/api-docs e Swagger UI al backend indicato da SAM_BACKEND_URL
# (default http://backend:8080, il servizio del docker-compose): la SPA chiama le API sulla stessa origine.
# =====================================================================================================

ARG NODE_IMAGE=node:22-alpine
ARG NGINX_IMAGE=nginx:stable-alpine

# -----------------------------------------------------------------------------------------------------
# Build: l'output (file statici) non dipende dalla piattaforma
# -----------------------------------------------------------------------------------------------------
FROM --platform=$BUILDPLATFORM ${NODE_IMAGE} AS build
WORKDIR /app

# Prima le sole dipendenze: lo strato resta valido finche' package.json e package-lock.json non cambiano
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm \
    npm ci --no-audit --no-fund

COPY . .

# Origine delle API se diversa da quella del frontend (vuota = stessa origine, tramite il proxy di nginx)
ARG VITE_API_BASE_URL=""
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}

# typecheck + build di produzione in dist/ (i mock non entrano nel bundle)
RUN npm run build

# -----------------------------------------------------------------------------------------------------
# Runtime
# -----------------------------------------------------------------------------------------------------
FROM ${NGINX_IMAGE}

LABEL org.opencontainers.image.title="salary-manager-frontend" \
      org.opencontainers.image.description="Salary Manager - SPA React servita da nginx"

# Il file e' un template: all'avvio l'immagine nginx sostituisce ${SAM_BACKEND_URL} e scrive conf.d/default.conf
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist/ /usr/share/nginx/html/

# URL del backend (schema://host:porta, senza barra finale ne' percorso)
ENV SAM_BACKEND_URL=http://backend:8080

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1/ || exit 1
