import { Result } from 'antd';
import { useAuth } from '../auth/public';
import { PERMISSIONS, hasPermission } from '../../shared/permissions';
import { AgentComposer } from './AgentComposer';
import { useAgentChatApi } from './AgentChatContext';
import { AgentMessages } from './components/AgentMessages';
import { welcomeMessage } from './model';
import styles from './AgentLayout.module.css';

export function AgentHomePage() {
  const { state } = useAuth();
  const { session, loadingConversation, conversationError } = useAgentChatApi();
  const displayName = state.status === 'authenticated' ? state.user.displayName : '';
  const allowed =
    state.status === 'authenticated' &&
    hasPermission(state.user.permissionCodes, PERMISSIONS.chatUse);
  // 正在加载历史时也要显示消息区：那里有「正在加载会话历史…」，否则会被误认为空会话。
  const hasMessages = Boolean(
    session && (session.messages.length > 0 || loadingConversation || conversationError),
  );
  return (
    <div className={styles.home}>
      <header className={styles.header}>
        <span>海防助手</span>
        {hasMessages && <span className={styles.conversationTitle}>{session?.title}</span>}
      </header>
      {!allowed ? (
        <div className={styles.welcome}>
          <Result
            status="403"
            title="没有使用海防助手的权限"
            subTitle="请联系管理员为你的角色授予「海防助手 · 使用对话」。"
          />
        </div>
      ) : (
        <>
          {hasMessages && session ? (
            <>
              <AgentMessages session={session} />
              <AgentComposer key={session.id} />
            </>
          ) : (
            <div className={styles.start}>
              <div className={styles.greeting}>
                <span className={styles.eyebrow}>开始新的对话</span>
                <h1>{welcomeMessage(displayName, new Date())}</h1>
                <p>从一个问题开始，逐步整理思路与线索。</p>
              </div>
              <AgentComposer key={session?.id ?? 'welcome'} />
            </div>
          )}
        </>
      )}
    </div>
  );
}
