# syntax=docker/dockerfile:1
# 交付形态的前端镜像：构建阶段跑 pnpm，运行阶段只留 nginx 与静态产物。
#
# 与开发镜像（infra/docker/web.Dockerfile：vite dev server）的区别：
#   * 只发布构建产物，不带 Node、不带源码、不带 pnpm store；
#   * nginx 负责 SPA 兜底、静态资源缓存与 /api 反向代理（见 infra/docker/nginx/default.conf）。

# --- 构建阶段 ---
FROM node:24.18.0-bookworm-slim@sha256:6f7b03f7c2c8e2e784dcf9295400527b9b1270fd37b7e9a7285cf83b6951452d AS build
RUN npm install --global pnpm@11.10.0
WORKDIR /workspace
# 先装依赖：lockfile 与 workspace 清单不变时这一层可以复用
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY apps/web/package.json apps/web/
COPY packages/api-contract/package.json packages/api-contract/
RUN --mount=type=cache,target=/pnpm-store pnpm config set store-dir /pnpm-store \
    && pnpm install --frozen-lockfile --filter @merine/web... --filter @merine/api-contract
# 再放源码并构建
COPY packages/api-contract/ packages/api-contract/
COPY apps/web/ apps/web/
RUN pnpm --filter @merine/web build

# --- 运行阶段 ---
FROM nginx:1.29-alpine@sha256:5616878291a2eed594aee8db4dade5878cf7edcb475e59193904b198d9b830de AS runtime
COPY infra/docker/nginx/default.conf /etc/nginx/conf.d/default.conf
COPY infra/docker/nginx/security-headers.conf /etc/nginx/security-headers.conf
COPY --from=build /workspace/apps/web/dist/ /usr/share/nginx/html/
EXPOSE 80
