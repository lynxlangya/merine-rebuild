import assert from 'node:assert/strict';
import { test } from 'node:test';
import { ApiError } from '../../shared/http.ts';
import { flowCommand, flowIntent, flowOperation, resultUncertain } from './actions.ts';
import { executeFlowCommand } from './api.ts';

const values = {
  title: '合成线索',
  body: '合成正文',
  scopeUnitCodes: ['b', 'a'],
  targetUnitCodes: ['b'],
  note: '说明',
};
test('规范化实际命令后，空格、单位顺序和未提交字段不改变请求键', () => {
  const command = flowCommand({ code: 'create' }, values);
  const first = flowIntent(null, command);
  const retry = flowIntent(
    first,
    flowCommand(
      { code: 'create' },
      {
        ...values,
        body: ' 合成正文 ',
        scopeUnitCodes: ['a', 'b', 'a'],
        kind: 'CORRECTION',
      },
    ),
  );
  assert.equal(retry.key, first.key);
  assert.deepEqual(retry.command, command);
  assert.equal(flowCommand({ code: 'update' }, values, '1', 3).code, 'update');
  const updated = flowCommand({ code: 'update' }, values, '1', 3);
  assert.ok('body' in updated && 'version' in updated.body);
  assert.equal(updated.body.version, 3);
});
test('首次响应丢失后，实际 HTTP 重试沿用原请求键并取得已有结果', async (t) => {
  Object.defineProperty(globalThis, 'document', {
    value: { cookie: 'XSRF-TOKEN=synthetic-test' },
    configurable: true,
  });
  t.after(() => Reflect.deleteProperty(globalThis, 'document'));
  const keys: string[] = [];
  const committed = new Set<string>();
  t.mock.method(globalThis, 'fetch', async (url: string, init: RequestInit) => {
    assert.equal(url, '/api/intelligence-topics');
    assert.equal(init.method, 'POST');
    const key = new Headers(init.headers).get('Idempotency-Key')!;
    keys.push(key);
    committed.add(key);
    if (keys.length === 1) throw new TypeError('合成响应丢失');
    return Response.json({ code: 'OK', data: { id: '123' }, requestId: 'synthetic-test' });
  });
  const first = flowIntent(null, flowCommand({ code: 'create' }, values));
  await assert.rejects(executeFlowCommand(first.command, first.key), (error: unknown) => {
    first.uncertain = resultUncertain(error);
    return first.uncertain;
  });
  const retry = flowIntent(
    first,
    flowCommand({ code: 'create' }, { ...values, body: '合成正文 ', scopeUnitCodes: ['a', 'b'] }),
  );
  assert.equal((await executeFlowCommand(retry.command, retry.key)).id, '123');
  assert.deepEqual(keys, [first.key, first.key]);
  assert.equal(committed.size, 1);
});
test('未知结果不能被新内容覆盖；明确失败后的新内容获得新键', () => {
  const first = flowIntent(null, flowCommand({ code: 'create' }, values));
  first.uncertain = true;
  const changed = flowCommand({ code: 'create' }, { ...values, body: '新正文' });
  assert.throws(() => flowIntent(first, changed), /先重试/);
  assert.equal(flowIntent(first, first.command).key, first.key);
  first.uncertain = false;
  assert.notEqual(flowIntent(first, changed).key, first.key);
});
test('动作入口拒绝未知动作和缺少回执的接收动作', () => {
  assert.equal(flowOperation('unknown'), null);
  assert.equal(flowOperation('feedbacks'), null);
  assert.equal(flowOperation('sign'), null);
  assert.deepEqual(flowOperation('supplement'), { code: 'supplement' });
});
test('网络、服务错误和处理中视为结果未知；普通业务拒绝允许修改', () => {
  for (const error of [
    new ApiError('网络', 0),
    new ApiError('错误', 500),
    new ApiError('处理中', 409, 'IDEMPOTENCY_PENDING'),
    new SyntaxError('响应解析失败'),
  ]) {
    assert.equal(resultUncertain(error), true);
  }
  assert.equal(resultUncertain(new ApiError('范围不合法', 400, 'OUTSIDE_SCOPE')), false);
});
