import type { ChatConversation, ChatConversationMessage } from '@merine/api-contract';
import { useQuery } from '@tanstack/react-query';
import { chatKeys, fetchConversations } from './api';
import type { AgentMessage, AgentModelSelection } from './chatModel';

/**
 * 服务端会话与消息的读取。
 * 会话是随写入变化的列表，所以不做长时间缓存：窗口聚焦或执行结束后重新拉取。
 */
export function useConversationsQuery(enabled = true) {
  return useQuery({
    queryKey: chatKeys.conversations,
    queryFn: ({ signal }) => fetchConversations(signal),
    enabled,
    staleTime: 0,
  });
}

/**
 * 服务端消息 → 界面消息。
 * 落库只保存文本，所以历史里只有 TEXT 部件；失败的轮次没有正文，用一条提示说明，
 * 具体错误码与耗时在「我的执行记录」里查。
 */
export function messagesFromServer(messages: ChatConversationMessage[]): AgentMessage[] {
  return messages.map((message) => {
    const role = message.role === 'USER' ? ('USER' as const) : ('ASSISTANT' as const);
    const failed = role === 'ASSISTANT' && message.status === 'FAILED';
    const aborted = role === 'ASSISTANT' && message.status === 'ABORTED';
    return {
      id: message.id,
      role,
      // 落库的消息都是终态；RUNNING 不会出现在历史里，按成功处理避免界面卡在「正在生成」。
      status: message.status === 'RUNNING' ? ('SUCCEEDED' as const) : message.status,
      createdAt: message.createdAt,
      parts: [
        {
          kind: 'known' as const,
          partId: `${message.id}-t1`,
          part: { type: 'TEXT' as const, text: message.text },
          status: 'DONE' as const,
        },
      ],
      error: failed
        ? {
            code: 'HISTORY_FAILED',
            message: '本轮未完成，详情见「我的执行记录」',
            retryable: false,
          }
        : aborted
          ? { code: 'HISTORY_ABORTED', message: '本轮已停止', retryable: false }
          : undefined,
    };
  });
}

/**
 * 会话最近使用的模型：连接与模型都还在、且这个模型仍可用时才回填，
 * 否则交给输入区用默认选择——不把已经不存在的目标带进请求。
 */
export function modelFromConversation(
  conversation: ChatConversation,
  available: { providerId: string; modelId: string }[],
): AgentModelSelection | undefined {
  if (!conversation.lastProviderId || !conversation.lastModelId) return undefined;
  const exists = available.some(
    (option) =>
      option.providerId === conversation.lastProviderId &&
      option.modelId === conversation.lastModelId,
  );
  if (!exists) return undefined;
  return {
    providerId: conversation.lastProviderId,
    modelId: conversation.lastModelId,
    reasoningEffort: conversation.lastReasoningEffort ?? undefined,
  };
}

/** 侧栏条目：服务端会话与本地草稿统一成一个形状。 */
export interface SessionListItem {
  id: string;
  title: string;
  updatedAt?: string;
  draft: boolean;
  version?: number;
  /** 服务端会话原文：打开会话要拿它的最近使用目标与版本。 */
  source: ChatConversation;
}

export function listItems(
  drafts: { id: string; title: string; source: ChatConversation }[],
  conversations: ChatConversation[],
): SessionListItem[] {
  return [
    ...drafts.map((draft) => ({ ...draft, draft: true })),
    ...conversations.map((conversation) => ({
      id: conversation.id,
      title: conversation.title,
      updatedAt: conversation.updatedAt,
      version: conversation.version,
      draft: false,
      source: conversation,
    })),
  ];
}
