/**
 * 真实接口联调：经 Vite 代理验证「认证 → 会话 → 业务读写 → 退出」这条链路。
 *
 * 需要先初始化本地演示账号（./scripts/dev.sh seed），并把账号密码经环境变量传入：
 *   API_LOGIN_NAME=... API_PASSWORD=... docker compose ... exec -T web pnpm smoke
 * 密码不进代码与仓库；缺失时直接失败，不静默跳过。
 */
import assert from 'node:assert/strict';

const base = process.env.SMOKE_BASE_URL ?? 'http://127.0.0.1:5173';
const loginName = process.env.API_LOGIN_NAME;
const password = process.env.API_PASSWORD;

if (!loginName || !password) {
  console.error(
    '缺少 API_LOGIN_NAME / API_PASSWORD。先运行 ./scripts/dev.sh seed 初始化演示账号，再带上这两个变量运行。',
  );
  process.exit(1);
}

/** fetch 不保存 Cookie，这里用一个最小的 Cookie 罐维持会话与 CSRF 令牌。 */
const jar = new Map();
function cookieHeader() {
  return [...jar].map(([name, value]) => `${name}=${value}`).join('; ');
}
function storeCookies(response) {
  for (const raw of response.headers.getSetCookie?.() ?? []) {
    const [pair] = raw.split(';');
    const index = pair.indexOf('=');
    jar.set(pair.slice(0, index).trim(), pair.slice(index + 1).trim());
  }
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

async function json(path, options) {
  const response = await call(path, options);
  const contentType = response.headers.get('content-type') ?? '';
  assert.ok(
    contentType.includes('application/json'),
    `${path} 必须返回 JSON，实际为 ${contentType}`,
  );
  const body = await response.json();
  assert.ok(body.requestId, `${path} 的响应必须带 requestId`);
  assert.equal(
    response.headers.get('x-request-id'),
    body.requestId,
    '响应头与响应体的请求编号必须一致',
  );
  return { response, body };
}

function csrfHeader() {
  const token = jar.get('XSRF-TOKEN');
  assert.ok(token, '缺少 XSRF-TOKEN Cookie');
  return { 'X-XSRF-TOKEN': token };
}

const unauth = await json('/api/bootstrap');
assert.equal(unauth.response.status, 401, '未登录访问业务接口必须返回 401');
assert.equal(unauth.body.code, 'UNAUTHENTICATED');

await json('/api/auth/csrf');

const noCsrf = await json('/api/auth/session', {
  method: 'POST',
  body: JSON.stringify({ loginName, password }),
});
assert.equal(noCsrf.response.status, 403, '缺少 CSRF 令牌的写请求必须被拒绝');

const wrongPassword = await json('/api/auth/session', {
  method: 'POST',
  headers: csrfHeader(),
  body: JSON.stringify({ loginName, password: `${password}-wrong` }),
});
assert.equal(wrongPassword.response.status, 401);
const unknownAccount = await json('/api/auth/session', {
  method: 'POST',
  headers: csrfHeader(),
  body: JSON.stringify({ loginName: `${loginName}.missing`, password: `${password}-wrong` }),
});
assert.equal(unknownAccount.response.status, 401);
assert.deepEqual(
  { code: unknownAccount.body.code, message: unknownAccount.body.message },
  { code: wrongPassword.body.code, message: wrongPassword.body.message },
  '账号不存在与密码错误必须返回相同结果，不能泄漏账号是否存在',
);

const login = await json('/api/auth/session', {
  method: 'POST',
  headers: csrfHeader(),
  body: JSON.stringify({ loginName, password }),
});
assert.equal(login.response.status, 200);
assert.equal(login.body.data.loginName, loginName);
assert.ok(login.body.data.unitName);
assert.ok(Array.isArray(login.body.data.roleNames));
assert.ok(!JSON.stringify(login.body).includes('password'), '响应不得包含任何密码字段');

const sessionCookie = jar.get('JSESSIONID');
assert.ok(sessionCookie, '登录成功后必须下发会话 Cookie');

const note = `SMOKE-${Date.now()}`;
const before = await json('/api/bootstrap');
assert.equal(before.response.status, 200);
assert.equal(before.body.data.database, 'merine_rebuild');

const created = await json('/api/bootstrap/probes', {
  method: 'POST',
  headers: csrfHeader(),
  body: JSON.stringify({ note }),
});
assert.equal(created.response.status, 201);
assert.equal(created.body.data.note, note);
assert.ok(created.body.data.createdAt);

const after = await json('/api/bootstrap');
assert.ok(after.body.data.totalProbes >= before.body.data.totalProbes + 1);
assert.ok(after.body.data.recentProbes.some((row) => row.id === created.body.data.id));

for (const invalid of ['', ' '.repeat(3), 'x'.repeat(121)]) {
  const result = await json('/api/bootstrap/probes', {
    method: 'POST',
    headers: csrfHeader(),
    body: JSON.stringify({ note: invalid }),
  });
  assert.equal(result.response.status, 400);
  assert.equal(result.body.code, 'VALIDATION_ERROR');
}

const missing = await json('/api/not-a-route');
assert.equal(missing.response.status, 404);

const logout = await json('/api/auth/session', { method: 'DELETE', headers: csrfHeader() });
assert.equal(logout.response.status, 200);

const afterLogout = await json('/api/bootstrap');
assert.equal(afterLogout.response.status, 401, '退出后原会话必须失效');

console.log(
  `PASS: 认证（CSRF/登录/错误密码/未登录/退出）→ 代理 → API → MySQL 读写与校验；合成记录 ${created.body.data.id}`,
);
