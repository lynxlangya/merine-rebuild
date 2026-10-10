import type { ChatEvent, MessagePart } from '@merine/api-contract';

/**
 * 演示对话的流式解析：SSE 拆帧 → JSON → 判别联合。
 *
 * 规则（与设计稿 §4 对齐）：
 * - 拆帧处理增量 UTF-8、任意字节切分、CR/LF/CRLF、多行 data、注释与空行；不自动重连。
 * - 未知**事件**、损坏的已知事件都抛 {@link ChatProtocolError}，不能静默成功；
 *   未知**展示部件**降级为 unknownPart，由界面渲染升级占位。
 * - 单事件与累计缓冲都有上限；错误信息不携带整个载荷。
 */

export class ChatProtocolError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'ChatProtocolError';
  }
}

export type StreamItem =
  | { kind: 'event'; event: ChatEvent }
  | { kind: 'unknownPart'; messageId: string; partId: string; rawType: string };

export interface SseFrame {
  id?: string;
  data: string;
}

const MAX_EVENT_CHARS = 256 * 1024;
const MAX_PENDING_CHARS = 512 * 1024;

const EVENT_TYPES = new Set([
  'STREAM_START',
  'PART_START',
  'TEXT_DELTA',
  'PART_SNAPSHOT',
  'PART_DONE',
  'CANCEL_REQUESTED',
  'MESSAGE_DONE',
  'ERROR',
]);

const PART_TYPES = new Set(['TEXT', 'TABLE', 'CHART', 'SOURCES', 'STEPS', 'QUESTION', 'NOTICE']);

const PART_STATUS = new Set(['SUCCEEDED', 'AWAITING_INPUT', 'ABORTED']);
const EXECUTION_MODES = new Set(['LOCAL_STUB', 'PROVIDER']);
const QUESTION_MODES = new Set(['SINGLE', 'MULTIPLE', 'TEXT']);
const NOTICE_LEVELS = new Set(['INFO', 'WARNING', 'ERROR']);
const SOURCE_KINDS = new Set(['TASK', 'FLOW', 'INTEL', 'MENU', 'EXTERNAL']);

export class SseFrameParser {
  private buffer = '';
  private data: string[] = [];
  private id: string | undefined;
  private eventSize = 0;

  push(text: string): SseFrame[] {
    this.buffer += text;
    if (this.buffer.length > MAX_PENDING_CHARS) {
      throw new ChatProtocolError('SSE 缓冲超过上限');
    }
    const frames: SseFrame[] = [];
    for (;;) {
      const breakAt = this.findLineBreak();
      if (breakAt < 0) break;
      const line = this.buffer.slice(0, breakAt);
      const width = this.buffer[breakAt] === '\r' && this.buffer[breakAt + 1] === '\n' ? 2 : 1;
      this.buffer = this.buffer.slice(breakAt + width);
      const frame = this.consumeLine(line);
      if (frame) frames.push(frame);
    }
    return frames;
  }

  /** 处理没有以空行收尾的最后一帧与残留换行。 */
  end(): SseFrame[] {
    const frames: SseFrame[] = [];
    if (this.buffer.length > 0) {
      let line = this.buffer;
      this.buffer = '';
      if (line.endsWith('\r')) line = line.slice(0, -1);
      const frame = this.consumeLine(line);
      if (frame) frames.push(frame);
    }
    const last = this.dispatch();
    if (last) frames.push(last);
    return frames;
  }

  private findLineBreak(): number {
    for (let index = 0; index < this.buffer.length; index++) {
      const char = this.buffer[index];
      if (char === '\n' || char === '\r') return index;
    }
    return -1;
  }

  private consumeLine(line: string): SseFrame | null {
    if (line === '') return this.dispatch();
    if (line.startsWith(':')) return null;
    const colon = line.indexOf(':');
    const field = colon === -1 ? line : line.slice(0, colon);
    let value = colon === -1 ? '' : line.slice(colon + 1);
    if (value.startsWith(' ')) value = value.slice(1);
    if (field === 'data') {
      this.eventSize += value.length;
      if (this.eventSize > MAX_EVENT_CHARS) {
        throw new ChatProtocolError('SSE 单事件超过上限');
      }
      this.data.push(value);
    } else if (field === 'id') {
      this.id = value;
    }
    return null;
  }

