#!/usr/bin/env node
// 本地单操作员配置：只同步服务令牌和数据源 ID，不读取或复制数据库密码。
import { randomBytes } from 'node:crypto';
import { readFileSync, writeFileSync, renameSync, mkdirSync, lstatSync, existsSync } from 'node:fs';
import { resolve, join } from 'node:path';
import { fileURLToPath } from 'node:url';
const root = resolve(fileURLToPath(new URL('..', import.meta.url)));
const id = process.argv[2];
if (process.argv.length !== 3 || !id || !/^[A-Za-z0-9_-]{1,80}$/.test(id)) {
  throw new Error('用法：node scripts/configure-agent-query.mjs <数据源 ID>（从后台卡片复制）');
}
const envPath = join(root, '.env');
if (!existsSync(envPath) || !lstatSync(envPath).isFile())
  throw new Error('请先运行项目 setup；.env 必须为普通文件');
let env = readFileSync(envPath, 'utf8');
const existing = env.match(/^MERINE_AGENT_BRIDGE_TOKEN=([a-f0-9]{64})$/im)?.[1];
const token = existing ?? randomBytes(32).toString('hex');
for (const [key, value] of Object.entries({
  MERINE_AGENT_BRIDGE_TOKEN: token,
  MERINE_AGENT_DATA_SOURCE_ID: id,
})) {
  const pattern = new RegExp(`^${key}=.*$`, 'gm');
  env = pattern.test(env)
    ? env.replace(pattern, `${key}=${value}`)
    : env.trimEnd() + `\n${key}=${value}\n`;
}
function privateWrite(path, content) {
  if (existsSync(path) && !lstatSync(path).isFile()) throw new Error('目标必须为普通文件');
  const temp = path + '.tmp-' + randomBytes(8).toString('hex');
  writeFileSync(temp, content, { mode: 0o600, flag: 'wx' });
  renameSync(temp, path);
}
const home = join(root, 'apps/agent/.dsh-home');
mkdirSync(home, { recursive: true, mode: 0o700 });
privateWrite(envPath, env);
privateWrite(
  join(home, 'query-bridge.json'),
  JSON.stringify(
    { url: 'http://127.0.0.1:9002/internal/agent/data-source', token, localForwardPort: 3307 },
    null,
    2,
  ) + '\n',
);
console.log(
  '已配置固定数据源并同步私有服务令牌，未输出凭据。请重建 API 容器以加载新环境变量，并重启 Agent；详见 docs/tasks/agent-readonly-query.md。',
);
