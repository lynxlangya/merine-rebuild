import assert from 'node:assert/strict';

const methods = new Set(['get', 'post', 'put', 'patch', 'delete', 'head', 'options', 'trace']);

/** 只排序对象键和文档标签；参数、枚举等数组保留原语义顺序。 */
export function sortKeys(value) {
  if (Array.isArray(value)) return value.map(sortKeys);
  if (value && typeof value === 'object')
    return Object.fromEntries(
      Object.keys(value)
        .sort()
        .map((key) => [key, sortKeys(value[key])]),
    );
  return value;
}

/**
 * 拍平多态子类型里的父类型引用。
 *
 * springdoc 会为“实现判别联合接口的 record”同时生成父类型的 oneOf 和子类型的
 * `allOf: [{$ref: 父}, {...}]`。openapi-typescript 遇到这种自引用会生成
 * `Omit<父, 'type'>` 的循环类型，TypeScript 只能解析成 any，联合就不再能按 type 缩窄。
 * 父类型在这里只是判别联合，子类型自带 type 字面量，去掉该引用不丢失字段。
 * 只处理“子类型在父类型 oneOf 中”的组合，其它 allOf 原样保留。
 */
export function flattenPolymorphicParents(schema) {
  const copy = structuredClone(schema);
  const schemas = copy.components?.schemas;
  if (!schemas) return copy;
  const polymorphicParents = new Map();
  for (const [name, definition] of Object.entries(schemas)) {
    if (Array.isArray(definition?.oneOf)) polymorphicParents.set(name, definition.oneOf);
  }
  for (const [name, definition] of Object.entries(schemas)) {
    if (!Array.isArray(definition?.allOf)) continue;
    const kept = definition.allOf.filter((item) => {
      if (!item || typeof item !== 'object' || typeof item.$ref !== 'string') return true;
      const parent = item.$ref.split('/').pop();
      const children = polymorphicParents.get(parent);
      return !children?.some((child) => child?.$ref?.endsWith(`/${name}`));
    });
    definition.allOf = kept;
  }
  return copy;
}

export function normalizeSchema(schema) {
  const flattened = flattenPolymorphicParents(schema);
  return sortKeys({
    ...flattened,
    servers: [{ url: '/' }],
    ...(flattened.tags && {
      tags: [...flattened.tags].sort((a, b) => a.name.localeCompare(b.name, 'en')),
    }),
  });
}

export function discoverGroups(config) {
  assert(Array.isArray(config.urls) && config.urls.length, '后端未注册 OpenAPI 分组');
  const names = new Set();
  return config.urls
    .map(({ name, url }) => {
      assert(/^[a-z][a-z0-9-]*$/.test(name), `分组名称不适合用作文件名：${name}`);
      assert(!names.has(name), `重复分组：${name}`);
      names.add(name);
      assert(url === `/api/openapi/${name}`, `分组文档必须使用本项目路径：${name}`);
      return { name, url };
    })
    .sort((a, b) => a.name.localeCompare(b.name, 'en'));
}

function operations(schema) {
  assert(schema.paths && typeof schema.paths === 'object', '文档缺少 paths');
  return new Map(
    Object.entries(schema.paths).flatMap(([path, item]) =>
      Object.entries(item)
        .filter(([method]) => methods.has(method))
        .map(([method, operation]) => [`${method.toUpperCase()} ${path}`, operation]),
    ),
  );
}

function validateReferences(schema, name) {
  function visit(value) {
    if (!value || typeof value !== 'object') return;
    if ('$ref' in value) {
      const reference = value.$ref;
      assert(typeof reference === 'string' && reference.startsWith('#/'), `${name} 包含外部引用`);
      const target = reference
        .slice(2)
        .split('/')
        .map((key) => decodeURIComponent(key).replaceAll('~1', '/').replaceAll('~0', '~'))
        .reduce((node, key) => node?.[key], schema);
      assert(target !== undefined, `${name} 包含悬空引用：${reference}`);
    }
    Object.values(value).forEach(visit);
  }
  visit(schema);
}

/** 每个接口必须恰好属于一个分组，协议与全量文档一致，引用在组内完整。 */
export function validateGroups(fullSchema, groups) {
  const full = normalizeSchema(fullSchema);
  const expected = operations(full);
  assert(expected.size, '全量文档没有接口');
  const operationIds = new Set();
  for (const [key, operation] of expected) {
    assert(operation.operationId, `${key} 缺少 operationId`);
    assert(!operationIds.has(operation.operationId), `重复 operationId：${operation.operationId}`);
    operationIds.add(operation.operationId);
  }
  const owners = new Map();
  for (const { name, schema: input } of groups) {
    const schema = normalizeSchema(input);
    validateReferences(schema, name);
    const entries = operations(schema);
    assert(entries.size, `分组没有接口：${name}`);
    for (const [key, operation] of entries) {
      assert(expected.has(key), `${name} 存在全量文档未声明的接口：${key}`);
      assert(!owners.has(key), `${key} 同时属于 ${owners.get(key)} 和 ${name}`);
      assert.deepEqual(operation, expected.get(key), `${name} 接口与全量文档不一致：${key}`);
      owners.set(key, name);
    }
    for (const [key, component] of Object.entries(schema.components ?? {})) {
      for (const [id, definition] of Object.entries(component))
        assert.deepEqual(
          definition,
          full.components?.[key]?.[id],
          `${name} 组件与全量文档不一致：${id}`,
        );
    }
  }
  const missing = [...expected.keys()].filter((key) => !owners.has(key));
  assert(!missing.length, `接口未分组：${missing.join(', ')}`);
  return owners.size;
}
