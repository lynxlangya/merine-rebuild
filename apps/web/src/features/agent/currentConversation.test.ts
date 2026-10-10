import assert from 'node:assert/strict';
import test from 'node:test';
import { readCurrentConversation, rememberCurrentConversation } from './currentConversation.ts';
import {
  activeSession,
  applyStreamItem,
  createSession,
  addSession,
  openServerSession,
  startTurn,
} from './chatModel.ts';

function storage() {
  const values = new Map<string, string>();
  return {
    values,
    getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => {
      values.set(key, value);
    },
    removeItem: (key: string) => {
      values.delete(key);
    },
  };
}

test('新会话拿到服务端标识后，刷新按该标识恢复而非临时会话 id', () => {
  const store = storage();
  let state = addSession({ sessions: [] }, createSession('local-draft', 1));
  state = startTurn(state, 'local-draft', {
    messageId: 'user-1',
    text: '问题',
    idempotencyKey: 'key-1',
  });
  state = applyStreamItem(state, 'local-draft', {
    kind: 'event',
    event: {
      type: 'STREAM_START',
      protocolVersion: 1,
      clientConversationId: 'local-draft',
      executionMode: 'LOCAL_STUB',
      generationId: 'g-1',
      messageId: 'm-1',
      conversationId: 'server-id',
    },
  });
  rememberCurrentConversation(() => store, 'user-a', activeSession(state)?.conversationId);
  const restoredId = readCurrentConversation(() => store, 'user-a');
  assert.equal(restoredId, 'server-id');
  const restored = openServerSession(
    { sessions: [] },
    { id: restoredId!, title: '问题', messages: [] },
  );
  assert.equal(activeSession(restored)?.conversationId, 'server-id');
  assert.deepEqual([...store.values.values()], ['server-id']);
});

test('切换会话覆盖当前标识，新聊天或删除当前会话后清除标识', () => {
  const store = storage();
  rememberCurrentConversation(() => store, 'user-a', 'conversation-a');
  rememberCurrentConversation(() => store, 'user-a', 'conversation-b');
  assert.equal(
    readCurrentConversation(() => store, 'user-a'),
    'conversation-b',
  );
  rememberCurrentConversation(() => store, 'user-a');
  assert.equal(
    readCurrentConversation(() => store, 'user-a'),
    undefined,
  );
});

test('不同账号及标签页的当前会话互不影响', () => {
  const firstTab = storage();
  const secondTab = storage();
  rememberCurrentConversation(() => firstTab, 'user-a', 'conversation-a');
  assert.equal(
    readCurrentConversation(() => firstTab, 'user-b'),
    undefined,
  );
  assert.equal(
    readCurrentConversation(() => secondTab, 'user-a'),
    undefined,
  );
  rememberCurrentConversation(() => firstTab, 'user-b', 'conversation-b');
  rememberCurrentConversation(() => firstTab, 'user-b');
  assert.equal(
    readCurrentConversation(() => firstTab, 'user-a'),
    'conversation-a',
  );
});

test('浏览器禁止访问存储时安全降级，不阻止对话', () => {
  const unavailable = () => {
    throw new Error('SecurityError');
  };
  assert.equal(readCurrentConversation(unavailable, 'user-a'), undefined);
  assert.doesNotThrow(() => rememberCurrentConversation(unavailable, 'user-a', 'conversation-a'));
  assert.doesNotThrow(() => rememberCurrentConversation(unavailable, 'user-a'));
});
