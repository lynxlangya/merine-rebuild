/** 标签页内只记住当前账号的会话标识，正文、草稿和权限不进入浏览器存储。 */
type ConversationStorage = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>;

function key(userId: string) {
  return `merine:agent:current-conversation:${userId}`;
}

export function readCurrentConversation(storage: () => ConversationStorage, userId: string) {
  try {
    return storage().getItem(key(userId)) || undefined;
  } catch {
    return undefined;
  }
}

export function rememberCurrentConversation(
  storage: () => ConversationStorage,
  userId: string,
  conversationId?: string,
) {
  try {
    if (conversationId) storage().setItem(key(userId), conversationId);
    else storage().removeItem(key(userId));
  } catch {
    // 存储不可用时仍允许正常对话，只失去刷新恢复能力。
  }
}
