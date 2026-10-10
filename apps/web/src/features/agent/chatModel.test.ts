import assert from 'node:assert/strict';
import test from 'node:test';
import type { ChatEvent, MessagePart } from '@merine/api-contract';
import {
  activeSession,
  addSession,
  applyRunView,
  applyStreamItem,
  createSession,
  describeStreamFailure,
  failRun,
  isBusy,
  markAnswered,
  messageText,
  pendingQuestion,
  openServerSession,
  selectSession,
  questionPart,
  setDraft,
  startTurn,
  stopRequested,
  type AgentChatState,
  type AgentSession,
} from './chatModel.ts';
import { ApiError } from '../../shared/http.ts';
import type { ChatRunView } from '@merine/api-contract';
import type { StreamItem } from './stream.ts';

const event = (value: ChatEvent): StreamItem => ({ kind: 'event', event: value });

test('历史迟到回填不切回旧会话，也不清空正在编辑的草稿', () => {
  let state = openServerSession(
    { sessions: [] },
    { id: 'server-a', title: '会话 A', messages: [] },
  );
  state = setDraft(state, 'server-a', '未发送的问题');
  state = addSession(state, createSession('draft-b', 2));
  state = openServerSession(state, { id: 'server-a', title: '会话 A 已更新', messages: [] }, false);
  assert.equal(state.activeId, 'draft-b');
  assert.equal(state.sessions.find((item) => item.id === 'server-a')?.draft, '未发送的问题');
  state = selectSession(state, 'server-a');
  assert.equal(activeSession(state)?.title, '会话 A 已更新');
});

function baseState(): AgentChatState {
  return addSession({ sessions: [] }, createSession('s-1', 1));
}

function send(state: AgentChatState, text = '本月情况', key = 'key-0001'): AgentChatState {
  return startTurn(state, 's-1', { messageId: `u-${key}`, text, idempotencyKey: key });
}

function assistant(session: AgentSession | undefined) {
  return session?.messages.at(-1);
}

test('一轮发送到终态的完整归约', () => {
  let state = send(baseState());
  assert.equal(isBusy(activeSession(state)), true);
  assert.equal(activeSession(state)?.messages.length, 1);
  assert.equal(activeSession(state)?.title, '本月情况');
  assert.equal(activeSession(state)?.draft, '');

  state = applyStreamItem(
    state,
    's-1',
    event({
      type: 'STREAM_START',
      protocolVersion: 1,
      messageId: 'm-1',
      generationId: 'g-1',
      clientConversationId: 'c-1',
      conversationId: 'srv-c-1',
      executionMode: 'LOCAL_STUB',
    }),
  );
  state = applyStreamItem(
    state,
    's-1',
    event({ type: 'PART_START', messageId: 'm-1', partId: 'p-1', partType: 'TEXT' }),
  );
  state = applyStreamItem(
    state,
    's-1',
    event({ type: 'TEXT_DELTA', messageId: 'm-1', partId: 'p-1', delta: '甲' }),
  );
  state = applyStreamItem(
    state,
    's-1',
    event({ type: 'TEXT_DELTA', messageId: 'm-1', partId: 'p-1', delta: '乙' }),
  );
  state = applyStreamItem(
    state,
    's-1',
    event({ type: 'PART_DONE', messageId: 'm-1', partId: 'p-1' }),
  );
  state = applyStreamItem(
    state,
    's-1',
    event({ type: 'MESSAGE_DONE', messageId: 'm-1', status: 'SUCCEEDED', usage: undefined }),
  );

  const session = activeSession(state);
  assert.equal(session?.run?.status, 'SUCCEEDED');
  assert.equal(session?.run?.generationId, 'g-1');
  assert.equal(assistant(session)?.status, 'SUCCEEDED');
  assert.equal(messageText(assistant(session)!), '甲乙');
  assert.equal(assistant(session)?.parts[0].status, 'DONE');
  assert.equal(isBusy(session), false);
});

