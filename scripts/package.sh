#!/usr/bin/env bash
# 输出 API、Web、Linux x86_64 Agent 三份成品；不部署、不重启、不复制运行数据。
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
if [[ $# == 0 ]]; then
    ./scripts/dev.sh build
elif [[ $# != 1 || "$1" != --skip-build ]]; then
    printf 'Usage: ./scripts/package.sh [--skip-build]\n' >&2
    exit 1
fi
mkdir -p "$ROOT/dist"
stage="$(mktemp -d "$ROOT/dist/.package-XXXXXX")"
trap 'rm -rf "$stage"' EXIT
compose=(docker compose --project-name merine-rebuild-dev --env-file "$ROOT/.env" -f "$ROOT/infra/compose.yaml")
mkdir -p "$stage/api" "$stage/web/public" "$stage/web/nginx"
api_jar="$("${compose[@]}" exec -T api sh -c 'find target -maxdepth 1 -name "*.jar" -type f')"
[[ "$api_jar" == target/*.jar && "$api_jar" != *$'\n'* ]] || { printf '未找到唯一的已构建 API jar。\n' >&2; exit 1; }
"${compose[@]}" cp "api:/workspace/apps/api/$api_jar" "$stage/api/merine-api.jar"
cp -R apps/api/src/main/resources/db/migration "$stage/api/migration"
cp -R apps/web/dist/. "$stage/web/public/"
cp infra/docker/nginx/default.conf infra/docker/nginx/security-headers.conf "$stage/web/nginx/"

node scripts/package-agent.mjs "$stage/agent-build"
docker buildx build --platform linux/amd64 -f infra/docker/agent.package.Dockerfile \
    --output "type=local,dest=$stage/output" "$stage/agent-build"
docker run --rm --platform linux/amd64 --network none \
    -v "$stage/output/agent:/agent:ro" -v "$ROOT/scripts/package-agent-smoke.mjs:/smoke.mjs:ro" \
    node:24.18.0-bookworm-slim node /smoke.mjs

mkdir -p "$ROOT/dist/api" "$ROOT/dist/web" "$ROOT/dist/agent"
COPYFILE_DISABLE=1 tar -czf "$stage/merine-api.tar.gz" -C "$stage" api
COPYFILE_DISABLE=1 tar -czf "$stage/merine-web.tar.gz" -C "$stage" web
COPYFILE_DISABLE=1 tar -czf "$stage/merine-agent-linux-x64.tar.gz" -C "$stage/output" agent
mv "$stage/merine-api.tar.gz" "$ROOT/dist/api/"
mv "$stage/merine-web.tar.gz" "$ROOT/dist/web/"
mv "$stage/merine-agent-linux-x64.tar.gz" "$ROOT/dist/agent/"
cp infra/agent/DEPLOY.md "$ROOT/dist/agent/DEPLOY.md"
cp infra/PACKAGES.md "$ROOT/dist/README.md"
(cd dist && shasum -a 256 api/merine-api.tar.gz web/merine-web.tar.gz agent/merine-agent-linux-x64.tar.gz > SHA256SUMS)
ls -lh dist/api/*.tar.gz dist/web/*.tar.gz dist/agent/*.tar.gz
