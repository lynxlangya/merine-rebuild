import type {
  DataSourceCheckResult,
  DataSourcePage,
  QueryScopeDetail,
  ReplaceQueryScopeViews,
  SaveQueryScopeMetadata,
} from '@merine/api-contract';
import { request } from '../../shared/http';

export const queryScopeKeys = ['agent', 'query-scopes'] as const;

/** 列表只读配置库：返回数据源摘要与已选视图数，不连接目标库。 */
export function fetchQueryScopes(search: string, page: number, signal?: AbortSignal) {
  return request<DataSourcePage>(
    `/api/agent/query-scopes?${new URLSearchParams({
      search,
      page: String(page),
      pageSize: '12',
    })}`,
    { signal },
  );
}

/** 详情：已保存的白名单与说明覆盖值；视图/字段是否存在需要显式探测。 */
export function fetchQueryScopeDetail(id: string, signal?: AbortSignal) {
  return request<QueryScopeDetail>(`/api/agent/query-scopes/${encodeURIComponent(id)}`, { signal });
}

/** 刷新可读视图；带 view 时同时读取该视图字段名/类型/可空性/列注释。 */
export function inspectQueryScope(id: string, view?: string) {
  const query = view ? `?${new URLSearchParams({ view })}` : '';
  return request<DataSourceCheckResult>(
    `/api/agent/query-scopes/${encodeURIComponent(id)}/inspect${query}`,
    { method: 'POST' },
  );
}

/** 整体替换允许查询的视图；新增候选由服务端复检可读性，保存后数据源停用待复检。 */
export function replaceQueryScopeViews(id: string, input: ReplaceQueryScopeViews) {
  return request<QueryScopeDetail['source']>(
    `/api/agent/query-scopes/${encodeURIComponent(id)}/views`,
    { method: 'PUT', body: JSON.stringify(input) },
  );
}

/** 指定视图的说明覆盖段整体替换；新增字段说明前服务端会实时核对目标库字段。 */
export function saveQueryScopeMetadata(id: string, input: SaveQueryScopeMetadata) {
  return request<QueryScopeDetail>(`/api/agent/query-scopes/${encodeURIComponent(id)}/metadata`, {
    method: 'PUT',
    body: JSON.stringify(input),
  });
}