test('多个部件按 partId 归位，顺序稳定', () => {
  let state = send(baseState());
  state = applyStreamItem(
    state,
    's-1',
    event({
      type: 'STREAM_START',
      protocolVersion: 1,
      messageId: 'm-1',
      generationId: 'g-1',
      clientConversationId: 'c-1',
      conversationId: 'srv-c-1',
      executionMode: 'LOCAL_STUB',
    }),
  );
  state = applyStreamItem(
    state,
    's-1',
    event({ type: 'PART_START', messageId: 'm-1', partId: 'p-1', partType: 'TEXT' }),
  );
  state = applyStreamItem(
    state,
    's-1',
    event({ type: 'TEXT_DELTA', messageId: 'm-1', partId: 'p-1', delta: '前' }),
  );
  state = applyStreamItem(
    state,
    's-1',
    event({
      type: 'PART_SNAPSHOT',
      messageId: 'm-1',
      partId: 'p-2',
      part: { type: 'TABLE', columns: [], rows: [] } as MessagePart,
    }),
  );
  state = applyStreamItem(
    state,
    's-1',
    event({ type: 'PART_START', messageId: 'm-1', partId: 'p-3', partType: 'TEXT' }),
  );
  state = applyStreamItem(
    state,
    's-1',
    event({ type: 'TEXT_DELTA', messageId: 'm-1', partId: 'p-3', delta: '后' }),
  );
  const parts = assistant(activeSession(state))?.parts ?? [];
  assert.equal(parts.length, 3);
  assert.deepEqual(
    parts.map((part) => (part.kind === 'known' ? part.part.type : part.kind)),
    ['TEXT', 'TABLE', 'TEXT'],
  );
  assert.equal(
    parts[0].kind === 'known' && parts[0].part.type === 'TEXT' ? parts[0].part.text : '',
    '前',
  );
  assert.equal(
    parts[2].kind === 'known' && parts[2].part.type === 'TEXT' ? parts[2].part.text : '',
    '后',
  );
});

test('追问登记、作答与阻塞规则', () => {
  let state = send(baseState(), '上个月走私多少');
  state = applyStreamItem(
    state,
    's-1',
    event({
      type: 'STREAM_START',
      protocolVersion: 1,
      messageId: 'm-1',
      generationId: 'g-1',
      clientConversationId: 'c-1',
      conversationId: 'srv-c-1',
      executionMode: 'LOCAL_STUB',
    }),
  );
  state = applyStreamItem(
    state,
    's-1',
    event({
      type: 'PART_SNAPSHOT',
      messageId: 'm-1',
      partId: 'p-q',
      part: {
        type: 'QUESTION',
        questionId: 'Q1',
        prompt: '口径？',
        mode: 'SINGLE',
        required: true,
        options: [{ value: 'CASE', label: '立案' }],
      } as MessagePart,
    }),
  );
  state = applyStreamItem(
    state,
    's-1',
    event({ type: 'MESSAGE_DONE', messageId: 'm-1', status: 'AWAITING_INPUT', usage: undefined }),
  );
  assert.equal(pendingQuestion(activeSession(state))?.questionId, 'Q1');
  assert.equal(questionPart(activeSession(state))?.part.questionId, 'Q1');
  assert.equal(isBusy(activeSession(state)), false);

  state = markAnswered(state, 's-1', { questionId: 'Q1', values: ['CASE'] });
  assert.equal(pendingQuestion(activeSession(state)), undefined);
  assert.deepEqual(activeSession(state)?.run?.question?.values, ['CASE']);

  state = startTurn(state, 's-1', {
    messageId: 'u-2',
    text: '上个月走私多少',
    idempotencyKey: 'key-0002',
    generationId: 'g-1',
    answer: { questionId: 'Q1', values: ['CASE'] },
  });
  assert.equal(activeSession(state)?.run?.generationId, 'g-1');
  assert.equal(activeSession(state)?.messages.length, 3);
});

test('失败保留已接收内容并把流式部件标记为失败', () => {
  let state = send(baseState());
  state = applyStreamItem(
    state,
    's-1',
    event({
      type: 'STREAM_START',
      protocolVersion: 1,
      messageId: 'm-1',
      generationId: 'g-1',
      clientConversationId: 'c-1',
      conversationId: 'srv-c-1',
      executionMode: 'LOCAL_STUB',
    }),
  );
  state = applyStreamItem(
    state,
    's-1',
    event({ type: 'PART_START', messageId: 'm-1', partId: 'p-1', partType: 'TEXT' }),
  );
  state = applyStreamItem(
    state,
    's-1',
    event({ type: 'TEXT_DELTA', messageId: 'm-1', partId: 'p-1', delta: '半句' }),
  );
  state = applyStreamItem(
    state,
    's-1',
    event({
      type: 'ERROR',
      messageId: 'm-1',
      code: 'INTERNAL',
      message: '上游失败',
      retryable: true,
    }),
  );
  const message = assistant(activeSession(state));
  assert.equal(message?.status, 'FAILED');
  assert.equal(message?.error?.retryable, true);
  assert.equal(
    message?.parts.every((part) => part.status === 'FAILED'),
    true,
  );
  assert.equal(messageText(message!), '半句');
  assert.equal(activeSession(state)?.run?.status, 'FAILED');

  state = failRun(state, 's-1', {
    code: 'NETWORK_ERROR',
    message: '连接中断',
    retryable: true,
    status: 'INTERRUPTED',
  });
  assert.equal(activeSession(state)?.run?.status, 'INTERRUPTED');
});

