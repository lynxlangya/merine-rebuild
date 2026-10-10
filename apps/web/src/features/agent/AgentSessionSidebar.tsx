import { EditOutlined, MoreOutlined } from '@ant-design/icons';
import { App, Button, Dropdown, Input, Modal } from 'antd';
import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useAgentChatApi } from './AgentChatContext';
import { chatKeys, deleteConversation, renameConversation } from './api';
import { listItems, useConversationsQuery } from './history';
import styles from './AgentSessionSidebar.module.css';

/**
 * 左侧对话栏：本地草稿 + 服务端会话。
 *
 * 服务端会话只对本人可见，列表来自 `GET /api/agent/conversations`；
 * 还没有落库的本地会话（没拿到 conversationId）作为草稿显示在最上面。
 * 重命名与删除都带版本号，避免两个标签页互相覆盖。
 */
export function AgentSessionSidebar({ id }: { id: string }) {
  const { state, newSession, selectSession, openConversation } = useAgentChatApi();
  const { message, modal } = App.useApp();
  const client = useQueryClient();
  const conversations = useConversationsQuery();
  const [renaming, setRenaming] = useState<{ id: string; title: string; version: number }>();
  const [renameText, setRenameText] = useState('');
  const [pending, setPending] = useState(false);

  const drafts = state.sessions
    .filter((session) => !session.conversationId)
    .map((session) => ({
      id: session.id,
      title: session.title,
      // 草稿没有服务端会话：占位对象只用于统一条目形状，点击走 selectSession 分支。
      source: {
        id: session.id,
        title: session.title,
        lastProviderId: '',
        lastProviderName: '',
        lastModelId: '',
        messageCount: 0,
        version: 0,
        createdAt: '',
        updatedAt: '',
      },
    }));
  const items = listItems(drafts, conversations.data?.items ?? []);

  const refresh = () => client.invalidateQueries({ queryKey: chatKeys.conversations });

  const submitRename = () => {
    const target = renaming;
    const title = renameText.trim();
    if (!target || !title) return;
    setPending(true);
    renameConversation(target.id, title, target.version)
      .then(() => {
        setRenaming(undefined);
        void refresh();
      })
      .catch((error: Error) => message.error(error.message))
      .finally(() => setPending(false));
  };

  const confirmDelete = (item: { id: string; title: string; version?: number }) => {
    modal.confirm({
      title: '删除这个会话？',
      content: `「${item.title}」的消息与执行记录会一起删除，无法恢复。`,
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: () =>
        deleteConversation(item.id, item.version ?? 0)
          .then(() => {
            setStateIfDeleted(item.id);
            void refresh();
          })
          .catch((error: Error) => message.error(error.message)),
    });
  };

  /** 删掉的正好是当前会话：回到草稿会话，避免继续往里写。 */
  const setStateIfDeleted = (conversationId: string) => {
    const active = state.sessions.find((item) => item.id === state.activeId);
    if (active?.conversationId === conversationId) newSession();
  };

  return (
    <aside className={styles.sessions} id={id} aria-label="对话栏">
      <div className={styles.head}>
        <button type="button" className={styles.newChat} onClick={newSession}>
          <EditOutlined aria-hidden="true" />
          新聊天
        </button>
      </div>
      <h2 className={styles.listTitle}>最近对话</h2>
      {items.length === 0 ? (
        <p className={styles.empty}>
          {conversations.isLoading
            ? '正在加载会话…'
            : conversations.isError
              ? '会话列表加载失败，请刷新重试。'
              : '还没有对话，点击“新聊天”开始。'}
        </p>
      ) : (
        <ul className={styles.list}>
          {items.map((item) => {
            const activeSession = state.sessions.find((entry) => entry.id === state.activeId);
            const active = item.id === state.activeId || item.id === activeSession?.conversationId;
            return (
              <li key={item.id} className={styles.row}>
                <button
                  type="button"
                  className={`${styles.session} ${active ? styles.active : ''}`}
                  aria-current={active ? 'true' : undefined}
                  title={item.title}
                  onClick={() =>
                    item.draft ? selectSession(item.id) : openConversation(item.source)
                  }
                >
                  {item.title}
                </button>
                {!item.draft && (
                  <Dropdown
                    trigger={['click']}
                    menu={{
                      items: [
                        {
                          key: 'rename',
                          label: '重命名',
                          onClick: () => {
                            setRenaming({
                              id: item.id,
                              title: item.title,
                              version: item.version ?? 0,
                            });
                            setRenameText(item.title);
                          },
                        },
                        {
                          key: 'delete',
                          label: '删除',
                          danger: true,
                          onClick: () => confirmDelete(item),
                        },
                      ],
                    }}
                  >
                    <Button
                      type="text"
                      size="small"
                      className={styles.rowAction}
                      icon={<MoreOutlined />}
                      aria-label={`会话操作：${item.title}`}
                    />
                  </Dropdown>
                )}
              </li>
            );
          })}
        </ul>
      )}
      <Modal
        open={Boolean(renaming)}
        title="重命名会话"
        okText="保存"
        cancelText="取消"
        confirmLoading={pending}
        onOk={submitRename}
        onCancel={() => setRenaming(undefined)}
      >
        <Input
          value={renameText}
          maxLength={80}
          aria-label="会话标题"
          onChange={(event) => setRenameText(event.target.value)}
          onPressEnter={submitRename}
        />
      </Modal>
    </aside>
  );
}
