import type { AgentNavigationResolver } from '../features/agent/navigation';
import { routeByKey } from './routeRegistry';

/**
 * 助手来源的站内解析：由 app 装配注入，feature 不导入 `app/routeRegistry`。
 * 未注册的路由键、缺少业务标识或非法外链返回 undefined，前端渲染为不可点文本。
 */
export const resolveAgentSource: AgentNavigationResolver = (source) => {
  if (source.kind === 'MENU') {
    if (!source.routeKey) return undefined;
    const route = routeByKey(source.routeKey);
    return route ? { kind: 'internal', to: route.path } : undefined;
  }
  if (source.kind === 'TASK' || source.kind === 'FLOW') {
    if (!source.refId) return undefined;
    const route = routeByKey(
      source.kind === 'TASK' ? 'collaboration.tasks' : 'collaboration.flows',
    );
    return route
      ? { kind: 'internal', to: `${route.path}/${encodeURIComponent(source.refId)}` }
      : undefined;
  }
  if (source.kind === 'EXTERNAL') {
    return source.url && /^https?:\/\//i.test(source.url)
      ? { kind: 'external', to: source.url }
      : undefined;
  }
  return undefined;
};
