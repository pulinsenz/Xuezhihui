# 学智汇 前端镜像（多阶段：Vite 构建 → Nginx 托管静态产物）
# 基础镜像通过构建参数可覆盖（国内拉取失败时配合 docker-compose.pull-mirror.yml 用 DaoCloud 代理）
# 注意：多阶段 FROM 可用的 ARG 必须在文件顶部声明
ARG NODE_IMAGE=node:22-alpine
ARG NGINX_IMAGE=nginx:1.27-alpine
# Cloudflare Turnstile 站点 key（注册人机验证；未传则构建产物不渲染验证码 widget）
ARG VITE_TURNSTILE_SITE_KEY=

# 阶段一：Vite 构建
FROM ${NODE_IMAGE} AS build
WORKDIR /app

# 先只拷 manifest，命中 npm 缓存层，源码改动不触发全量重装
COPY package.json package-lock.json ./
# npm ci 默认直接用 package-lock 里 resolved 的 URL（已全部指向 npmmirror），无需 .npmrc；
# 这里再显式设一次 registry 纯属兜底：lock 中个别包若缺 resolved 时不会回退官方源导致超时
RUN npm config set registry https://registry.npmmirror.com \
    && npm ci --no-audit --no-fund
# 后拷源码，最大化缓存命中
COPY . .
# 注入 Turnstile site key（Vite 以 import.meta.env.VITE_* 编译进产物）
ARG VITE_TURNSTILE_SITE_KEY
ENV VITE_TURNSTILE_SITE_KEY=$VITE_TURNSTILE_SITE_KEY
RUN npm run build

# 阶段二：Nginx 运行（nginx:alpine 自带 busybox wget，供 healthcheck 用）
FROM ${NGINX_IMAGE}
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
# 不重写 CMD：nginx:alpine 默认 CMD 即 `nginx -g "daemon off;"`