  private dispatch(): SseFrame | null {
    if (this.data.length === 0) {
      this.id = undefined;
      this.eventSize = 0;
      return null;
    }
    const frame: SseFrame = { id: this.id, data: this.data.join('\n') };
    this.data = [];
    this.id = undefined;
    this.eventSize = 0;
    return frame;
  }
}

export async function* readChatStream(
  stream: ReadableStream<Uint8Array>,
): AsyncGenerator<StreamItem> {
  const reader = stream.getReader();
  const decoder = new TextDecoder();
  const parser = new SseFrameParser();
  try {
    for (;;) {
      const { value, done } = await reader.read();
      if (done) break;
      for (const frame of parser.push(decoder.decode(value, { stream: true }))) {
        yield parseSseData(frame.data);
      }
    }
    for (const frame of parser.end()) {
      yield parseSseData(frame.data);
    }
  } finally {
    reader.releaseLock();
  }
}

export function parseSseData(data: string): StreamItem {
  let payload: unknown;
  try {
    payload = JSON.parse(data);
  } catch {
    throw new ChatProtocolError('协议事件不是合法 JSON');
  }
  return parseStreamItem(payload);
}

export function parseStreamItem(payload: unknown): StreamItem {
  if (!isRecord(payload)) {
    throw new ChatProtocolError('协议事件必须是对象');
  }
  const type = payload.type;
  if (typeof type !== 'string' || !EVENT_TYPES.has(type)) {
    throw new ChatProtocolError('收到无法识别的协议事件，请升级前端版本');
  }
  switch (type) {
    case 'STREAM_START': {
      const protocolVersion = payload.protocolVersion;
      if (protocolVersion !== 1) {
        throw new ChatProtocolError('协议版本不受支持，请升级前端版本');
      }
      const executionMode = requiredString(payload, 'executionMode');
      if (!EXECUTION_MODES.has(executionMode)) {
        throw new ChatProtocolError('执行模式无法识别');
      }
      return event({
        type,
        protocolVersion: 1,
        messageId: requiredString(payload, 'messageId'),
        generationId: requiredString(payload, 'generationId'),
        clientConversationId: requiredString(payload, 'clientConversationId'),
        // 服务端会话标识：落库后的会话就是它，前端据此把临时会话替换成服务端会话。
        conversationId: requiredString(payload, 'conversationId'),
        executionMode: executionMode as 'LOCAL_STUB' | 'PROVIDER',
        model: optionalString(payload, 'model'),
      });
    }
    case 'PART_START':
      return event({
        type,
        messageId: requiredString(payload, 'messageId'),
        partId: requiredString(payload, 'partId'),
        partType: requiredString(payload, 'partType'),
      });
    case 'TEXT_DELTA':
      return event({
        type,
        messageId: requiredString(payload, 'messageId'),
        partId: requiredString(payload, 'partId'),
        delta: requiredString(payload, 'delta'),
      });
    case 'PART_SNAPSHOT': {
      const messageId = requiredString(payload, 'messageId');
      const partId = requiredString(payload, 'partId');
      const part = payload.part;
      if (!isRecord(part) || typeof part.type !== 'string') {
        throw new ChatProtocolError('部件缺少判别字段');
      }
      if (!PART_TYPES.has(part.type)) {
        return { kind: 'unknownPart', messageId, partId, rawType: part.type };
      }
      return event({ type, messageId, partId, part: parsePart(part) });
    }
    case 'PART_DONE':
      return event({
        type,
        messageId: requiredString(payload, 'messageId'),
        partId: requiredString(payload, 'partId'),
      });
    case 'CANCEL_REQUESTED':
      return event({ type, messageId: requiredString(payload, 'messageId') });
    case 'MESSAGE_DONE': {
      const status = requiredString(payload, 'status');
      if (!PART_STATUS.has(status)) {
        throw new ChatProtocolError('消息终态无法识别');
      }
      return event({
        type,
        messageId: requiredString(payload, 'messageId'),
        status: status as 'SUCCEEDED' | 'AWAITING_INPUT' | 'ABORTED',
        usage: undefined,
      });
    }
    case 'ERROR': {
      const retryable = payload.retryable;
      if (typeof retryable !== 'boolean') {
        throw new ChatProtocolError('错误事件缺少 retryable');
      }
      return event({
        type,
        messageId: optionalString(payload, 'messageId'),
        code: requiredString(payload, 'code') as Extract<ChatEvent, { type: 'ERROR' }>['code'],
        message: requiredString(payload, 'message'),
        retryable,
      });
    }
    default:
      throw new ChatProtocolError('收到无法识别的协议事件，请升级前端版本');
  }
}

