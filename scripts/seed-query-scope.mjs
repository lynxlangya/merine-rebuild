#!/usr/bin/env node
/**
 * 查询范围首批配置初始化：把 scripts/query-scope-seed.json 的视图与中文说明合并到指定数据源。
 *
 * 原则：
 *   - 只补缺失：视图白名单取并集；视图显示名/用途说明、字段说明只在现有值为空时写入。
 *   - 保留人工配置：已有说明一律不覆盖，与模板不同的值只列为差异报告。
 *   - 先核对再写：模板视图必须能被只读账号探测到、模板字段必须真实存在；
 *     新增视图核对失败时整批停止，不把没有预期语义的视图加入白名单。
 *   - 重新读取后重新合并：保存范围会重新读取最新配置，说明按最新内容现算，避免覆盖并发修改。
 *   - 全程走现有管理接口（登录 → 探测 → 更新范围 → 保存说明 → 复检启用），不直接写配置表。
 *
 * 用法：
 *   node scripts/seed-query-scope.mjs <dataSourceId>            # 只输出计划，不写配置
 *   node scripts/seed-query-scope.mjs <dataSourceId> --apply    # 合并范围与说明，并在有变更时复检启用
 *
 * 环境：默认连本地 dev API（http://127.0.0.1:9002，免密账号 demo.hq.admin）；
 *       非 dev 环境用 API_BASE_URL / API_LOGIN_NAME / API_PASSWORD。
 */
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const template = JSON.parse(
  readFileSync(fileURLToPath(new URL('./query-scope-seed.json', import.meta.url)), 'utf8'),
);
const [sourceId, ...flags] = process.argv.slice(2);
const apply = flags.includes('--apply');
if (!sourceId || !/^[A-Za-z0-9-]{1,80}$/.test(sourceId)) {
  console.error('用法：node scripts/seed-query-scope.mjs <数据源 ID> [--apply]');
  process.exit(1);
}

const base = process.env.API_BASE_URL ?? 'http://127.0.0.1:9002';
const loginName = process.env.API_LOGIN_NAME ?? 'demo.hq.admin';
const password = process.env.API_PASSWORD;

const jar = new Map();
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
  if (jar.size > 0)
    headers.set('Cookie', [...jar].map(([name, value]) => `${name}=${value}`).join('; '));
  const response = await fetch(`${base}${path}`, {
    ...options,
    headers,
    signal: AbortSignal.timeout(20000),
  });
  storeCookies(response);
  return response;
}
async function json(path, options) {
  const response = await call(path, options);
  const body = await response.json().catch(() => null);
  return { response, body };
}
function xsrf() {
  return jar.get('XSRF-TOKEN') ?? '';
}

await call('/api/auth/csrf');
const login = await call(password ? '/api/auth/session' : '/api/auth/dev/session', {
  method: 'POST',
  headers: { 'X-XSRF-TOKEN': xsrf() },
  body: JSON.stringify(password ? { loginName, password } : { loginName }),
});
if (!login.ok) {
  console.error(
    `登录失败（${login.status}）。请确认 API 已启动；非 dev 环境提供 API_LOGIN_NAME / API_PASSWORD。`,
  );
  process.exit(1);
}

const detailPath = `/api/agent/query-scopes/${encodeURIComponent(sourceId)}`;
async function fetchDetail() {
  const { response, body } = await json(detailPath);
  if (!response.ok)
    throw new Error(`读取查询范围失败（${response.status}）：${body?.message ?? ''}`);
  return body.data;
}
async function inspect(view) {
  const query = view ? `?view=${encodeURIComponent(view)}` : '';
  const { response, body } = await json(`${detailPath}/inspect${query}`, {
    method: 'POST',
    headers: { 'X-XSRF-TOKEN': xsrf() },
  });
  if (!response.ok) throw new Error(`探测失败（${response.status}）：${body?.message ?? ''}`);
  return body.data;
}
async function replaceViews(version, views) {
  const { response, body } = await json(`${detailPath}/views`, {
    method: 'PUT',
    headers: { 'X-XSRF-TOKEN': xsrf() },
    body: JSON.stringify({ version, views }),
  });
  if (!response.ok) throw new Error(`保存范围失败（${response.status}）：${body?.message ?? ''}`);
  return body.data;
}
async function saveMetadata(input) {
  const { response, body } = await json(`${detailPath}/metadata`, {
    method: 'PUT',
    headers: { 'X-XSRF-TOKEN': xsrf() },
    body: JSON.stringify(input),
  });
  if (!response.ok) throw new Error(`保存说明失败（${response.status}）：${body?.message ?? ''}`);
  return body.data;
}

