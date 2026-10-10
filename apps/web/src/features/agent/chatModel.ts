import type { ChatRunView, MessagePart, ModelOption } from '@merine/api-contract';
import { errorText } from '../../shared/api-error.ts';
import { ApiError, isAbortError } from '../../shared/http.ts';
import { ChatProtocolError, type StreamItem } from './stream.ts';

/**
 * 演示对话的前端状态模型：会话、消息、部件与追问。
 *
 * 全部是纯函数，便于测试会话隔离、迟到事件与停止竞争；网络与副作用在 useAgentChat 中处理。
 * 客户端消息状态比服务端多一个 INTERRUPTED（断连未收到终态），与设计稿 §4.4 的状态机一致。
 * 部件用协议 partId 标识，事件按 partId 归位，不依赖渲染顺序以外的东西。
 */

export type RunStatus =
  | 'STREAMING'
  | 'CANCEL_REQUESTED'
  | 'SUCCEEDED'
  | 'AWAITING_INPUT'
  | 'FAILED'
  | 'ABORTED'
  | 'INTERRUPTED';

export type PartStatus = 'STREAMING' | 'DONE' | 'FAILED' | 'ABORTED';

/** 未知展示部件降级为 unknown，渲染升级提示而不是丢掉整条消息。 */
export type RenderedPart =
  | { kind: 'known'; partId: string; part: MessagePart; status: PartStatus }
  | { kind: 'unknown'; partId: string; rawType: string; status: PartStatus };

export interface AgentMessage {
  id: string;
  role: 'USER' | 'ASSISTANT';
  status: RunStatus;
  parts: RenderedPart[];
  error?: { code: string; message: string; retryable: boolean };
  createdAt: string;
}

export interface PendingQuestion {
  generationId: string;
  questionId: string;
  answered: boolean;
  values?: string[];
  freeText?: string;
  skipped?: boolean;
}

export interface AgentRun {
  idempotencyKey: string;
  generationId?: string;
  messageId?: string;
  status: RunStatus;
  question?: PendingQuestion;
  retryText?: string;
}

export type ReasoningEffort = ModelOption['reasoningEfforts'][number];

export interface AgentModelSelection {
  providerId: string;
  modelId: string;
  /** 省略时不发送 reasoning_effort，交给供应商默认值。 */
  reasoningEffort?: ReasoningEffort;
}

export interface AgentSession {
  id: string;
  title: string;
  draft: string;
  messages: AgentMessage[];
  run?: AgentRun;
  /** 服务端会话标识：首轮由 STREAM_START 带回，后续轮次沿用它写入同一会话。 */
  conversationId?: string;
  /** 该会话最近一次使用的模型；追问沿用同一选择。 */
  model?: AgentModelSelection;
}

export interface AgentChatState {
  sessions: AgentSession[];
  activeId?: string;
}

export interface AnswerInput {
  questionId: string;
  values: string[];
  freeText?: string;
  skipped?: boolean;
  /** 仅用于回显用户选择的中文标签，不进入请求。 */
  label?: string;
}

const KNOWN_PART_TYPES = new Set([
  'TEXT',
  'TABLE',
  'CHART',
  'SOURCES',
  'STEPS',
  'QUESTION',
  'NOTICE',
]);

export function createSession(id: string, index: number): AgentSession {
  return { id, title: `新对话 ${index}`, draft: '', messages: [] };
}

/**
 * 打开服务端会话：本机已有同 id 的会话就替换它的消息（以服务端为准），
 * 否则新建一个 id 与会话 id 相同的本地会话，并置为当前会话。
 * activate=false 用于异步历史回填，保留当前选择与草稿，避免迟到响应抢回焦点。
 */
export function openServerSession(
  state: AgentChatState,
  server: {
    id: string;
    title: string;
    messages: AgentMessage[];
    /** 会话最近使用的模型与档位：打开会话时回填到输入区，避免误用另一个模型继续问。 */
    model?: AgentModelSelection;
  },
  activate = true,
): AgentChatState {
  const existing = state.sessions.find((session) => session.id === server.id);
  const sessions = existing
    ? state.sessions.map((session) =>
        session.id === server.id
          ? {
              ...session,
              title: server.title,
              messages: server.messages,
              draft: activate ? '' : session.draft,
              model: server.model ?? session.model,
            }
          : session,
      )
    : [
        ...state.sessions,
        {
          id: server.id,
          title: server.title,
          draft: '',
          messages: server.messages,
          conversationId: server.id,
          model: server.model,
        },
      ];
  return { sessions, activeId: activate ? server.id : state.activeId };
}

