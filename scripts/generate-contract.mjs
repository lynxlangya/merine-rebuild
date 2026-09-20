/**
 * 从当前运行的后端导出 OpenAPI 快照与 TypeScript 类型。
 *
 * 接口文档默认要求登录（merine.security.public-api-docs=false），因此这里先建立会话：
 *   API_LOGIN_NAME=... API_PASSWORD=... docker compose ... exec -T web pnpm contract:generate
 * 账号口令只经环境变量传入，不写在脚本与仓库里；缺失时直接失败，不静默跳过。
 */
import { writeFile } from 'node:fs/promises';
import openapiTS, { astToString } from 'openapi-typescript';

const base = process.env.API_BASE_URL ?? 'http://127.0.0.1:9002';
const loginName = process.env.API_LOGIN_NAME;
const password = process.env.API_PASSWORD;

if (!loginName || !password) {
  console.error(
    '缺少 API_LOGIN_NAME / API_PASSWORD。先运行 ./scripts/dev.sh seed 初始化演示账号，再带上这两个变量运行。',
  );
  process.exit(1);
}

/** 最小 Cookie 罐：fetch 不保存 Cookie，而会话与 CSRF 令牌都要跨请求保持。 */
const jar = new Map();
function storeCookies(response) {
  for (const raw of response.headers.getSetCookie?.() ?? []) {
    const [pair] = raw.split(';');
    const index = pair.indexOf('=');
    jar.set(pair.slice(0, index).trim(), pair.slice(index + 1).trim());
  }
}
function cookieHeader() {
  return [...jar].map(([name, value]) => `${name}=${value}`).join('; ');
}

async function call(path, options = {}) {
  const headers = new Headers(options.headers);
  headers.set('Accept', 'application/json');
  if (options.body) headers.set('Content-Type', 'application/json');
  if (jar.size > 0) headers.set('Cookie', cookieHeader());
  const response = await fetch(`${base}${path}`, {
    ...options,
    headers,
    signal: AbortSignal.timeout(15000),
  });
  storeCookies(response);
  return response;
}

await call('/api/auth/csrf');
const login = await call('/api/auth/session', {
  method: 'POST',
  headers: { 'X-XSRF-TOKEN': jar.get('XSRF-TOKEN') ?? '' },
  body: JSON.stringify({ loginName, password }),
});
if (!login.ok) {
  console.error(`登录失败（${login.status}），无法导出接口文档。请确认账号口令与权限。`);
  process.exit(1);
}

const response = await call('/api/openapi');
if (!response.ok) throw new Error(`OpenAPI request failed: ${response.status}`);
const schema = await response.json();
schema.servers = [{ url: '/' }];
await writeFile(
  new URL('../packages/api-contract/openapi.json', import.meta.url),
  `${JSON.stringify(schema, null, 2)}\n`,
);
await writeFile(
  new URL('../packages/api-contract/src/schema.d.ts', import.meta.url),
  astToString(await openapiTS(schema)),
);
console.log('OpenAPI snapshot and TypeScript types updated.');
