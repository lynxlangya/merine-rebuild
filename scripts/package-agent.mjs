// 在隔离工作区裁剪发布依赖；不修改 Agent 开发工作区、锁文件和运行数据。
import { cpSync, existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const source = join(root, 'apps/agent');
const destination = resolve(process.argv[2]);
const require = createRequire(join(source, 'apps/cli/package.json'));
const yaml = require('js-yaml');
class Expression {
  constructor(value) {
    this.value = value;
  }
}
const schema = yaml.DEFAULT_SCHEMA.extend([
  new yaml.Type('tag:yaml.org,2002:js', {
    kind: 'scalar',
    construct: (value) => new Expression(value),
    instanceOf: Expression,
    represent: (value) => value.value,
  }),
]);
const readJson = (file) => JSON.parse(readFileSync(file, 'utf8'));
const writeJson = (file, value) => writeFileSync(file, `${JSON.stringify(value, null, 2)}\n`);
const shortName = (name) => name.replace(/^@deepseek-ai\/dsh-/, '');
const excluded = new Set([
  'subagent-codex',
  'subagent-claude-code',
  'skill-office',
  'office-to-pdf',
  'client-ui-sidebar-documentpreview',
  'client-ui-sidebar-files',
  'api-workspace-files',
  'file-reference-local',
  'tool-fs',
  'tool-fs-search',
  'skill-filesystem',
  'terminal',
  'terminal-bash',
  'tool-terminal',
  'api-terminal-controller',
  'client-ui-sidebar-terminal',
  'tool-bash',
  'tool-bash-persistent',
  'tool-pwsh',
  'tool-pwsh-persistent',
  'host-open-in-app',
  'client-ui-open-in-app',
  'host-directory-picker-auto',
  'host-directory-picker-native',
  'host-directory-picker-browse',
  'client-ui-directory-picker-native',
  'client-ui-directory-picker-browse',
  'session-log-deepseek',
  'plugin-package-inventory-deepseek',
  'session-telemetry-otel',
  'host-product-telemetry-otel',
  'client-product-analytics',
  'command-feedback',
  'message-feedback',
  'client-ui-message-feedback',
  'client-ui-settings-session-log',
  'client-ui-settings-web-search',
  'web-search-deepseek',
  'web-fetch-http',
  'tool-web',
]);
const denied = (name) =>
  excluded.has(shortName(name)) ||
  /^@deepseek-ai\/dsh-experimental-/.test(name) ||
  /^@deepseek-ai\/node-addon-system-(darwin|linux-arm64)/.test(name) ||
  [
    '@openai/codex',
    '@anthropic-ai/claude-agent-sdk',
    '@deepseek-ai/libreoffice-kit',
    'chrome-devtools-frontend',
    'sherpa-onnx-node',
    'electron-winstaller',
    'mermaid',
  ].includes(name);

const packages = new Map();
for (const parent of [
  'apps',
  'vendor',
  'native/system/packages',
  ...readdirSync(join(source, 'packages'), { withFileTypes: true })
    .filter((p) => p.isDirectory())
    .map((p) => `packages/${p.name}`),
]) {
  for (const child of readdirSync(join(source, parent), { withFileTypes: true })) {
    if (!child.isDirectory()) continue;
    const path = join(source, parent, child.name);
    if (!existsSync(join(path, 'package.json'))) continue;
    const manifest = readJson(join(path, 'package.json'));
    packages.set(manifest.name, { path, manifest });
  }
}
const selected = new Map();
function visit(name) {
  if (denied(name) || selected.has(name) || !packages.has(name)) return;
  const entry = packages.get(name);
  const manifest = structuredClone(entry.manifest);
  delete manifest.devDependencies;
  for (const field of [
    'dependencies',
    'optionalDependencies',
    'peerDependencies',
    'peerDependenciesMeta',
  ]) {
    if (!manifest[field]) continue;
    manifest[field] = Object.fromEntries(
      Object.entries(manifest[field]).filter(([dep]) => !denied(dep)),
    );
  }
  // 开发包原先借 devDependencies 提供运行时 peer；发布工作区显式保留这些 peer，
  // 否则删除开发声明后，冻结安装会把它们当成缺失的自动 peer。
  manifest.dependencies = { ...manifest.peerDependencies, ...manifest.dependencies };
  selected.set(name, { ...entry, manifest });
  for (const field of ['dependencies', 'optionalDependencies', 'peerDependencies']) {
    for (const dep of Object.keys(manifest[field] ?? {})) visit(dep);
  }
}
visit('@deepseek-ai/dsh');
mkdirSync(destination, { recursive: true });
for (const { path, manifest } of selected.values()) {
  const target = join(destination, relative(source, path));
  cpSync(path, target, {
    recursive: true,
    filter: (file) =>
      !relative(path, file)
        .split('/')
        .some((part) =>
          ['node_modules', '.git', '.dsh-home', '.cache', 'tests', '__tests__'].includes(part),
        ),
  });
  writeJson(join(target, 'package.json'), manifest);
}

// 保留 JS 表达式标签；递归处理预设内的插件和各层的同 id 覆盖。
const patches = [];
for (const { path, manifest } of selected.values()) {
  const declared = manifest.dsh?.bundle?.patch;
  if (!declared) continue;
  for (const file of Array.isArray(declared) ? declared : [declared]) {
    const target = join(destination, relative(source, path), file);
    patches.push({ target, value: yaml.load(readFileSync(target, 'utf8'), { schema }) });
  }
}
const removedIds = new Set();
function collect(value) {
  if (Array.isArray(value)) {
    value.forEach(collect);
    return;
  }
  if (!value || typeof value !== 'object' || value instanceof Expression) return;
  if (typeof value.name === 'string' && denied(value.name) && value.id) removedIds.add(value.id);
  Object.values(value).forEach(collect);
}
patches.forEach((p) => collect(p.value));
function prune(value) {
  if (Array.isArray(value))
    return value
      .filter(
        (row) =>
          !(
            row &&
            typeof row === 'object' &&
            ((typeof row.name === 'string' && denied(row.name)) || removedIds.has(row.id))
          ),
      )
      .map(prune);
  if (!value || typeof value !== 'object' || value instanceof Expression) return value;
  return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, prune(item)]));
}
for (const patch of patches)
  writeFileSync(
    patch.target,
    yaml.dump(prune(patch.value), { schema, lineWidth: 120, noRefs: true }),
  );