/**
 * 按当前详情合并单个模板视图：已有非空说明优先，模板只填空缺；
 * 返回完整覆盖集合、是否有变化、以及被保留的人工说明（用于差异报告）。
 */
function mergeView(entry, detail) {
  const meta = (detail.meta ?? []).find((item) => item.view === entry.view);
  const existingColumns = new Map(
    (meta?.columns ?? []).map((column) => [column.name, column.description ?? '']),
  );
  const displayName = (meta?.displayName ?? '').trim() || entry.displayName;
  const description = (meta?.description ?? '').trim() || entry.description;
  const merged = entry.columns.map((column) => ({
    name: column.name,
    description: (existingColumns.get(column.name) ?? '').trim() || column.description,
  }));
  // 保留模板之外的既有覆盖（说明保存是整段替换，必须原样带上）。
  const templateNames = new Set(entry.columns.map((column) => column.name));
  for (const column of meta?.columns ?? []) {
    if (column.name && !templateNames.has(column.name)) {
      merged.push({ name: column.name, description: column.description ?? '' });
    }
  }
  const existingByName = new Map(
    (meta?.columns ?? []).map((column) => [column.name, (column.description ?? '').trim()]),
  );
  const mergedByName = new Map(merged.map((column) => [column.name, column.description.trim()]));
  const columnsChanged =
    existingByName.size !== mergedByName.size ||
    [...mergedByName].some(([name, value]) => existingByName.get(name) !== value);
  const saved = new Set(detail.source.views ?? []);
  const changed =
    (meta?.displayName ?? '').trim() !== displayName ||
    (meta?.description ?? '').trim() !== description ||
    columnsChanged ||
    !saved.has(entry.view);
  const adminKept = [];
  if (meta?.displayName?.trim() && meta.displayName.trim() !== entry.displayName)
    adminKept.push('显示名');
  if (meta?.description?.trim() && meta.description.trim() !== entry.description)
    adminKept.push('用途说明');
  for (const column of meta?.columns ?? []) {
    const templateColumn = entry.columns.find((item) => item.name === column.name);
    if (
      templateColumn &&
      (column.description ?? '').trim() &&
      column.description.trim() !== templateColumn.description
    ) {
      adminKept.push(`字段 ${column.name}`);
    }
  }
  return {
    profile: { view: entry.view, displayName, description, columns: merged },
    changed,
    adminKept,
  };
}

let detail = await fetchDetail();
console.log(
  `数据源：${detail.source.name}（${detail.source.status}，配置 v${detail.source.version}，语义 v${detail.source.semanticVersion}）`,
);

// 1. 目录探测：模板视图必须都能被只读账号读到，否则不进入后续步骤。
const probe = await inspect();
if (probe.outcome !== 'PASSED') {
  console.error(`探测未通过（${probe.outcome}）：${probe.message}`);
  process.exit(1);
}
const detected = new Set(probe.detectedViews ?? []);
const unauthorized = template.views
  .filter((entry) => !detected.has(entry.view))
  .map((entry) => `${entry.view}：只读账号未探测到该视图，请先补充 SELECT/SHOW VIEW 授权`);
if (unauthorized.length > 0) {
  console.error('存在未授权视图，本次不写入：');
  for (const item of unauthorized) console.error(`  - ${item}`);
  process.exit(1);
}

// 2. 逐视图核对字段：新增视图核对失败即整批停止；已开放视图核对失败只跳过说明更新。
const existingViews = new Set(detail.source.views ?? []);
const failures = [];
for (const entry of template.views) {
  const inspected = await inspect(entry.view);
  if (inspected.outcome !== 'PASSED') {
    failures.push({ view: entry.view, message: `字段探测未通过（${inspected.outcome}）` });
    continue;
  }
  const live = new Set((inspected.viewColumns ?? []).map((column) => column.name));
  const missing = entry.columns.filter((column) => !live.has(column.name));
  if (missing.length > 0) {
    failures.push({
      view: entry.view,
      message: `模板字段在目标库不存在（${missing.map((column) => column.name).join('、')}）`,
    });
    continue;
  }
}
const blocking = failures.filter((failure) => !existingViews.has(failure.view));
if (blocking.length > 0) {
  console.error('新增视图结构核对未通过，本次不写入任何配置：');
  for (const failure of blocking) console.error(`  - ${failure.view}：${failure.message}`);
  process.exit(1);
}

