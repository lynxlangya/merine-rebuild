#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# 测试库与开发库在同一个 MySQL 实例内，但库名、账号都独立：
# 破坏性验证只作用于 merine_rebuild_test，不清开发库。
DB_URL_TEST='jdbc:mysql://mysql:3306/merine_rebuild_test?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true'

setup() {
  if [[ -f "$ROOT/.env" ]]; then return; fi
  command -v openssl >/dev/null || { printf 'Setup requires openssl.\n' >&2; exit 1; }
  (
    umask 077
    for key in MYSQL_ROOT_PASSWORD MYSQL_MIGRATION_PASSWORD MYSQL_APP_PASSWORD MYSQL_TEST_PASSWORD; do
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

# 向已有的 .env 追加缺失的键；不修改任何已存在的值。.env 已建立时 setup 会直接返回，
# 因此升级到带测试库的版本需要这个补充步骤。
ensure_env_key() {
  local key="$1"
  grep -q "^${key}=" "$ROOT/.env" && return
  command -v openssl >/dev/null || { printf 'Setup requires openssl.\n' >&2; exit 1; }
  (
    umask 077
    printf '%s=%s\n' "$key" "$(openssl rand -hex 24)" >> "$ROOT/.env"
  )
  printf 'Appended %s to the local .env; existing credentials are unchanged.\n' "$key"
}

# .env 只由本脚本生成（KEY=hex），因此可以安全地读入当前 shell
load_env() {
  require_env
  set -a
  # shellcheck disable=SC1091
  . "$ROOT/.env"
  set +a
}

# 幂等：库、测试账号与授权重复执行不会改变已有内容
prepare_test_db() {
  compose up -d --wait --wait-timeout 180 mysql
  compose exec -T -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql \
    mysql --protocol=socket --user=root <<SQL
CREATE DATABASE IF NOT EXISTS merine_rebuild_test
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'merine_app_test'@'%' IDENTIFIED BY '$MYSQL_TEST_PASSWORD';
GRANT ALL PRIVILEGES ON merine_rebuild_test.* TO 'merine_migrate'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON merine_rebuild_test.* TO 'merine_app_test'@'%';
FLUSH PRIVILEGES;
SQL
  # 测试库跑与开发库同一套迁移，避免用 H2 或手工建表代替 MySQL 语义
  compose run --rm --no-deps \
    -e DB_URL="$DB_URL_TEST" \
    -e DB_MIGRATION_USER=merine_migrate \
    -e DB_MIGRATION_PASSWORD="$MYSQL_MIGRATION_PASSWORD" \
    migrate
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
  seed)
    require_env
    ensure_env_key MYSQL_TEST_PASSWORD >/dev/null 2>&1 || true
    load_env
    compose config --quiet
    compose build api
    compose up -d --wait --wait-timeout 180 mysql
    # 迁移步骤不能接管 stdin：否则它会读走后面交互输入的密码，read 直接遇到 EOF
    compose run --rm --no-deps -T migrate < /dev/null
    read -r -p '演示账号登录名 [demo.analyst]: ' seed_login
    seed_login="${seed_login:-demo.analyst}"
    read -r -s -p '演示账号密码（至少 6 位，输入不回显）: ' seed_password
    printf '\n'
    if [[ ${#seed_password} -lt 6 ]]; then
      printf '密码至少 6 位，已中止；未写入任何数据。\n' >&2
      exit 1
    fi
    # 单位与角色可按需覆盖：SEED_ROLE_NAME='系统管理员' ./scripts/dev.sh seed
    seed_env=()
    for seed_key in SEED_DISPLAY_NAME SEED_UNIT_CODE SEED_UNIT_NAME SEED_ROLE_CODE SEED_ROLE_NAME; do
      seed_value="$(printenv "$seed_key" || true)"
      if [[ -n "$seed_value" ]]; then
        seed_env+=(-e "$seed_key=$seed_value")
      fi
    done

    # 不加 -q：初始化结果需要出现在日志里；密码只经环境变量传入。
    # ${seed_env[@]+"${seed_env[@]}"} 是空数组的安全展开写法：bash 3.2（macOS 自带）
    # 在 set -u 下展开空数组会直接以 unbound variable 退出，而默认用法
    # （不设任何 SEED_* 变量）seed_env 恰好是空的，会在输完密码后失败。
    compose run --rm --no-deps -T \
      -e SPRING_PROFILES_ACTIVE=seed \
      -e SEED_LOGIN_NAME="$seed_login" \
      -e SEED_PASSWORD="$seed_password" \
      ${seed_env[@]+"${seed_env[@]}"} \
      api ./mvnw -B spring-boot:run
    unset seed_password
    ;;
  test-db)
    require_env
    ensure_env_key MYSQL_TEST_PASSWORD
    load_env
    prepare_test_db
    printf '测试库 merine_rebuild_test 已就绪（与开发库 merine_rebuild 相互独立）。\n'
    ;;
  menus)
    # 恢复默认菜单：补齐缺失的引导菜单与权限码，不删除也不覆盖已有内容。
    # 用途是「菜单管理入口被删掉、页面上已经点不到恢复按钮」时的兜底。
    require_env
    load_env
    compose config --quiet
    compose build api
    compose up -d --wait --wait-timeout 180 mysql
    compose run --rm --no-deps -T migrate < /dev/null
    compose run --rm --no-deps -T -e SPRING_PROFILES_ACTIVE=menus api ./mvnw -B spring-boot:run
    ;;
  check)
    require_env
    ensure_env_key MYSQL_TEST_PASSWORD
    load_env
    prepare_test_db
    compose exec -T web pnpm --filter @merine/web typecheck
    compose exec -T web pnpm --filter @merine/web test
    compose exec -T web pnpm format:check
    compose exec -T \
      -e TEST_DB_URL="$DB_URL_TEST" \
      -e TEST_DB_APP_USER=merine_app_test \
      -e TEST_DB_APP_PASSWORD="$MYSQL_TEST_PASSWORD" \
      api ./mvnw -B -q verify
    ;;
  build)
    require_env
    # mvn package 会执行测试，测试需要隔离测试库的连接信息。
    # 这里不跳过测试：打包与检查应当跑同一套回归。
    ensure_env_key MYSQL_TEST_PASSWORD
    load_env
    prepare_test_db
    compose exec -T web pnpm --filter @merine/web build
    compose exec -T \
      -e TEST_DB_URL="$DB_URL_TEST" \
      -e TEST_DB_APP_USER=merine_app_test \
      -e TEST_DB_APP_PASSWORD="$MYSQL_TEST_PASSWORD" \
      api ./mvnw -B -q package
    ;;
  *)
    printf 'Usage: ./scripts/dev.sh {setup|up|deps|migrate|seed|menus|test-db|status|logs [service]|down|check|build}\n'
    printf 'up starts the local development stack; down preserves all named volumes.\n'
    printf 'seed initializes a local demo account; test-db prepares the isolated test schema.\n'
    printf 'menus restores missing bootstrap menu nodes and permission codes (works without the UI).\n'
    [[ "${1:-help}" == help ]] || exit 1
    ;;
esac
