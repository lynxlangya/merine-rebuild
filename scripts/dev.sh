#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

setup() {
  if [[ -f "$ROOT/.env" ]]; then return; fi
  command -v openssl >/dev/null || { printf 'Setup requires openssl.\n' >&2; exit 1; }
  (
    umask 077
    for key in MYSQL_ROOT_PASSWORD MYSQL_MIGRATION_PASSWORD MYSQL_APP_PASSWORD; do
      printf '%s=%s\n' "$key" "$(openssl rand -hex 24)"
    done > "$ROOT/.env"
  )
  printf 'Created private local .env; existing credentials are never overwritten.\n'
}

compose() {
  docker compose --project-name merine-rebuild-dev --env-file "$ROOT/.env" -f "$ROOT/infra/compose.yaml" "$@"
}

require_env() {
  [[ -f "$ROOT/.env" ]] || { printf 'Run ./scripts/dev.sh setup first.\n' >&2; exit 1; }
}

case "${1:-help}" in
  setup) setup ;;
  up)
    setup
    compose config --quiet
    compose build api web
    compose up -d --wait --wait-timeout 180 mysql
    compose run --rm --no-deps migrate
    compose up -d --wait --wait-timeout 600 api web
    printf '\nWeb: http://127.0.0.1:5173\nAPI docs: http://127.0.0.1:9002/api/docs\n'
    ;;
  deps)
    setup
    compose up -d --wait --wait-timeout 180 mysql
    ;;
  migrate)
    require_env
    compose build api
    compose up -d --wait --wait-timeout 180 mysql
    compose run --rm --no-deps migrate
    ;;
  status) require_env; compose ps ;;
  logs) require_env; shift; compose logs --tail 100 -f "$@" ;;
  down) require_env; compose down ;;
  check)
    require_env
    compose exec -T web pnpm --filter @merine/web typecheck
    compose exec -T web pnpm format:check
    compose exec -T api ./mvnw -B -q verify
    ;;
  build)
    require_env
    compose exec -T web pnpm --filter @merine/web build
    compose exec -T api ./mvnw -B -q package
    ;;
  *)
    printf 'Usage: ./scripts/dev.sh {setup|up|deps|migrate|status|logs [service]|down|check|build}\n'
    printf 'up starts the local development stack; down preserves all named volumes.\n'
    [[ "${1:-help}" == help ]] || exit 1
    ;;
esac