export function activeSession(state: AgentChatState): AgentSession | undefined {
  return state.sessions.find((session) => session.id === state.activeId);
}

export function isBusy(session: AgentSession | undefined): boolean {
  const status = session?.run?.status;
  return status === 'STREAMING' || status === 'CANCEL_REQUESTED';
}

export function pendingQuestion(session: AgentSession | undefined): PendingQuestion | undefined {
  const question = session?.run?.question;
  return question && !question.answered ? question : undefined;
}

/** 当前会话里等待回答的问题所在的协议部件。 */
export function questionPart(
  session: AgentSession | undefined,
):
  | { messageId: string; partId: string; part: Extract<MessagePart, { type: 'QUESTION' }> }
  | undefined {
  if (!session) return undefined;
  for (const message of session.messages) {
    for (const rendered of message.parts) {
      if (rendered.kind === 'known' && rendered.part.type === 'QUESTION') {
        return { messageId: message.id, partId: rendered.partId, part: rendered.part };
      }
    }
  }
  return undefined;
}

export function messageText(message: AgentMessage): string {
  return message.parts
    .map((part) => (part.kind === 'known' && part.part.type === 'TEXT' ? part.part.text : ''))
    .join('');
}

export function lastMessage(session: AgentSession | undefined): AgentMessage | undefined {
  return session?.messages[session.messages.length - 1];
}

export function setDraft(state: AgentChatState, sessionId: string, draft: string): AgentChatState {
  return patchSession(state, sessionId, (session) => ({ ...session, draft }));
}

/**
 * 新会话：当前会话就是空草稿时直接复用，否则新建。
 * 没有这一步时每次点「新聊天」都会多出一条空会话，点几次就堆一排。
 */
export function startNewSession(state: AgentChatState, id: string): AgentChatState {
  const active = activeSession(state);
  if (active && !active.conversationId && active.messages.length === 0) {
    return { ...state, activeId: active.id };
  }
  const empty = state.sessions.find(
    (session) => !session.conversationId && session.messages.length === 0,
  );
  if (empty) return { ...state, activeId: empty.id };
  return addSession(state, createSession(id, state.sessions.length + 1));
}

export function addSession(state: AgentChatState, session: AgentSession): AgentChatState {
  return { sessions: [session, ...state.sessions], activeId: session.id };
}

export function selectSession(state: AgentChatState, sessionId: string): AgentChatState {
  return state.sessions.some((session) => session.id === sessionId)
    ? { ...state, activeId: sessionId }
    : state;
}

/**
 * 开始一轮发送：立刻上屏用户消息、记录原意图（用于重试）并进入 STREAMING。
 * 追问作答沿用 pending 的 generationId，并把该问题标记为已答（服务端仍会独立校验一次）。
 */
export function startTurn(
  state: AgentChatState,
  sessionId: string,
  input: {
    messageId: string;
    text: string;
    idempotencyKey: string;
    generationId?: string;
    answer?: AnswerInput;
    model?: AgentModelSelection;
  },
): AgentChatState {
  return patchSession(state, sessionId, (session) => {
    const userMessage: AgentMessage = {
      id: input.messageId,
      role: 'USER',
      status: 'SUCCEEDED',
      parts: [
        {
          kind: 'known',
          partId: `user-${input.messageId}`,
          part: { type: 'TEXT', text: input.text },
          status: 'DONE',
        },
      ],
      createdAt: new Date().toISOString(),
    };
    const firstTurn = session.messages.length === 0;
    return {
      ...session,
      title: firstTurn ? titleFrom(input.text) : session.title,
      draft: '',
      model: input.model ?? session.model,
      messages: [...session.messages, userMessage],
      run: {
        idempotencyKey: input.idempotencyKey,
        generationId: input.generationId,
        status: 'STREAMING',
        // 作答先记为待确认：服务端在 STREAM_START 接受后才锁定（见 applyStreamItem）。
        question: input.answer
          ? session.run?.question
            ? {
                ...session.run.question,
                answered: false,
                values: input.answer.values,
                freeText: input.answer.freeText,
                skipped: input.answer.skipped ?? false,
              }
            : undefined
          : undefined,
        retryText: input.text,
      },
    };
  });
}

