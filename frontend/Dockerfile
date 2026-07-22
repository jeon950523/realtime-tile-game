# syntax=docker/dockerfile:1

FROM node:22-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN VITE_API_BASE_URL="" VITE_WS_URL="" npm run build

FROM nginx:alpine AS runtime

COPY nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/ /usr/share/nginx/html/

EXPOSE 80

HEALTHCHECK --interval=10s --timeout=5s --start-period=10s --retries=10 \
  CMD wget --quiet --tries=1 --spider http://127.0.0.1/ || exit 1