const workspace = yaml.load(readFileSync(join(source, 'pnpm-workspace.yaml'), 'utf8'));
workspace.packages = [...selected.values()].map((entry) => relative(source, entry.path));
// 不安装开发环境 Git hooks；原生模块仍使用原仓库已批准的构建设置。
workspace.allowBuilds.lefthook = false;
// 已排除的 Office/打包器仍在原锁文件中有补丁记录；允许它们在发布闭包中不用。
workspace.allowUnusedPatches = true;
writeFileSync(join(destination, 'pnpm-workspace.yaml'), yaml.dump(workspace));
const original = readJson(join(source, 'package.json'));
writeJson(join(destination, 'package.json'), {
  name: 'merine-agent-production-build',
  private: true,
  packageManager: original.packageManager,
});
// 只裁掉 importer 的声明，不重新解析第三方版本。保留原 snapshots/packages，
// 由 frozen-lockfile 校验裁剪后的安装，避免打包顺带升级传递依赖。
const lock = yaml.load(readFileSync(join(source, 'pnpm-lock.yaml'), 'utf8'));
const importers = { '.': {} };
for (const { path, manifest } of selected.values()) {
  const key = relative(source, path);
  const originalImporter = lock.importers[key];
  if (!originalImporter) throw new Error(`锁文件缺少 workspace：${key}`);
  const importer = {};
  for (const field of ['dependencies', 'optionalDependencies']) {
    const deps = Object.fromEntries(
      Object.keys(manifest[field] ?? {}).map((name) => {
        const entry =
          originalImporter[field]?.[name] ??
          originalImporter.devDependencies?.[name] ??
          originalImporter.dependencies?.[name] ??
          originalImporter.optionalDependencies?.[name];
        if (!entry) throw new Error(`锁文件缺少依赖：${key} / ${name}`);
        return [
          name,
          {
            ...entry,
            specifier: workspace.overrides?.[name] ? entry.specifier : manifest[field][name],
          },
        ];
      }),
    );
    if (Object.keys(deps).length) importer[field] = deps;
  }
  importers[key] = importer;
}
lock.importers = importers;
writeFileSync(
  join(destination, 'pnpm-lock.yaml'),
  yaml.dump(lock, { lineWidth: 160, noRefs: true }),
);
for (const file of ['LICENSE', 'THIRD_PARTY_NOTICES.md'])
  cpSync(join(source, file), join(destination, file));
if (existsSync(join(source, 'patches')))
  cpSync(join(source, 'patches'), join(destination, 'patches'), { recursive: true });
mkdirSync(join(destination, 'native/system/scripts'), { recursive: true });
cpSync(
  join(source, 'native/system/scripts/build.ts'),
  join(destination, 'native/system/scripts/build.ts'),
);
cpSync(join(root, 'infra/agent'), join(destination, 'deployment'), { recursive: true });
cpSync(join(root, 'scripts/assemble-agent.mjs'), join(destination, 'assemble-agent.mjs'));
mkdirSync(join(destination, 'project'), { recursive: true });
const projectPatch = yaml.load(readFileSync(join(source, 'project/merine.patch.yml'), 'utf8'), {
  schema,
});
writeFileSync(
  join(destination, 'project/merine.patch.yml'),
  yaml.dump(prune(projectPatch), { schema }),
);
writeJson(join(destination, 'production-scope.json'), {
  platform: 'linux-x64-glibc',
  node: '24.18.0',
  excludedPackages: [...packages.keys()].filter(denied).sort(),
  retainedSharedDependencies: ['node-pty', '@earendil-works/pi-ai'],
  note: '只裁剪发布工作区；共享子进程和模型适配依赖暂留。未包含凭据、会话或业务数据。',
});
console.log(
  `Agent 发布工作区：${selected.size} 个 workspace 包；已排除 ${[...packages.keys()].filter(denied).length} 个候选包。`,
);