// 3. 计划：基于当前详情计算白名单并集与说明合并结果；字段核对失败的已开放视图只报告。
const viewsToAdd = template.views
  .filter((entry) => !existingViews.has(entry.view))
  .map((entry) => entry.view);
const planUpdates = () => {
  const updates = [];
  const differences = [];
  for (const entry of template.views) {
    const failure = failures.find((item) => item.view === entry.view);
    if (failure) {
      differences.push(`${entry.view}：${failure.message}，保留现有说明`);
      continue;
    }
    const merged = mergeView(entry, detail);
    if (merged.changed) updates.push(merged.profile);
    if (merged.adminKept.length > 0) {
      differences.push(`${entry.view}：保留已有说明（${merged.adminKept.join('、')}）`);
    }
  }
  return { updates, differences };
};
let plan = planUpdates();

console.log(
  `\n计划：新增允许视图 ${viewsToAdd.length} 个${viewsToAdd.length ? `（${viewsToAdd.join('、')}）` : ''}`,
);
console.log(
  `计划：补充或更新说明 ${plan.updates.length} 个视图（${plan.updates.map((item) => item.view).join('、') || '无'}）`,
);
for (const item of plan.differences) console.log(`提示：${item}`);
if (!apply) {
  console.log('\n当前为演练模式：未写入任何配置。确认后加 --apply 执行。');
  process.exit(0);
}
if (viewsToAdd.length === 0 && plan.updates.length === 0) {
  console.log('\n无需变更：视图范围与说明均已配置。');
  process.exit(0);
}

// 4. 合并白名单（新增视图会由服务端复检并停用数据源），随后按最新配置重新计算说明。
if (viewsToAdd.length > 0) {
  await replaceViews(detail.source.version, [...existingViews, ...viewsToAdd]);
  detail = await fetchDetail();
  console.log(`\n已保存范围：${detail.source.views.length} 个视图；数据源已停用，等待复检启用`);
}
plan = planUpdates();
for (const profile of plan.updates) {
  detail = await saveMetadata({
    version: detail.source.version,
    semanticVersion: detail.source.semanticVersion,
    view: profile.view,
    displayName: profile.displayName,
    description: profile.description,
    columns: profile.columns.filter((column) => column.description.trim() !== ''),
  });
  console.log(
    `已保存说明：${profile.view}（${profile.columns.filter((column) => column.description).length} 个字段说明）`,
  );
}

// 5. 有改动时复检并启用，使新范围生效。
detail = await fetchDetail();
if (detail.source.status !== 'ENABLED') {
  const checkPath = `/api/agent/datasources/${encodeURIComponent(sourceId)}/check`;
  const checked = await json(checkPath, { method: 'POST', headers: { 'X-XSRF-TOKEN': xsrf() } });
  if (!checked.response.ok || checked.body?.data?.outcome !== 'PASSED') {
    console.error(
      `复检未通过：${checked.body?.data?.message ?? checked.body?.message ?? checked.response.status}`,
    );
    console.error('配置已保存但数据源仍停用，请在「数据源管理」检查后手动启用。');
    process.exit(1);
  }
  const statusPath = `/api/agent/datasources/${encodeURIComponent(sourceId)}/status`;
  const enabled = await json(statusPath, {
    method: 'POST',
    headers: { 'X-XSRF-TOKEN': xsrf() },
    body: JSON.stringify({ status: 'ENABLED', version: detail.source.version }),
  });
  if (!enabled.response.ok) {
    console.error(`启用失败（${enabled.response.status}）：${enabled.body?.message ?? ''}`);
    process.exit(1);
  }
  console.log('复检通过，数据源已重新启用');
}
console.log(
  '\n完成：Agent 下一次 describe_business_data 会取得新目录（旧 catalogRevision 失效）。',
);