export function stopRequested(state: AgentChatState, sessionId: string): AgentChatState {
  return patchRun(state, sessionId, (run) =>
    run.status === 'STREAMING' ? { ...run, status: 'CANCEL_REQUESTED' } : run,
  );
}

/** 服务端确认的失败/中断：保留已接收内容，附错误与重试标记。 */
export function failRun(
  state: AgentChatState,
  sessionId: string,
  input: {
    messageId?: string;
    code: string;
    message: string;
    retryable: boolean;
    status: Extract<RunStatus, 'FAILED' | 'INTERRUPTED' | 'ABORTED'>;
  },
): AgentChatState {
  const partStatus: PartStatus = input.status === 'ABORTED' ? 'ABORTED' : 'FAILED';
  return patchSession(state, sessionId, (session) => {
    const targetId = input.messageId ?? session.run?.messageId;
    return {
      ...session,
      messages: targetId
        ? session.messages.map((message) =>
            message.id === targetId
              ? {
                  ...message,
                  status: input.status,
                  error: { code: input.code, message: input.message, retryable: input.retryable },
                  parts: settleStreaming(message.parts, partStatus),
                }
              : message,
          )
        : session.messages,
      run: session.run
        ? {
            ...session.run,
            status: input.status,
            // 作答在 STREAM_START 之前失败：清除未获服务端确认的答案，恢复可再次作答。
            question: resetPendingAnswer(session.run.question),
          }
        : session.run,
    };
  });
}

function resetPendingAnswer(question: PendingQuestion | undefined): PendingQuestion | undefined {
  if (!question || question.answered || question.values === undefined) {
    return question;
  }
  return { ...question, values: undefined, freeText: undefined, skipped: undefined };
}

export interface StreamFailure {
  code: string;
  message: string;
  retryable: boolean;
  status: Extract<RunStatus, 'FAILED' | 'INTERRUPTED'>;
}

/**
 * 区分「响应已明确的失败」和「结果待确认」：
 * - 服务端已返回的 ApiError（如 409/429/5xx JSON）可以标记 FAILED；
 * - 读取中断、网络异常、协议损坏都进入 INTERRUPTED，先核对原执行再决定是否重试。
 */
export function describeStreamFailure(error: unknown, streamStarted: boolean): StreamFailure {
  if (isAbortError(error)) {
    return {
      code: 'ABORTED_LOCALLY',
      message: '已停止接收，服务端结果待确认',
      retryable: true,
      status: 'INTERRUPTED',
    };
  }
  if (error instanceof ChatProtocolError) {
    return {
      code: 'PROTOCOL_ERROR',
      message: error.message,
      retryable: false,
      status: 'INTERRUPTED',
    };
  }
  if (error instanceof ApiError && !streamStarted) {
    return {
      code: error.code,
      message: errorText(error),
      retryable: error.status === 0 || error.status >= 500,
      status: 'FAILED',
    };
  }
  return {
    code: error instanceof ApiError ? error.code : 'INCOMPLETE',
    message: error instanceof ApiError ? errorText(error) : '连接中断，结果可能不完整',
    retryable: true,
    status: 'INTERRUPTED',
  };
}

/** 重试：不重复添加用户消息，只把执行重置为 STREAMING。 */
export function retryTurn(
  state: AgentChatState,
  sessionId: string,
  idempotencyKey: string,
): AgentChatState {
  return patchRun(state, sessionId, (run) => ({
    ...run,
    idempotencyKey,
    status: 'STREAMING',
    messageId: undefined,
  }));
}

