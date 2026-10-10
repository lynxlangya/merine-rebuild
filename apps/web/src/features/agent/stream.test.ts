import assert from 'node:assert/strict';
import test from 'node:test';
import type { ChatEvent, MessagePart } from '@merine/api-contract';
import {
  ChatProtocolError,
  SseFrameParser,
  parseSseData,
  readChatStream,
  type StreamItem,
} from './stream.ts';

const frame = (payload: unknown, id = '1') => `id: ${id}\ndata: ${JSON.stringify(payload)}\n\n`;

const streamStart = {
  type: 'STREAM_START',
  protocolVersion: 1,
  messageId: 'm-1',
  generationId: 'g-1',
  clientConversationId: 'conv-0001-demo',
  conversationId: 'srv-conv-0001-demo',
  executionMode: 'LOCAL_STUB',
};

test('拆帧支持注释、多行 data、id 与 CRLF/CR 换行', () => {
  const parser = new SseFrameParser();
  const frames = parser.push(
    ': ping\r\nid: 7\r\ndata: {"type":"TEXT_DELTA",\r\ndata: "messageId":"m","partId":"p","delta":"a"}\r\n\r\n',
  );
  assert.equal(frames.length, 1);
  assert.equal(frames[0].id, '7');
  assert.deepEqual(JSON.parse(frames[0].data), {
    type: 'TEXT_DELTA',
    messageId: 'm',
    partId: 'p',
    delta: 'a',
  });

  const lone = new SseFrameParser();
  assert.equal(lone.push('data: {}\r\r').length, 1);
});

test('按任意字节切分仍能解析中文与完整事件', async () => {
  const body =
    frame(streamStart) +
    frame({ type: 'TEXT_DELTA', messageId: 'm-1', partId: 'p-1', delta: '甲' });
  const bytes = new TextEncoder().encode(body);
  const chunks: Uint8Array[] = [];
  for (let index = 0; index < bytes.length; index += 7) {
    chunks.push(bytes.slice(index, index + 7));
  }
  const stream = new ReadableStream<Uint8Array>({
    start(controller) {
      chunks.forEach((chunk) => controller.enqueue(chunk));
      controller.close();
    },
  });
  const items: StreamItem[] = [];
  for await (const item of readChatStream(stream)) items.push(item);
  assert.equal(items.length, 2);
  const delta = items[1].kind === 'event' ? (items[1].event as ChatEvent) : undefined;
  assert.equal(delta?.type, 'TEXT_DELTA');
  assert.equal(delta?.type === 'TEXT_DELTA' ? delta.delta : '', '甲');
});

test('未知展示部件降级为 unknownPart，已知部件完整解析', () => {
  const unknown = parseSseData(
    JSON.stringify({
      type: 'PART_SNAPSHOT',
      messageId: 'm',
      partId: 'p',
      part: { type: 'VIDEO', url: 'x' },
    }),
  );
  assert.equal(unknown.kind, 'unknownPart');
  assert.equal(unknown.kind === 'unknownPart' ? unknown.rawType : '', 'VIDEO');

  const question = parseSseData(
    JSON.stringify({
      type: 'PART_SNAPSHOT',
      messageId: 'm',
      partId: 'q',
      part: {
        type: 'QUESTION',
        questionId: 'Q1',
        prompt: '口径？',
        mode: 'SINGLE',
        required: true,
        options: [{ value: 'CASE', label: '立案' }],
        maxLength: 100,
      },
    }),
  );
  assert.equal(question.kind, 'event');
  const part =
    question.kind === 'event' && question.event.type === 'PART_SNAPSHOT'
      ? (question.event.part as MessagePart)
      : undefined;
  assert.equal(part?.type, 'QUESTION');
  assert.equal(part?.type === 'QUESTION' ? part.maxLength : undefined, 100);
});

test('未知事件与损坏的已知事件必须报错，不能静默成功', () => {
  assert.throws(
    () => parseSseData(JSON.stringify({ type: 'MODEL_THINKING', text: '...' })),
    ChatProtocolError,
  );
  assert.throws(
    () => parseSseData(JSON.stringify({ type: 'TEXT_DELTA', messageId: 'm' })),
    ChatProtocolError,
  );
  assert.throws(() => parseSseData('{'), ChatProtocolError);
  assert.throws(
    () =>
      parseSseData(
        JSON.stringify({
          type: 'STREAM_START',
          protocolVersion: 2,
          messageId: 'm',
          generationId: 'g',
          clientConversationId: 'c',
          conversationId: 'srv-c',
          executionMode: 'LOCAL_STUB',
        }),
      ),
    ChatProtocolError,
  );
});

test('单事件超过上限时报错且错误信息不含载荷', () => {
  const parser = new SseFrameParser();
  const huge = 'x'.repeat(300 * 1024);
  try {
    parser.push(`data: ${huge}\n\n`);
    assert.fail('应当抛出超限错误');
  } catch (error) {
    assert.ok(error instanceof ChatProtocolError);
    assert.ok((error as Error).message.length < 80);
  }
});
