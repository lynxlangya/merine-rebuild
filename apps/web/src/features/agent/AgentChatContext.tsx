import { createContext, useContext, type ReactNode } from 'react';
import type { AgentNavigationResolver } from './navigation';
import { useAgentChat, type AgentChatApi } from './useAgentChat';

const AgentChatContext = createContext<AgentChatApi | null>(null);
const AgentNavigationContext = createContext<AgentNavigationResolver | null>(null);

/**
 * 助手页面的两个注入点：
 * - 对话状态由 AgentLayout 统一持有（侧栏与主页共享同一份会话）；
 * - 站内导航解析由 app 装配时传入，feature 不反向导入 `app/routeRegistry`。
 */
export function AgentChatProvider({
  resolveSource,
  children,
}: {
  resolveSource: AgentNavigationResolver;
  children: ReactNode;
}) {
  const chat = useAgentChat();
  return (
    <AgentChatContext value={chat}>
      <AgentNavigationContext value={resolveSource}>{children}</AgentNavigationContext>
    </AgentChatContext>
  );
}

export function useAgentChatApi(): AgentChatApi {
  const chat = useContext(AgentChatContext);
  if (!chat) throw new Error('useAgentChat 必须在 AgentChatProvider 内使用');
  return chat;
}

export function useAgentSourceResolver(): AgentNavigationResolver {
  return useContext(AgentNavigationContext) ?? (() => undefined);
}
