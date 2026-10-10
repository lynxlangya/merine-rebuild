import { useCallback, useEffect, useRef, useState } from 'react';
import type { ChatRequest } from '@merine/api-contract';
import { ApiError, isAbortError } from '../../shared/http';
import { useAuth } from '../auth/public';
import { readCurrentConversation, rememberCurrentConversation } from './currentConversation';
import { errorText } from '../../shared/api-error';
import { fetchChatRun, startChat, stopChatRun } from './api';
import {
  activeSession,
  addSession,
  applyRunView,
  applyStreamItem,
  createSession,
  describeStreamFailure,
  failRun,
  isBusy,
  messageText,
  pendingQuestion,
  retryTurn,
  openServerSession as openServerSessionState,
  selectSession as selectSessionState,
  setDraft as setDraftState,
  startNewSession as startNewSessionState,
  startTurn,
  stopRequested,
  type AgentChatState,
  type AgentModelSelection,
  type AgentSession,
  type AnswerInput,
  type PendingQuestion,
} from './chatModel';
import { readChatStream } from './stream';
import type { ChatConversation } from '@merine/api-contract';
import { useQueryClient } from '@tanstack/react-query';
import { chatKeys, fetchConversation, fetchConversationMessages } from './api';
import { messagesFromServer, modelFromConversation } from './history';
import { useModelOptionsQuery } from '../model-providers/public';

export interface AgentChatApi {
  state: AgentChatState;
  session?: AgentSession;
  busy: boolean;
  question?: PendingQuestion;
  /** 该会话仍在从服务端加载（历史消息还没回来）。 */
  loadingConversation: boolean;
  conversationError?: string;
  retryConversation: () => void;
  newSession: () => void;
  selectSession: (id: string) => void;
  /** 打开服务端会话：拉取它的消息、回填它最近使用的模型，并置为当前会话。 */
  openConversation: (conversation: ChatConversation) => void;
  setDraft: (id: string, text: string) => void;
  send: (text: string, model?: AgentModelSelection) => void;
  stop: () => void;
  retry: () => void;
  answer: (input: AnswerInput) => void;
}

/**
 * 对话的会话与流式协调器：
 * 创建/切换会话（含打开服务端会话）、发起与中止执行、回答追问、断连后先核对再重试。
 *
 * <p>页面内存里保存的是「正在进行的这一份」：流式部件、待答问题与草稿都只在内存；
 * 一轮结束后服务端已落库，刷新或从侧栏重新打开会从服务端恢复文本历史。
 */