test('停止请求与服务端确认', () => {
  let state = send(baseState(), '慢速回答');
  state = applyStreamItem(
    state,
    's-1',
    event({
      type: 'STREAM_START',
      protocolVersion: 1,
      messageId: 'm-1',
      generationId: 'g-1',
      clientConversationId: 'c-1',
      conversationId: 'srv-c-1',
      executionMode: 'LOCAL_STUB',
    }),
  );
  state = applyStreamItem(
    state,
    's-1',
    event({ type: 'PART_START', messageId: 'm-1', partId: 'p-1', partType: 'TEXT' }),
  );
  state = stopRequested(state, 's-1');
  assert.equal(activeSession(state)?.run?.status, 'CANCEL_REQUESTED');
  state = applyStreamItem(state, 's-1', event({ type: 'CANCEL_REQUESTED', messageId: 'm-1' }));
  state = applyStreamItem(
    state,
    's-1',
    event({ type: 'MESSAGE_DONE', messageId: 'm-1', status: 'ABORTED', usage: undefined }),
  );
  assert.equal(activeSession(state)?.run?.status, 'ABORTED');
  assert.equal(assistant(activeSession(state))?.parts[0].status, 'ABORTED');
});

test('未知展示部件降级、跨会话事件不串写', () => {
  let state = addSession(baseState(), createSession('s-2', 2));
  state = send(state, '会话一');
  state = selectAndSend(state, 's-2', '会话二');
  state = applyStreamItem(state, 's-1', {
    kind: 'unknownPart',
    messageId: 'm-other',
    partId: 'x',
    rawType: 'VIDEO',
  });
  assert.equal(
    activeSession(state)?.messages.every((message) => message.id !== 'm-other'),
    true,
  );
  state = applyStreamItem(
    state,
    's-2',
    event({
      type: 'STREAM_START',
      protocolVersion: 1,
      messageId: 'm-2',
      generationId: 'g-2',
      clientConversationId: 'c-2',
      conversationId: 'srv-c-2',
      executionMode: 'LOCAL_STUB',
    }),
  );
  state = applyStreamItem(state, 's-2', {
    kind: 'unknownPart',
    messageId: 'm-2',
    partId: 'p-x',
    rawType: 'VIDEO',
  });
  const second = state.sessions.find((session) => session.id === 's-2');
  assert.equal(second?.messages.at(-1)?.parts[0].kind, 'unknown');
  const first = state.sessions.find((session) => session.id === 's-1');
  assert.equal(
    first?.messages.some((message) => message.role === 'ASSISTANT'),
    false,
    '未知消息标识不得在别的会话里新建消息',
  );
});

test('草稿按会话隔离', () => {
  let state = addSession(baseState(), createSession('s-2', 2));
  state = setDraft(state, 's-1', '草稿一');
  state = setDraft(state, 's-2', '草稿二');
  assert.equal(state.sessions.find((session) => session.id === 's-1')?.draft, '草稿一');
  assert.equal(state.sessions.find((session) => session.id === 's-2')?.draft, '草稿二');
});

test('作答在受理前失败时恢复可再次作答', () => {
  let state = send(baseState(), '上个月走私多少');
  state = applyStreamItem(
    state,
    's-1',
    event({
      type: 'STREAM_START',
      protocolVersion: 1,
      messageId: 'm-1',
      generationId: 'g-1',
      clientConversationId: 'c-1',
      conversationId: 'srv-c-1',
      executionMode: 'LOCAL_STUB',
    }),
  );
  state = applyStreamItem(
    state,
    's-1',
    event({
      type: 'PART_SNAPSHOT',
      messageId: 'm-1',
      partId: 'p-q',
      part: {
        type: 'QUESTION',
        questionId: 'Q1',
        prompt: '口径？',
        mode: 'SINGLE',
        required: true,
        options: [{ value: 'CASE', label: '立案案件' }],
      } as MessagePart,
    }),
  );
  state = applyStreamItem(
    state,
    's-1',
    event({ type: 'MESSAGE_DONE', messageId: 'm-1', status: 'AWAITING_INPUT', usage: undefined }),
  );

  // 作答已提交但尚未收到 STREAM_START（服务端结果未确认）
  state = startTurn(state, 's-1', {
    messageId: 'u-2',
    text: '立案案件',
    idempotencyKey: 'key-0002',
    generationId: 'g-1',
    answer: { questionId: 'Q1', values: ['CASE'], label: '立案案件' },
  });
  assert.deepEqual(activeSession(state)?.run?.question?.values, ['CASE']);

  state = failRun(state, 's-1', {
    code: 'CHAT_CONCURRENCY_LIMIT',
    message: '同一账号同时只能有一条演示执行',
    retryable: true,
    status: 'FAILED',
  });
  const question = activeSession(state)?.run?.question;
  assert.equal(question?.values, undefined, '未获确认的答案要被清除');
  assert.equal(question?.freeText, undefined);
  assert.equal(question?.answered, false);
  assert.equal(pendingQuestion(activeSession(state))?.questionId, 'Q1', '仍可再次作答');
});

