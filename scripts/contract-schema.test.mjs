import assert from 'node:assert/strict';
import test from 'node:test';
import { discoverGroups, normalizeSchema, validateGroups } from './contract-schema.mjs';

const port = { operationId: 'portList', responses: { 200: { description: 'ok' } } };
const task = { operationId: 'taskList', responses: { 200: { description: 'ok' } } };
const doc = (paths) => ({ openapi: '3.1.0', info: { title: 'test', version: '1' }, paths });
const full = () => doc({ '/api/ports': { get: port }, '/api/tasks': { get: task } });
const groups = () => [
  { name: 'maritime', schema: doc({ '/api/ports': { get: port } }) },
  { name: 'tasks', schema: doc({ '/api/tasks': { get: task } }) },
];

test('自动发现新分组，拒绝重复名称、越界路径和不可用文件名', () => {
  assert.deepEqual(
    discoverGroups({
      urls: [
        { name: 'tasks', url: '/api/openapi/tasks' },
        { name: 'new-module', url: '/api/openapi/new-module' },
      ],
    }).map(({ name }) => name),
    ['new-module', 'tasks'],
  );
  for (const urls of [
    [],
    [
      { name: 'tasks', url: '/api/openapi/tasks' },
      { name: 'tasks', url: '/api/openapi/tasks' },
    ],
    [{ name: '../escape', url: '/api/openapi/../escape' }],
    [{ name: 'tasks', url: 'https://external.example/api/openapi/tasks' }],
  ])
    assert.throws(() => discoverGroups({ urls }));
});

test('分组覆盖完整且接口内容一致时通过', () => {
  assert.equal(validateGroups(full(), groups()), 2);
});

test('新接口漏分组、同一接口跨组重复或组内新增未知接口时拒绝', () => {
  const extended = full();
  extended.paths['/api/new'] = { get: { ...port, operationId: 'newList' } };
  assert.throws(() => validateGroups(extended, groups()), /接口未分组/);
  assert.throws(() => validateGroups(full(), [...groups(), groups()[0]]), /同时属于/);
  const extra = groups();
  extra[0].schema.paths['/api/unknown'] = { get: port };
  assert.throws(() => validateGroups(full(), extra), /未声明/);
});

test('自动编号冲突或分组协议发生变化时拒绝', () => {
  const duplicate = full();
  duplicate.paths['/api/tasks'].get = { ...task, operationId: port.operationId };
  assert.throws(() => validateGroups(duplicate, groups()), /重复 operationId/);
  const changed = groups();
  changed[0].schema.paths['/api/ports'].get = { ...port, summary: 'unexpected' };
  assert.throws(() => validateGroups(full(), changed), /接口与全量文档不一致/);
});

test('分组必须自包含，拒绝悬空引用和错误共享结构', () => {
  const referenced = doc({
    '/api/ports': {
      get: {
        ...port,
        responses: {
          200: {
            content: { 'application/json': { schema: { $ref: '#/components/schemas/Port' } } },
          },
        },
      },
    },
  });
  referenced.components = { schemas: { Port: { type: 'object' } } };
  const group = { name: 'maritime', schema: structuredClone(referenced) };
  assert.equal(validateGroups(referenced, [group]), 1);
  delete group.schema.components.schemas.Port;
  assert.throws(() => validateGroups(referenced, [group]), /悬空引用/);
  group.schema.components.schemas.Port = { type: 'string' };
  assert.throws(() => validateGroups(referenced, [group]), /组件与全量文档不一致/);
});

test('输出顺序稳定，同时保留参数和枚举数组顺序', () => {
  const a = {
    paths: {},
    tags: [{ name: 'z' }, { name: 'a' }],
    enum: ['z', 'a'],
    info: { b: 2, a: 1 },
  };
  const b = {
    info: { a: 1, b: 2 },
    enum: ['z', 'a'],
    tags: [{ name: 'a' }, { name: 'z' }],
    paths: {},
  };
  assert.equal(JSON.stringify(normalizeSchema(a)), JSON.stringify(normalizeSchema(b)));
  assert.deepEqual(normalizeSchema(a).enum, ['z', 'a']);
});
