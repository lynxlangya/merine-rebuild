/**
 * 从当前运行的后端导出 OpenAPI 快照与 TypeScript 类型。
 *
 * 接口文档默认要求登录（merine.security.public-api-docs=false）。本地开发使用
 * dev profile 的账号选择入口；交付环境使用 API_LOGIN_NAME / API_PASSWORD 建立会话。
 * 口令只经环境变量传入，不写在脚本与仓库里。
 */
import { mkdir, readdir, rm, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import openapiTS, { astToString } from 'openapi-typescript';
import { format, resolveConfig } from 'prettier';
import { discoverGroups, normalizeSchema, validateGroups } from './contract-schema.mjs';

const base = process.env.API_BASE_URL ?? 'http://127.0.0.1:9002';
const loginName = process.env.API_LOGIN_NAME ?? 'demo.hq.admin';
const password = process.env.API_PASSWORD;

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
const login = await call(password ? '/api/auth/session' : '/api/auth/dev/session', {
  method: 'POST',
  headers: { 'X-XSRF-TOKEN': jar.get('XSRF-TOKEN') ?? '' },
  body: JSON.stringify(password ? { loginName, password } : { loginName }),
});
if (!login.ok) {
  console.error(
    password
      ? `登录失败（${login.status}），无法导出接口文档。请确认账号口令与权限。`
      : `本地免密登录失败（${login.status}），无法导出接口文档。请确认 dev API 已启动；非 dev 环境请提供 API_LOGIN_NAME / API_PASSWORD。`,
  );
  process.exit(1);
}

async function document(path) {
  const response = await call(path);
  if (!response.ok) throw new Error(`OpenAPI request failed: ${path} (${response.status})`);
  return response.json();
}

try {
  const [full, config] = await Promise.all([
    document('/api/openapi'),
    document('/api/openapi/swagger-config'),
  ]);
  const groups = await Promise.all(
    discoverGroups(config).map(async ({ name, url }) => ({
      name,
      schema: normalizeSchema(await document(url)),
    })),
  );
  const count = validateGroups(full, groups);
  const formatOptions = await resolveConfig(fileURLToPath(import.meta.url));
  // 校验和类型生成全部成功后再写文件，避免协议错误改写已有契约。
  const outputs = [];
  for (const { name, schema } of groups) {
    outputs.push({
      name,
      json: await format(JSON.stringify(schema), { ...formatOptions, parser: 'json' }),
      types: await format(astToString(await openapiTS(schema)), {
        ...formatOptions,
        parser: 'typescript',
      }),
    });
  }
  const snapshotDir = new URL('../packages/api-contract/openapi/', import.meta.url);
  const typeDir = new URL('../packages/api-contract/src/generated/', import.meta.url);
  await mkdir(snapshotDir, { recursive: true });
  await mkdir(typeDir, { recursive: true });
  for (const output of outputs) {
    await writeFile(new URL(`${output.name}.json`, snapshotDir), output.json);
    await writeFile(new URL(`${output.name}.d.ts`, typeDir), output.types);
  }
  // 只清理专用生成目录里已撤销分组的文件，以及旧的全量产物。
  for (const [dir, suffix] of [
    [snapshotDir, '.json'],
    [typeDir, '.d.ts'],
  ]) {
    const expected = new Set(outputs.map(({ name }) => `${name}${suffix}`));
    for (const file of await readdir(dir))
      if (file.endsWith(suffix) && !expected.has(file)) await rm(new URL(file, dir));
  }
  await rm(new URL('../packages/api-contract/openapi.json', import.meta.url), { force: true });
  await rm(new URL('../packages/api-contract/src/schema.d.ts', import.meta.url), { force: true });
  console.log(`Updated ${groups.length} contract groups covering ${count} operations.`);
} finally {
  // 导出结束即注销这次建立的临时会话；不保存 Cookie 或令牌。
  const logout = await call('/api/auth/session', {
    method: 'DELETE',
    headers: { 'X-XSRF-TOKEN': jar.get('XSRF-TOKEN') ?? '' },
  });
  if (!logout.ok) {
    console.error(`退出契约导出临时会话失败（${logout.status}）。`);
    process.exitCode = 1;
  }
}
