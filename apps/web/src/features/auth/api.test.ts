import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import { ApiError, onUnauthorized } from '../../shared/http.ts';
import { logout } from './api.ts';

const originalDocument = Object.getOwnPropertyDescriptor(globalThis, 'document');
before(() => {
  Object.defineProperty(globalThis, 'document', {
    configurable: true,
    value: { cookie: 'XSRF-TOKEN=synthetic-csrf-token' },
  });
});
after(() => {
  if (originalDocument) Object.defineProperty(globalThis, 'document', originalDocument);
  else Reflect.deleteProperty(globalThis, 'document');
});

function response(status: number) {
  return Response.json(
    { code: status === 200 ? 'OK' : 'TEST_ERROR', message: '合成响应', data: null },
    { status },
  );
}

test('退出遇到网络、CSRF 或服务错误时拒绝成功分支，并允许重试', async (context) => {
  let unauthorized = 0;
  const unsubscribe = onUnauthorized(() => unauthorized++);
  context.after(unsubscribe);
  const fetchMock = context.mock.method(globalThis, 'fetch');

  for (const status of [0, 403, 500]) {
    fetchMock.mock.mockImplementation(async () => {
      if (status === 0) throw new TypeError('synthetic network failure');
      return response(status);
    });
    await assert.rejects(logout(), (error: unknown) => {
      assert.ok(error instanceof ApiError);
      assert.equal(error.status, status);
      return true;
    });
  }
  assert.equal(unauthorized, 0, '退出失败不能广播会话已失效');

  fetchMock.mock.mockImplementation(async (_path, options) => {
    assert.equal(options?.method, 'DELETE');
    return response(200);
  });
  await assert.doesNotReject(logout());
});

test('服务端返回 401 时按会话已经失效处理，无须继续重试退出', async (context) => {
  let unauthorized = 0;
  const unsubscribe = onUnauthorized(() => unauthorized++);
  context.after(unsubscribe);
  context.mock.method(globalThis, 'fetch', async () => response(401));
  await assert.doesNotReject(logout());
  assert.equal(unauthorized, 1);
});
