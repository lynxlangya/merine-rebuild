import { useEffect, useRef, useState } from 'react';
import { Alert, Button } from 'antd';
import { DownOutlined } from '@ant-design/icons';
import { useAgentChatApi } from '../AgentChatContext';
import type { AgentSession } from '../chatModel';
import { AgentMessageItem } from './AgentMessageItem';
import styles from './AgentParts.module.css';

/**
 * 消息列表：贴近底部时自动跟随；用户向上滚动后停在原位，显示「回到底部」。
 * 正文不逐字 aria-live 播报，状态由独立的 role="status" 区域承担。
 */
export function AgentMessages({ session }: { session: AgentSession }) {
  const { busy, loadingConversation, conversationError, retryConversation } = useAgentChatApi();
  const scrollRef = useRef<HTMLDivElement>(null);
  const [following, setFollowing] = useState(true);
  const lastId = session.messages.at(-1)?.id;

  useEffect(() => {
    const node = scrollRef.current;
    if (node && following) node.scrollTop = node.scrollHeight;
  });

  const onScroll = () => {
    const node = scrollRef.current;
    if (!node) return;
    setFollowing(node.scrollHeight - node.scrollTop - node.clientHeight < 48);
  };

  return (
    <div className={styles.messagesWrap}>
      <div className={styles.messages} ref={scrollRef} onScroll={onScroll} aria-label="对话消息">
        {conversationError && (
          <Alert
            type="error"
            showIcon
            title="会话历史加载失败"
            description={conversationError}
            action={
              <Button size="small" onClick={retryConversation}>
                重试
              </Button>
            }
          />
        )}
        {loadingConversation && session.messages.length === 0 && (
          <p className={styles.historyLoading}>正在加载会话历史…</p>
        )}
        {session.messages.map((message) => (
          <AgentMessageItem key={message.id} message={message} last={message.id === lastId} />
        ))}
      </div>
      <div className={styles.statusLine} role="status" aria-live="polite">
        {statusText(session, busy)}
      </div>
      {!following && (
        <Button
          className={styles.jump}
          size="small"
          shape="circle"
          icon={<DownOutlined />}
          aria-label="回到底部"
          onClick={() => {
            setFollowing(true);
            const node = scrollRef.current;
            if (node) node.scrollTop = node.scrollHeight;
          }}
        />
      )}
    </div>
  );
}

function statusText(session: AgentSession, busy: boolean): string {
  const status = session.run?.status;
  if (status === 'CANCEL_REQUESTED') return '正在停止…';
  if (busy || status === 'STREAMING') return '正在生成…';
  if (status === 'AWAITING_INPUT') return '等待你的回答';
  if (status === 'FAILED') return '生成失败，可重试';
  if (status === 'INTERRUPTED') return '连接中断，结果待确认';
  if (status === 'ABORTED') return '已停止';
  return '';
}