/** 用服务端快照恢复/校准一次执行：断连核对、同 key 回放与停止结果共用。 */
export function applyRunView(
  state: AgentChatState,
  sessionId: string,
  view: ChatRunView,
): AgentChatState {
  const status = normalizeRunStatus(view.status);
  const parts: RenderedPart[] = (view.parts ?? []).map((item, index) => {
    const partId = item.partId ?? `snapshot-${index}`;
    const partStatus = normalizePartStatus(item.status);
    return item.part
      ? { kind: 'known', partId, part: item.part, status: partStatus }
      : { kind: 'unknown', partId, rawType: 'UNKNOWN', status: partStatus };
  });
  return patchSession(state, sessionId, (session) => {
    const messages = [...session.messages];
    // 只按当前执行的 messageId 定位：本轮尚未收到 STREAM_START 时不能复用上一轮的助手消息。
    const targetId = session.run?.messageId;
    const target =
      targetId === undefined
        ? -1
        : messages.findIndex((message) => message.id === targetId && message.role === 'ASSISTANT');
    const snapshotId = targetId ?? `snapshot-${view.generationId ?? view.idempotencyKey}`;
    const snapshot: AgentMessage = {
      id: snapshotId,
      role: 'ASSISTANT',
      status,
      parts,
      createdAt: new Date().toISOString(),
    };
    if (target < 0) {
      messages.push(snapshot);
    } else {
      messages[target] = { ...messages[target], status, parts };
    }
    return {
      ...session,
      messages,
      run: {
        idempotencyKey: view.idempotencyKey,
        generationId: view.generationId,
        messageId: target < 0 ? snapshotId : messages[target].id,
        status,
        retryText: session.run?.retryText,
        question: view.question
          ? {
              generationId: view.generationId ?? '',
              questionId: view.question.questionId ?? '',
              answered: view.question.answered ?? false,
              values: view.question.values,
              freeText: view.question.freeText,
            }
          : undefined,
      },
    };
  });
}

function normalizeRunStatus(status: ChatRunView['status'] | undefined): RunStatus {
  switch (status) {
    case 'RUNNING':
      return 'STREAMING';
    case 'SUCCEEDED':
    case 'AWAITING_INPUT':
    case 'FAILED':
    case 'ABORTED':
      return status;
    default:
      return 'INTERRUPTED';
  }
}

function normalizePartStatus(status: string | undefined): PartStatus {
  return status === 'STREAMING' || status === 'FAILED' || status === 'ABORTED' ? status : 'DONE';
}

export function markAnswered(
  state: AgentChatState,
  sessionId: string,
  answer: AnswerInput,
): AgentChatState {
  return patchRun(state, sessionId, (run) =>
    run.question && run.question.questionId === answer.questionId
      ? {
          ...run,
          question: {
            ...run.question,
            answered: true,
            values: answer.values,
            freeText: answer.freeText,
            skipped: answer.skipped ?? false,
          },
        }
      : run,
  );
}

