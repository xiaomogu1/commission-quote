# Build stage: install exact dependency versions from the lock file and build the static site.
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# Runtime stage: nginx serves the site and forwards /api to the lending quote service (runs as non-root).
FROM nginxinc/nginx-unprivileged:1.27-alpine
# Readable by the non-root nginx user whatever the file's permissions are on the build machine.
COPY --chmod=644 docker/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080
