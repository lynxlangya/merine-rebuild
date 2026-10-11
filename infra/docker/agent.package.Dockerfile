# syntax=docker/dockerfile:1
# 仅用作 Linux x86_64 打包，不启动或修改项目已有运行实例。
FROM --platform=linux/amd64 node:24.18.0-bookworm-slim AS package
RUN --mount=type=cache,id=merine-agent-linux-x64-apt,target=/var/cache/apt,sharing=locked \
    rm -f /etc/apt/apt.conf.d/docker-clean \
    && apt-get update && apt-get install -y --no-install-recommends ca-certificates \
    && sed -i 's|http://deb.debian.org|https://deb.debian.org|g' /etc/apt/sources.list.d/debian.sources \
    && apt-get -o Acquire::Retries=2 update \
    && apt-get -o Acquire::Retries=2 install -y --no-install-recommends python3 make g++ musl-tools \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /build
COPY . .
RUN --mount=type=cache,id=merine-agent-linux-x64-pnpm,target=/pnpm-store \
    corepack enable && corepack pnpm install --store-dir /pnpm-store --prod --frozen-lockfile
# 使用 Node 自带的 TS stripping，构建 Linux 的系统原生模块；不引入 tsx 开发依赖。
RUN node native/system/scripts/build.ts
RUN --network=none node assemble-agent.mjs
RUN cp LICENSE THIRD_PARTY_NOTICES.md production-scope.json /out/agent/ \
    && cp -R project /out/agent/project \
    && cp deployment/* /out/agent/ && chmod +x /out/agent/start.sh

FROM scratch AS artifacts
COPY --from=package /out/agent /agent
