// 复制冻结安装的生产依赖，不再次解析版本；用 Node 镜像自带 npm 的 packlist 遵守各包 files 声明。
import {
  cpSync,
  existsSync,
  lstatSync,
  mkdirSync,
  readFileSync,
  readdirSync,
  realpathSync,
  writeFileSync,
} from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, join, relative } from 'node:path';
const require = createRequire('/usr/local/lib/node_modules/npm/package.json');
const packlist = require('npm-packlist');
const yaml = createRequire('/build/apps/cli/package.json')('js-yaml');
const paths = yaml.load(readFileSync('/build/pnpm-workspace.yaml', 'utf8')).packages;
const output = '/out/agent';
mkdirSync(output, { recursive: true });
const copyModules = (from, to) => {
  if (existsSync(from))
    cpSync(from, to, {
      recursive: true,
      verbatimSymlinks: true,
      filter: (path) => !relative(from, path).split('/').includes('.bin'),
    });
};
copyModules('/build/node_modules', join(output, 'node_modules'));
for (const path of paths) {
  const source = join('/build', path);
  const manifest = JSON.parse(readFileSync(join(source, 'package.json'), 'utf8'));
  const files = await packlist({
    path: source,
    package: manifest,
    isProjectRoot: true,
    edgesOut: new Map(),
  });
  for (const file of files) {
    if (file.split('/').some((part) => part === '.env' || part === '.dsh-home'))
      throw new Error('Runtime data in package');
    const target = join(output, path, file);
    mkdirSync(dirname(target), { recursive: true });
    cpSync(join(source, file), target, { verbatimSymlinks: true });
  }
  copyModules(join(source, 'node_modules'), join(output, path, 'node_modules'));
}
writeFileSync(
  join(output, 'package.json'),
  JSON.stringify({ name: 'merine-agent-runtime', private: true, type: 'module' }) + '\n',
);
// 整包可搬迁：所有软链接必须有目标，且目标位于包内。
function checkLinks(path) {
  for (const entry of readdirSync(path, { withFileTypes: true })) {
    const file = join(path, entry.name);
    if (lstatSync(file).isSymbolicLink()) {
      const real = realpathSync(file);
      if (!real.startsWith(output + '/'))
        throw new Error(`External package link: ${relative(output, file)}`);
    } else if (entry.isDirectory()) checkLinks(file);
  }
}
checkLinks(output);
console.log(`Assembled ${paths.length} workspace packages; all links resolve inside the artifact.`);
