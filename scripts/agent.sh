#!/usr/bin/env bash
# 海防助手 Agent（apps/agent：裁剪后的 DeepSeek Harness）生命周期脚本。
# 由 ./scripts/dev.sh 的 up/down/status/restart 联动调用，也可以单独执行：
#   ./scripts/agent.sh start|stop|restart|status|logs
# Agent 只服务本项目：配置与运行数据固定在 apps/agent/.dsh-home（已 gitignore），
# 不读写 ~/.dsh，也不影响本机的桌面版 DeepSeek Harness。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
AGENT_DIR="$ROOT/apps/agent"
AGENT_HOME="$AGENT_DIR/.dsh-home"
AGENT_LOG="$AGENT_HOME/dsh-web.log"
AGENT_PID_FILE="$AGENT_HOME/dsh-web.pid"
AGENT_PATCH="$AGENT_DIR/project/merine.patch.yml"
PORT="${MERINE_AGENT_PORT:-3080}"

log() { printf '%s\n' "$*"; }
fail() { printf '%s\n' "$*" >&2; exit 1; }

listener_pid() { lsof -t -iTCP:"$PORT" -sTCP:LISTEN 2>/dev/null | head -1 || true; }
port_in_use() { [[ -n "$(listener_pid)" ]]; }
read_pid() { [[ -f "$AGENT_PID_FILE" ]] && tr -d '[:space:]' < "$AGENT_PID_FILE" || true; }
pid_alive() { [[ -n "${1:-}" ]] && kill -0 "$1" 2>/dev/null; }

print_url() {
    local url
    url="$(grep -o 'http://127\.0\.0\.1:[0-9]*/?token=[^[:space:]]*' "$AGENT_LOG" 2>/dev/null | tail -1 || true)"
    [[ -n "$url" ]] && log "访问地址：$url"
}

require_toolchain() {
    command -v node >/dev/null || fail '找不到 node。Agent 需要 Node.js 22.19 或以上（仓库 mise.toml 固定 24.18）。'
    command -v corepack >/dev/null || fail '找不到 corepack，无法使用仓库固定的 pnpm 版本（apps/agent/package.json 的 packageManager）。'
    [[ -d "$AGENT_DIR" ]] || fail "找不到 Agent 目录：$AGENT_DIR"
    local major minor
    major="$(node -p 'process.versions.node.split(".")[0]')"
    minor="$(node -p 'process.versions.node.split(".")[1]')"
    if ((major < 22 || (major == 22 && minor < 19))); then
        fail "Node 版本过低（当前 $(node -v)），需要 22.19 或以上。"
    fi
}

require_runtime() {
    require_toolchain
    [[ -d "$AGENT_DIR/node_modules" ]] || fail 'Agent 依赖尚未安装，先执行：./scripts/agent.sh install'
}

install() {
    require_toolchain
    log '安装 Agent 依赖（corepack pnpm install）...'
    (cd "$AGENT_DIR" && COREPACK_ENABLE_DOWNLOAD_PROMPT=0 corepack pnpm install)
    log 'Agent 依赖安装完成。'
}

build() {
    require_runtime
    log '构建 Agent（native + lib + web）...'
    (cd "$AGENT_DIR" && COREPACK_ENABLE_DOWNLOAD_PROMPT=0 corepack pnpm run build)
    log 'Agent 构建完成。'
}

start() {
    require_runtime
    if port_in_use; then
        log "Agent 已在运行（端口 ${PORT}，PID $(listener_pid)）。"
        print_url
        return 0
    fi
    mkdir -p "$AGENT_HOME"
    : >"$AGENT_LOG"
    (
        cd "$AGENT_DIR"
        patch_args=()
        [[ -f "$AGENT_PATCH" ]] && patch_args=(--patch "$AGENT_PATCH")
        nohup env DSH_HOME="$AGENT_HOME" COREPACK_ENABLE_DOWNLOAD_PROMPT=0 \
            corepack pnpm dsh --profile web ${patch_args[@]+"${patch_args[@]}"} --no-open --port "$PORT" >>"$AGENT_LOG" 2>&1 &
        echo $! >"$AGENT_PID_FILE"
    )
    local waited=0
    while ((waited < 90)); do
        if port_in_use; then break; fi
        sleep 1
        waited=$((waited + 1))
    done
    if ! port_in_use; then
        printf 'Agent 启动失败（等待 %ss），最近日志：\n' "$waited" >&2
        tail -20 "$AGENT_LOG" >&2 || true
        exit 1
    fi
    # 记录真正监听端口的进程（pnpm/corepack 会再派生子进程，父进程 PID 不足以管理）。
    listener_pid >"$AGENT_PID_FILE"
    log "Agent 已启动（PID $(listener_pid)，端口 ${PORT}，DSH_HOME=${AGENT_HOME}）。"
    [[ -f "$AGENT_PATCH" ]] && log "已应用项目覆盖层：project/merine.patch.yml"
    print_url
}

stop() {
    local was_running=0 pid
    pid="$(read_pid)"
    if pid_alive "$pid"; then
        kill "$pid" 2>/dev/null || true
        was_running=1
    fi
    local lp
    lp="$(listener_pid)"
    if [[ -n "$lp" ]]; then
        kill "$lp" 2>/dev/null || true
        was_running=1
    fi
    local waited=0
    while ((waited < 20)); do
        port_in_use || break
        sleep 0.5
        waited=$((waited + 1))
    done
    if port_in_use; then
        lp="$(listener_pid)"
        [[ -n "$lp" ]] && kill -9 "$lp" 2>/dev/null || true
        sleep 1
    fi
    rm -f "$AGENT_PID_FILE"
    port_in_use && fail "Agent 未停止：端口 ${PORT} 仍被占用。"
    if ((was_running == 1)); then
        log 'Agent 已停止。'
    else
        log 'Agent 未在运行。'
    fi
}

status() {
    if port_in_use; then
        log "Agent: running（PID $(listener_pid)，端口 ${PORT}，DSH_HOME=${AGENT_HOME}）"
        print_url
    else
        log 'Agent: stopped'
        [[ -f "$AGENT_PID_FILE" ]] && log "（发现残留 PID 文件：${AGENT_PID_FILE}，下次 start 时会覆盖）"
    fi
}

logs() {
    [[ -f "$AGENT_LOG" ]] || fail "没有日志文件：${AGENT_LOG}（Agent 还没有启动过）"
    tail -n 100 -f "$AGENT_LOG"
}

case "${1:-help}" in
    install) install ;;
    build) build ;;
    start) start ;;
    stop) stop ;;
    restart)
        stop
        start
        ;;
    status) status ;;
    logs) logs ;;
    *)
        printf 'Usage: ./scripts/agent.sh {install|build|start|stop|restart|status|logs}\n'
        printf 'Agent 只服务本项目：DSH_HOME=%s，不读写 ~/.dsh。\n' "$AGENT_HOME"
        printf '端口可用 MERINE_AGENT_PORT 覆盖（默认 3080）。\n'
        [[ "${1:-help}" == help ]] || exit 1
        ;;
esac