function parsePart(part: Record<string, unknown>): MessagePart {
  switch (part.type) {
    case 'TEXT':
      return { type: 'TEXT', text: requiredString(part, 'text') };
    case 'TABLE':
      return {
        type: 'TABLE',
        title: optionalString(part, 'title'),
        columns: requiredArray(part, 'columns'),
        rows: requiredArray(part, 'rows'),
        note: optionalString(part, 'note'),
      } as MessagePart;
    case 'CHART':
      if (!isRecord(part.spec)) throw new ChatProtocolError('图表部件缺少 spec');
      return {
        type: 'CHART',
        spec: {
          chartType: requiredString(part.spec, 'chartType'),
          title: optionalString(part.spec, 'title'),
          unit: optionalString(part.spec, 'unit'),
          categories: optionalArray(part.spec, 'categories'),
          series: requiredArray(part.spec, 'series'),
          meta: isRecord(part.spec.meta) ? part.spec.meta : undefined,
        },
      } as MessagePart;
    case 'SOURCES': {
      const items = requiredArray(part, 'items');
      for (const item of items) {
        if (!isRecord(item) || typeof item.kind !== 'string' || !SOURCE_KINDS.has(item.kind)) {
          throw new ChatProtocolError('来源缺少可识别的类别');
        }
      }
      return { type: 'SOURCES', items } as MessagePart;
    }
    case 'STEPS':
      return { type: 'STEPS', items: requiredArray(part, 'items') } as MessagePart;
    case 'QUESTION': {
      const mode = requiredString(part, 'mode');
      if (!QUESTION_MODES.has(mode)) throw new ChatProtocolError('追问模式无法识别');
      return {
        type: 'QUESTION',
        questionId: requiredString(part, 'questionId'),
        prompt: requiredString(part, 'prompt'),
        mode: mode as 'SINGLE' | 'MULTIPLE' | 'TEXT',
        options: optionalArray(part, 'options'),
        allowOther: optionalBoolean(part, 'allowOther'),
        minSelections: optionalNumber(part, 'minSelections'),
        maxSelections: optionalNumber(part, 'maxSelections'),
        placeholder: optionalString(part, 'placeholder'),
        maxLength: optionalNumber(part, 'maxLength'),
        required: part.required === true,
      } as MessagePart;
    }
    case 'NOTICE': {
      const level = requiredString(part, 'level');
      if (!NOTICE_LEVELS.has(level)) throw new ChatProtocolError('提示级别无法识别');
      return {
        type: 'NOTICE',
        level: level as 'INFO' | 'WARNING' | 'ERROR',
        text: requiredString(part, 'text'),
        code: optionalString(part, 'code'),
      } as MessagePart;
    }
    default:
      throw new ChatProtocolError('部件类型无法识别');
  }
}

function event(event: ChatEvent): StreamItem {
  return { kind: 'event', event };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function requiredString(source: Record<string, unknown>, key: string): string {
  const value = source[key];
  if (typeof value !== 'string' || value.length === 0) {
    throw new ChatProtocolError(`事件字段 ${key} 缺失或格式不正确`);
  }
  return value;
}

function optionalString(source: Record<string, unknown>, key: string): string | undefined {
  const value = source[key];
  return typeof value === 'string' ? value : undefined;
}

function optionalNumber(source: Record<string, unknown>, key: string): number | undefined {
  const value = source[key];
  return typeof value === 'number' && Number.isFinite(value) ? value : undefined;
}

function optionalBoolean(source: Record<string, unknown>, key: string): boolean | undefined {
  const value = source[key];
  return typeof value === 'boolean' ? value : undefined;
}

function requiredArray(source: Record<string, unknown>, key: string): unknown[] {
  const value = source[key];
  if (!Array.isArray(value)) {
    throw new ChatProtocolError(`事件字段 ${key} 缺失或格式不正确`);
  }
  return value;
}

function optionalArray(source: Record<string, unknown>, key: string): unknown[] | undefined {
  const value = source[key];
  return Array.isArray(value) ? value : undefined;
}
