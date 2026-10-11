// 在与目标平台匹配的 Node 容器中检查成品；不调用模型、不使用开发环境 DSH_HOME。
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { mkdtemp, rm, readdir } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';

assert.equal(process.platform, 'linux');
assert.equal(process.arch, 'x64');
const installed = await readdir('/agent/node_modules/.pnpm');
assert.deepEqual(
  installed.filter((name) =>
    /libreoffice|sherpa|chrome-devtools|electron-winstaller|@openai\+codex|@anthropic-ai\+claude-agent-sdk|mermaid@/.test(
      name,
    ),
  ),
  [],
  'excluded dependencies must not be shipped',
);
const home = await mkdtemp(join(tmpdir(), 'agent-package-smoke-'));
const child = spawn('/bin/sh', ['/agent/start.sh', '--port', '3081'], {
  cwd: '/agent',
  env: { PATH: process.env.PATH, HOME: home, DSH_HOME: home },
  stdio: ['ignore', 'pipe', 'pipe'],
});
let output = '';
child.stdout.on('data', (chunk) => {
  output += chunk;
});
child.stderr.on('data', (chunk) => {
  output += chunk;
});
try {
  let response;
  for (let attempt = 0; attempt < 120; attempt++) {
    if (child.exitCode !== null) throw new Error('Agent exited before readiness');
    try {
      response = await fetch('http://127.0.0.1:3081/', { signal: AbortSignal.timeout(1000) });
    } catch {
      /* startup */
    }
    if (response) break;
    await delay(500);
  }
  assert.equal(response?.status, 401);
  for (let attempt = 0; attempt < 40 && !output.includes('?token='); attempt++) await delay(250);
  const url = output.match(/http:\/\/127\.0\.0\.1:3081\/\?token=[^\s\x1b]+/)?.[0];
  assert.ok(url, 'local authentication URL');
  const auth = await fetch(url, { redirect: 'manual' });
  const cookie = auth.headers
    .getSetCookie()
    .map((v) => v.split(';')[0])
    .join('; ');
  assert.ok(cookie);
  const page = await fetch('http://127.0.0.1:3081/', { headers: { cookie } });
  assert.equal(page.status, 200);
  const html = await page.text();
  assert.match(html, /__DSH_BOOT__/);
  const graphText = html.match(/globalThis\["__DSH_BOOT__"\] = ([\s\S]*?)<\/script>/)?.[1];
  assert.ok(graphText, 'client plugin graph');
  const graph = JSON.parse(graphText);
  for (const batch of graph.batches) {
    const asset = await fetch(new URL(batch.url, 'http://127.0.0.1:3081/'), {
      headers: { cookie },
    });
    assert.equal(asset.status, 200, 'client plugin bundle');
    assert.match(asset.headers.get('content-type'), /javascript/);
    await asset.arrayBuffer();
  }
  const inventory = await fetch('http://127.0.0.1:3081/api/pluginInventory/list', {
    method: 'POST',
    headers: { cookie, origin: 'http://127.0.0.1:3081', 'content-type': 'application/json' },
    body: JSON.stringify({
      type: 'client-request',
      rpcId: 'package-smoke',
      method: 'pluginInventory/list',
      payload: { args: {} },
    }),
  });
  assert.equal(inventory.status, 200);
  const result = (await inventory.json()).result;
  assert.equal(result.ok, true);
  const bad = result.value.entries.filter((row) => row.enabled && row.fiberPhase === 'failed');
  assert.deepEqual(
    bad.map((row) => row.moduleName),
    [],
    'no failed host plugins',
  );
  const forbidden =
    /dsh-(?:office-to-pdf|tool-web|tool-bash|tool-pwsh|session-log-deepseek|session-telemetry-otel|web-search-deepseek|web-fetch-http|experimental-)/;
  const rows = [];
  function visit(value) {
    if (Array.isArray(value)) value.forEach(visit);
    else if (value && typeof value === 'object') {
      if (value.moduleName) rows.push(value);
      Object.values(value).forEach(visit);
    }
  }
  visit(result.value);
  assert.deepEqual(
    rows
      .filter((row) => row.enabled && forbidden.test(row.moduleName))
      .map((row) => row.moduleName),
    [],
  );
  // 验证 HTML 直接引用的本地静态资源，避免只验服务启动而漏掉前端文件。
  for (const match of html.matchAll(/(?:src|href)="(\/(?:assets\/|favicon)[^"]+)"/g)) {
    const asset = await fetch(`http://127.0.0.1:3081${match[1]}`, { headers: { cookie } });
    assert.equal(asset.status, 200, match[1]);
  }
  console.log(
    `Linux x64 成品冒烟通过：401 → 登录后 200；${rows.length} 个插件项已检查；静态资源正常；未调用模型。`,
  );
} catch (error) {
  // 启动日志仅打印已去除 token 的诊断尾部，禁止泄露登录链接。
  console.error(output.replace(/token=[^\s\x1b]+/g, 'token=[REDACTED]').slice(-5000));
  throw error;
} finally {
  const stopped = new Promise((resolve) => child.once('exit', resolve));
  child.kill('SIGTERM');
  await Promise.race([stopped, delay(5000)]);
  if (child.exitCode === null) {
    child.kill('SIGKILL');
    await stopped;
  }
  await rm(home, { recursive: true, force: true });
}