export function useAgentChat(): AgentChatApi {
  const { state: auth } = useAuth();
  const userId = auth.status === 'authenticated' ? auth.user.id : '';
  const [restoreId, setRestoreId] = useState(() =>
    readCurrentConversation(() => window.sessionStorage, userId),
  );
  const [state, setState] = useState<AgentChatState>(() =>
    restoreId
      ? openServerSessionState(
          { sessions: [] },
          { id: restoreId, title: '正在恢复会话…', messages: [] },
        )
      : { sessions: [] },
  );
  const stateRef = useRef(state);
  stateRef.current = state;
  const controllers = useRef(new Map<string, AbortController>());
  const client = useQueryClient();
  const historyController = useRef<AbortController | undefined>(undefined);
  const [loadingId, setLoadingId] = useState<string | undefined>(restoreId);
  const [conversationError, setConversationError] = useState<string>();
  const modelOptions = useModelOptionsQuery();
  const session = activeSession(state);
  const loadingConversation = Boolean(loadingId && session?.id === loadingId);

  useEffect(() => {
    rememberCurrentConversation(() => window.sessionStorage, userId, session?.conversationId);
  }, [userId, session?.conversationId]);

  const cancelHistory = useCallback(() => {
    historyController.current?.abort();
    setRestoreId(undefined);
    setLoadingId(undefined);
    setConversationError(undefined);
  }, []);

  const runStream = useCallback(
    async (sessionId: string, request: ChatRequest) => {
      controllers.current.get(sessionId)?.abort();
      const controller = new AbortController();
      controllers.current.set(sessionId, controller);
      let settled = false;
      let streamStarted = false;
      try {
        const result = await startChat(request, controller.signal);
        if (result.kind === 'snapshot') {
          setState((previous) => applyRunView(previous, sessionId, result.view));
          settled = true;
          return;
        }
        streamStarted = true;
        const body = result.response.body;
        if (!body) throw new ApiError('响应没有可读取的流', 200, 'EMPTY_STREAM');
        for await (const item of readChatStream(body)) {
          setState((previous) => applyStreamItem(previous, sessionId, item));
          if (
            item.kind === 'event' &&
            (item.event.type === 'MESSAGE_DONE' || item.event.type === 'ERROR')
          ) {
            settled = true;
            break;
          }
        }
      } catch (error) {
        settled = true;
        const failure = describeStreamFailure(error, streamStarted);
        setState((previous) => failRun(previous, sessionId, failure));
      } finally {
        controllers.current.delete(sessionId);
        if (!settled) {
          setState((previous) =>
            failRun(previous, sessionId, {
              code: 'INCOMPLETE',
              message: '连接中断，结果可能不完整',
              retryable: true,
              status: 'INTERRUPTED',
            }),
          );
        }
        // 一轮结束后服务端已经落库：刷新会话列表，标题与「最近更新」随之更新。
        void client.invalidateQueries({ queryKey: chatKeys.conversations });
      }
    },
    [client],
  );

  const newSession = useCallback(() => {
    cancelHistory();
    setState((previous) => startNewSessionState(previous, crypto.randomUUID()));
  }, [cancelHistory]);

  const selectSession = useCallback(
    (id: string) => {
      cancelHistory();
      const current = stateRef.current;
      const leaving = activeSession(current);
      if (leaving && leaving.id !== id && isBusy(leaving) && leaving.run) {
        // 切换会话：请求停止旧执行并终止本地接收；终态确认由服务端事件决定。
        void stopChatRun(leaving.run.idempotencyKey).catch(() => undefined);
        controllers.current.get(leaving.id)?.abort();
        setState((previous) => stopRequested(previous, leaving.id));
      }
      setState((previous) => selectSessionState(previous, id));
    },
    [cancelHistory],
  );

  const setDraft = useCallback((id: string, text: string) => {
    setState((previous) => setDraftState(previous, id, text));
  }, []);

  /** 加载与刷新只回填目标会话，不允许迟到响应改变用户后来选择的会话。 */
  const loadConversation = useCallback(
    (id: string, known?: ChatConversation, background = false) => {
      historyController.current?.abort();
      const controller = new AbortController();
      historyController.current = controller;
      if (!background) {
        setLoadingId(id);
        setConversationError(undefined);
      }
      void Promise.all([
        known ? Promise.resolve(known) : fetchConversation(id, controller.signal),
        fetchConversationMessages(id, controller.signal),
      ])
        .then(([conversation, messages]) => {
          if (controller.signal.aborted) return;
          const available = (modelOptions.data ?? []).map((option) => ({
            providerId: option.providerId,
            modelId: option.modelId,
          }));
          const stored = messagesFromServer(messages);
          setState((previous) => {
            const target = previous.sessions.find((item) => item.id === id);
            if (!target || isBusy(target)) return previous;
            if (background && target.messages.length >= stored.length) return previous;
            return openServerSessionState(
              previous,
              {
                id,
                title: conversation.title,
                messages: stored,
                model: modelFromConversation(conversation, available),
              },
              false,
            );
          });
        })
        .catch((error: unknown) => {
          if (controller.signal.aborted || isAbortError(error)) return;
          if (background) return;
          if (error instanceof ApiError && error.status === 404) {
            setState((previous) => ({
              sessions: previous.sessions.filter((item) => item.id !== id),
              activeId: previous.activeId === id ? undefined : previous.activeId,
            }));
          } else {
            // 网络失败保留会话标识，禁止按空历史继续发送，并提供重试。
            setConversationError(errorText(error));
          }
        })
        .finally(() => {
          if (controller.signal.aborted) return;
          if (historyController.current === controller) {
            setLoadingId(undefined);
            setRestoreId(undefined);
          }
        });
      return controller;
    },
    [modelOptions.data],
  );

  // 模型选项到达后再恢复，以便连同该会话最近使用的模型与推理强度一起回填。
  useEffect(() => {
    if (!restoreId || modelOptions.isPending) return;
    const controller = loadConversation(restoreId);
    return () => controller.abort();
  }, [restoreId, modelOptions.isPending, loadConversation]);

  const openConversation = useCallback(
    (conversation: ChatConversation) => {
      const { id, title } = conversation;
      const existing = stateRef.current.sessions.find((item) => item.id === id);
      selectSession(id);
      if (!existing) {
        setState((previous) => openServerSessionState(previous, { id, title, messages: [] }));
      }
      if (!isBusy(existing)) loadConversation(id, conversation, Boolean(existing?.messages.length));
    },
    [loadConversation, selectSession],
  );

  const retryConversation = useCallback(() => {
    const id = activeSession(stateRef.current)?.conversationId;
    if (id) loadConversation(id);
  }, [loadConversation]);

  const send = useCallback(
    (raw: string, model?: AgentModelSelection) => {
      const text = raw.trim();
      if (!text) return;
      const current = stateRef.current;
      let target = activeSession(current);
      if (!target) {
        target = createSession(crypto.randomUUID(), current.sessions.length + 1);
        setState((previous) => addSession(previous, target!));
      }
      if (isBusy(target) || pendingQuestion(target)) return;
      const sessionId = target.id;
      const chosen = model ?? target.model;
      const history = requestHistory(target);
      const idempotencyKey = crypto.randomUUID();
      setState((previous) =>
        startTurn(previous, sessionId, {
          messageId: `u-${crypto.randomUUID()}`,
          text,
          idempotencyKey,
          model: chosen,
        }),
      );
      void runStream(sessionId, {
        idempotencyKey,
        clientConversationId: sessionId,
        conversationId: target.conversationId,
        clientTimeZone: timeZone(),
        providerId: chosen?.providerId,
        modelId: chosen?.modelId,
        reasoningEffort: chosen?.reasoningEffort,
        messages: [...history, { role: 'USER', text }],
      });
    },
    [runStream],
  );

  const stop = useCallback(() => {
    const current = stateRef.current;
    const target = activeSession(current);
    const run = target?.run;
    if (!target || !run || !isBusy(target)) return;
    void stopChatRun(run.idempotencyKey)
      .then(() => setState((previous) => stopRequested(previous, target.id)))
      .catch(() => undefined);
  }, []);

  const answer = useCallback(
    (input: AnswerInput) => {
      const current = stateRef.current;
      const target = activeSession(current);
      const pending = pendingQuestion(target);
      if (!target || !pending || isBusy(target)) return;
      const text = input.skipped
        ? '跳过该问题'
        : (input.label ?? [...input.values, input.freeText ?? ''].filter(Boolean).join('、'));
      const history = requestHistory(target);
      const idempotencyKey = crypto.randomUUID();
      setState((previous) =>
        startTurn(previous, target.id, {
          messageId: `u-${crypto.randomUUID()}`,
          text: text || '继续',
          idempotencyKey,
          generationId: pending.generationId,
          answer: input,
        }),
      );
      void runStream(target.id, {
        idempotencyKey,
        generationId: pending.generationId,
        clientConversationId: target.id,
        conversationId: target.conversationId,
        clientTimeZone: timeZone(),
        messages: [...history, { role: 'USER', text: text || '继续' }],
        answers: [
          {
            questionId: input.questionId,
            values: input.values,
            freeText: input.freeText,
            skipped: input.skipped ?? false,
          },
        ],
      });
    },
    [runStream],
  );

  const retry = useCallback(() => {
    const current = stateRef.current;
    const target = activeSession(current);
    const run = target?.run;
    if (!target || !run || isBusy(target) || !run.retryText) return;
    const history = requestHistory(target);
    void (async () => {
      if (run.status === 'INTERRUPTED' || run.status === 'CANCEL_REQUESTED') {
        try {
          const view = await fetchChatRun(run.idempotencyKey);
          if (view.status === 'RUNNING') {
            // 原执行仍在运行：保持可再次核对的待确认状态，避免永久停在生成中。
            setState((previous) =>
              failRun(previous, target.id, {
                code: 'RUNNING_UNCONFIRMED',
                message: '原执行仍在运行，请稍后再次核对',
                retryable: true,
                status: 'INTERRUPTED',
              }),
            );
            return;
          }
          setState((previous) => applyRunView(previous, target.id, view));
          return;
        } catch (error) {
          if (!(error instanceof ApiError && error.status === 404)) {
            setState((previous) =>
              failRun(previous, target.id, {
                code: error instanceof ApiError ? error.code : 'UNKNOWN',
                message: errorText(error),
                retryable: true,
                status: 'INTERRUPTED',
              }),
            );
            return;
          }
        }
      }
      const idempotencyKey = crypto.randomUUID();
      const chosen = target.model;
      setState((previous) => retryTurn(previous, target.id, idempotencyKey));
      void runStream(target.id, {
        idempotencyKey,
        clientConversationId: target.id,
        conversationId: target.conversationId,
        clientTimeZone: timeZone(),
        providerId: chosen?.providerId,
        modelId: chosen?.modelId,
        reasoningEffort: chosen?.reasoningEffort,
        messages: [...history, { role: 'USER', text: run.retryText! }],
      });
    })();
  }, [runStream]);

  useEffect(
    () => () => {
      controllers.current.forEach((controller) => controller.abort());
      controllers.current.clear();
      historyController.current?.abort();
    },
    [],
  );

  return {
    state,
    session,
    busy: isBusy(session),
    question: pendingQuestion(session),
    loadingConversation,
    conversationError,
    retryConversation,
    newSession,
    selectSession,
    openConversation,
    setDraft,
    send,
    stop,
    retry,
    answer,
  };
}

function requestHistory(session: AgentSession) {
  return session.messages
    .slice(-20)
    .map((message) => ({ role: message.role, text: messageText(message).slice(0, 8000) }))
    .filter((turn) => turn.text.length > 0);
}

function timeZone(): string {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
  } catch {
    return 'UTC';
  }
}
