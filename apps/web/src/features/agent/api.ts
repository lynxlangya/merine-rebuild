import type { ChatRequest, ChatRunView, ChatRuntime } from '@merine/api-contract';
import type {
  ChatConversation,
  ChatConversationMessage,
  ChatConversationPage,
  ChatRunRecordPage,
  ChatRunStats,
} from '@merine/api-contract';
export type { ChatRuntime };
import { ApiError, readEnvelope, request, requestStream } from '../../shared/http';

export const chatKeys = {
  runtime: ['agent', 'chat-runtime'] as const,
  conversations: ['agent', 'conversations'] as const,
  runs: (state: string, page: number, filter: RunFilter) =>
    ['agent', 'chat-runs', state, page, filter] as const,
  runStats: (filter: RunStatsFilter) => ['agent', 'chat-run-stats', filter] as const,
  messages: (id: string) => ['agent', 'conversations', id, 'messages'] as const,
};

/** 我的会话分页：服务端只返回归属当前账号的会话。 */
export function fetchConversations(signal?: AbortSignal) {
  return request<ChatConversationPage>('/api/agent/conversations?page=1&pageSize=50', {
    signal,
  });
}

/** 现有接口只有分页列表；逐页定位，当前会话不受侧栏最近 50 条的限制。 */
export async function fetchConversation(id: string, signal?: AbortSignal) {
  for (let page = 1; ; page++) {
    const result = await request<ChatConversationPage>(
      `/api/agent/conversations?page=${page}&pageSize=100`,
      { signal },
    );
    const conversation = result.items.find((item) => item.id === id);
    if (conversation) return conversation;
    if (page * 100 >= result.total || result.items.length === 0) {
      throw new ApiError('会话不存在或已删除', 404, 'CONVERSATION_NOT_FOUND');
    }
  }
}

/** 某个会话的消息（按 seq）；不属于本人或不存在都返回 404。 */
export function fetchConversationMessages(id: string, signal?: AbortSignal) {
  return request<ChatConversationMessage[]>(
    `/api/agent/conversations/${encodeURIComponent(id)}/messages`,
    { signal },
  );
}

export function renameConversation(id: string, title: string, version: number) {
  return request<ChatConversation>(`/api/agent/conversations/${encodeURIComponent(id)}`, {
    method: 'PATCH',
    body: JSON.stringify({ title, version }),
  });
}

export function deleteConversation(id: string, version: number) {
  return request<null>(`/api/agent/conversations/${encodeURIComponent(id)}?version=${version}`, {
    method: 'DELETE',
  });
}

/** 执行记录与统计共用的筛选：连接 + 模型（都为空表示不筛选）。 */
export interface RunFilter {
  providerId?: string;
  modelId?: string;
}

export interface RunStatsFilter extends RunFilter {
  days: number;
  offsetMinutes: number;
}

/** 明细与聚合共用时间范围：`days` 缺省表示不限时间（当前只有用量页在用，始终带范围）。 */
export interface RunRangeFilter extends RunFilter {
  days?: number;
}

/** 我的执行记录：用量与失败原因；`state` 为空表示不筛选。 */
export function fetchChatRuns(
  options: { page?: number; pageSize?: number; state?: string } & RunRangeFilter = {},
  signal?: AbortSignal,
) {
  const query = new URLSearchParams({
    page: String(options.page ?? 1),
    pageSize: String(options.pageSize ?? 20),
  });
  if (options.state) query.set('state', options.state);
  if (options.providerId) query.set('providerId', options.providerId);
  if (options.modelId) query.set('modelId', options.modelId);
  if (options.days) query.set('days', String(options.days));
  return request<ChatRunRecordPage>(`/api/agent/chat/runs?${query}`, { signal });
}

/** 用量聚合：KPI、按天趋势与模型分布；`offsetMinutes` 决定按哪个本地日期分桶。 */
export function fetchChatRunStats(filter: RunStatsFilter, signal?: AbortSignal) {
  const query = new URLSearchParams({
    days: String(filter.days),
    offsetMinutes: String(filter.offsetMinutes),
  });
  if (filter.providerId) query.set('providerId', filter.providerId);
  if (filter.modelId) query.set('modelId', filter.modelId);
  return request<ChatRunStats>(`/api/agent/chat/runs/stats?${query}`, { signal });
}

/** 对话运行模式：标注本地演示或真实模型选择器。 */
export function fetchChatRuntime(signal?: AbortSignal) {
  return request<ChatRuntime>('/api/agent/chat/config', { signal });
}

export type ChatStartResult =
  { kind: 'stream'; response: Response } | { kind: 'snapshot'; view: ChatRunView };

/**
 * 发起/续接一次执行：新执行返回 SSE 流；同幂等键已结束时返回原快照 JSON。
 * 401、CSRF 与网络错误沿用 shared/http 的统一约定。
 */
export async function startChat(
  request_: ChatRequest,
  signal: AbortSignal,
): Promise<ChatStartResult> {
  const response = await requestStream('/api/agent/chat', {
    method: 'POST',
    headers: { Accept: 'text/event-stream, application/json' },
    body: JSON.stringify(request_),
    signal,
  });
  const contentType = response.headers.get('content-type') ?? '';
  if (contentType.includes('text/event-stream')) {
    return { kind: 'stream', response };
  }
  return { kind: 'snapshot', view: await readEnvelope<ChatRunView>(response) };
}

/** 断连/停止后先核对原执行，再决定重试。已过期或不存在返回 404。 */
export function fetchChatRun(idempotencyKey: string, signal?: AbortSignal) {
  return request<ChatRunView>(`/api/agent/chat/requests/${encodeURIComponent(idempotencyKey)}`, {
    signal,
  });
}

export function stopChatRun(idempotencyKey: string) {
  return request<ChatRunView>(
    `/api/agent/chat/requests/${encodeURIComponent(idempotencyKey)}/stop`,
    {
      method: 'POST',
    },
  );
}
