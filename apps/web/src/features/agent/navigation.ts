/**
 * 对话内链接的分类与站内导航能力。
 *
 * feature 不能导入 `app/routeRegistry`：站内路径解析由 app 装配时注入
 * {@link AgentNavigationResolver}，这里只做纯分类，便于测试与复用。
 */

export type AgentLinkTarget =
  { kind: 'internal'; to: string } | { kind: 'external'; to: string } | { kind: 'plain' };

/**
 * 判断 Markdown/来源里的 href 是否可安全跳转：
 * - 站内路径必须以单个 `/` 开头，拒绝 `//evil.com` 与反斜杠变体；
 * - 外链只接受明确的 http(s)；
 * - 其它（javascript:、data:、mailto:、空值等）按纯文本处理。
 */
export function classifyHref(href: string | undefined | null): AgentLinkTarget {
  if (!href) return { kind: 'plain' };
  const trimmed = href.trim();
  if (trimmed.length === 0) return { kind: 'plain' };
  if (/^https?:\/\//i.test(trimmed)) return { kind: 'external', to: trimmed };
  if (trimmed.startsWith('/') && !trimmed.startsWith('//') && !trimmed.includes('\\')) {
    return { kind: 'internal', to: trimmed };
  }
  return { kind: 'plain' };
}

export type AgentSource = {
  kind?: 'TASK' | 'FLOW' | 'INTEL' | 'MENU' | 'EXTERNAL';
  refId?: string;
  routeKey?: string;
  url?: string;
  title?: string;
  snippet?: string;
};

export type AgentSourceTarget = { kind: 'internal'; to: string } | { kind: 'external'; to: string };

/** app 侧注入：把来源解析成可跳转目标；未注册/非法返回 undefined（渲染为不可点文本）。 */
export type AgentNavigationResolver = (source: AgentSource) => AgentSourceTarget | undefined;
