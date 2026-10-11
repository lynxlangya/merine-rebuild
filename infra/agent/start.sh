#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
export DSH_HOME="${DSH_HOME:-$ROOT/.dsh-home}"
# 使用编译后的 JS，不要求安装 pnpm、tsx 或 TypeScript。
exec node "$ROOT/apps/cli/lib/bin.js" --profile web --patch "$ROOT/project/merine.patch.yml" --no-open "$@"