/** 应用一个协议事件；消息标识不匹配（迟到事件、别的会话）时原样返回，避免串写。 */
export function applyStreamItem(
  state: AgentChatState,
  sessionId: string,
  item: StreamItem,
): AgentChatState {
  if (item.kind === 'unknownPart') {
    return patchMessage(state, sessionId, item.messageId, (message) => ({
      ...message,
      parts: upsertUnknown(message.parts, item.partId, item.rawType),
    }));
  }
  const event = item.event;
  switch (event.type) {
    case 'STREAM_START':
      return patchSession(state, sessionId, (session) => {
        if (session.messages.some((message) => message.id === event.messageId)) return session;
        return {
          ...session,
          // STREAM_START 带服务端会话标识：首轮拿到后，后续轮次都写进同一个会话。
          conversationId: event.conversationId ?? session.conversationId,
          messages: [
            ...session.messages,
            {
              id: event.messageId,
              role: 'ASSISTANT',
              status: 'STREAMING',
              parts: [],
              createdAt: new Date().toISOString(),
            },
          ],
          run: session.run
            ? {
                ...session.run,
                generationId: event.generationId,
                messageId: event.messageId,
                question: session.run.question
                  ? { ...session.run.question, answered: true }
                  : undefined,
              }
            : session.run,
        };
      });
    case 'PART_START':
      if (event.partType !== 'TEXT' && !KNOWN_PART_TYPES.has(event.partType)) {
        return patchMessage(state, sessionId, event.messageId, (message) => ({
          ...message,
          parts: upsertUnknown(message.parts, event.partId, event.partType),
        }));
      }
      if (event.partType !== 'TEXT') {
        return state;
      }
      return patchMessage(state, sessionId, event.messageId, (message) => ({
        ...message,
        parts: upsertPart(message.parts, event.partId, { type: 'TEXT', text: '' }),
      }));
    case 'TEXT_DELTA':
      return patchMessage(state, sessionId, event.messageId, (message) => ({
        ...message,
        parts: message.parts.map((rendered) =>
          rendered.kind === 'known' &&
          rendered.partId === event.partId &&
          rendered.part.type === 'TEXT'
            ? { ...rendered, part: { ...rendered.part, text: rendered.part.text + event.delta } }
            : rendered,
        ),
      }));
    case 'PART_SNAPSHOT': {
      const next = patchMessage(state, sessionId, event.messageId, (message) => ({
        ...message,
        parts: upsertPart(message.parts, event.partId, event.part),
      }));
      return event.part.type === 'QUESTION'
        ? patchRun(next, sessionId, (run) => ({
            ...run,
            question: {
              generationId: run.generationId ?? '',
              questionId: event.part.type === 'QUESTION' ? event.part.questionId : '',
              answered: false,
            },
          }))
        : next;
    }
    case 'PART_DONE':
      return patchMessage(state, sessionId, event.messageId, (message) => ({
        ...message,
        parts: markPart(message.parts, event.partId, 'DONE'),
      }));
    case 'CANCEL_REQUESTED':
      return patchRun(state, sessionId, (run) =>
        run.status === 'STREAMING' ? { ...run, status: 'CANCEL_REQUESTED' } : run,
      );
    case 'MESSAGE_DONE':
      return patchSession(state, sessionId, (session) => ({
        ...session,
        messages: session.messages.map((message) =>
          message.id === event.messageId
            ? {
                ...message,
                status: event.status,
                parts: settleStreaming(
                  message.parts,
                  event.status === 'ABORTED' ? 'ABORTED' : 'DONE',
                ),
              }
            : message,
        ),
        run: session.run ? { ...session.run, status: event.status } : session.run,
      }));
    case 'ERROR':
      return failRun(state, sessionId, {
        messageId: event.messageId ?? undefined,
        code: event.code,
        message: event.message,
        retryable: event.retryable,
        status: 'FAILED',
      });
    default:
      return state;
  }
}

function titleFrom(text: string): string {
  const trimmed = text.trim().replace(/\s+/g, ' ');
  return trimmed.length > 16 ? `${trimmed.slice(0, 16)}…` : trimmed || '新对话';
}

function patchSession(
  state: AgentChatState,
  sessionId: string,
  update: (session: AgentSession) => AgentSession,
): AgentChatState {
  return {
    ...state,
    sessions: state.sessions.map((session) =>
      session.id === sessionId ? update(session) : session,
    ),
  };
}

function patchRun(
  state: AgentChatState,
  sessionId: string,
  update: (run: AgentRun) => AgentRun,
): AgentChatState {
  return patchSession(state, sessionId, (session) =>
    session.run ? { ...session, run: update(session.run) } : session,
  );
}

function patchMessage(
  state: AgentChatState,
  sessionId: string,
  messageId: string,
  update: (message: AgentMessage) => AgentMessage,
): AgentChatState {
  return patchSession(state, sessionId, (session) => ({
    ...session,
    messages: session.messages.map((message) =>
      message.id === messageId ? update(message) : message,
    ),
  }));
}

function upsertPart(parts: RenderedPart[], partId: string, part: MessagePart): RenderedPart[] {
  const index = parts.findIndex((rendered) => rendered.partId === partId);
  const next: RenderedPart = { kind: 'known', partId, part, status: 'STREAMING' };
  if (index < 0) return [...parts, next];
  const copy = [...parts];
  copy[index] = next;
  return copy;
}

function upsertUnknown(parts: RenderedPart[], partId: string, rawType: string): RenderedPart[] {
  const index = parts.findIndex((rendered) => rendered.partId === partId);
  const next: RenderedPart = { kind: 'unknown', partId, rawType, status: 'STREAMING' };
  if (index < 0) return [...parts, next];
  const copy = [...parts];
  copy[index] = next;
  return copy;
}

function markPart(parts: RenderedPart[], partId: string, status: PartStatus): RenderedPart[] {
  return parts.map((rendered) => (rendered.partId === partId ? { ...rendered, status } : rendered));
}

function settleStreaming(parts: RenderedPart[], status: PartStatus): RenderedPart[] {
  return parts.map((part) => (part.status === 'STREAMING' ? { ...part, status } : part));
}