test('快照按当前执行定位消息，不覆盖上一轮回答', () => {
  let state = send(baseState(), '问题一');
  state = applyStreamItem(
    state,
    's-1',
    event({
      type: 'STREAM_START',
      protocolVersion: 1,
      messageId: 'm-1',
      generationId: 'g-1',
      clientConversationId: 'c-1',
      conversationId: 'srv-c-1',
      executionMode: 'LOCAL_STUB',
    }),
  );
  state = applyStreamItem(
    state,
    's-1',
    event({ type: 'PART_START', messageId: 'm-1', partId: 'p-1', partType: 'TEXT' }),
  );
  state = applyStreamItem(
    state,
    's-1',
    event({ type: 'TEXT_DELTA', messageId: 'm-1', partId: 'p-1', delta: '回答一' }),
  );
  state = applyStreamItem(
    state,
    's-1',
    event({ type: 'MESSAGE_DONE', messageId: 'm-1', status: 'SUCCEEDED', usage: undefined }),
  );

  // 第二轮在 STREAM_START 之前断连，快照恢复：应追加而不是覆盖上一轮
  state = startTurn(state, 's-1', {
    messageId: 'u-2',
    text: '问题二',
    idempotencyKey: 'key-0002',
  });
  state = applyRunView(state, 's-1', {
    idempotencyKey: 'key-0002',
    generationId: 'g-2',
    clientConversationId: 's-1',
    conversationId: 'srv-s-1',
    status: 'SUCCEEDED',
    cancelRequested: false,
    parts: [{ partId: 'p-s', part: { type: 'TEXT', text: '回答二' }, status: 'DONE' }],
    startedAt: '2026-10-10T00:00:00Z',
    finishedAt: '2026-10-10T00:00:01Z',
  } as ChatRunView);
  const messages = activeSession(state)?.messages ?? [];
  assert.deepEqual(
    messages.map((message) => message.role),
    ['USER', 'ASSISTANT', 'USER', 'ASSISTANT'],
  );
  assert.equal(messageText(messages[1]), '回答一');
  assert.equal(messageText(messages[3]), '回答二');

  // 同一执行的快照按 messageId 就地校准，不再新增消息
  state = applyRunView(state, 's-1', {
    idempotencyKey: 'key-0002',
    generationId: 'g-2',
    clientConversationId: 's-1',
    conversationId: 'srv-s-1',
    status: 'SUCCEEDED',
    cancelRequested: false,
    parts: [{ partId: 'p-s', part: { type: 'TEXT', text: '回答二（快照）' }, status: 'DONE' }],
    startedAt: '2026-10-10T00:00:00Z',
    finishedAt: '2026-10-10T00:00:01Z',
  } as ChatRunView);
  assert.equal(activeSession(state)?.messages.length, 4);
  assert.equal(messageText(activeSession(state)!.messages[3]), '回答二（快照）');
});

test('流失败分类：明确失败与待确认分开', () => {
  const network = describeStreamFailure(new TypeError('network error'), true);
  assert.equal(network.status, 'INTERRUPTED');
  assert.equal(network.retryable, true);
  const rejected = describeStreamFailure(
    new ApiError('同一账号同时只能有一条演示执行', 429, 'CHAT_CONCURRENCY_LIMIT'),
    false,
  );
  assert.equal(rejected.status, 'FAILED');
  const midStream = describeStreamFailure(
    new ApiError('服务暂不可用', 500, 'INTERNAL_ERROR'),
    true,
  );
  assert.equal(midStream.status, 'INTERRUPTED');
  const aborted = describeStreamFailure(new DOMException('aborted', 'AbortError'), true);
  assert.equal(aborted.status, 'INTERRUPTED');
});

function selectAndSend(state: AgentChatState, sessionId: string, text: string): AgentChatState {
  return startTurn({ ...state, activeId: sessionId }, sessionId, {
    messageId: `u-${sessionId}`,
    text,
    idempotencyKey: `key-${sessionId}`,
  });
}
