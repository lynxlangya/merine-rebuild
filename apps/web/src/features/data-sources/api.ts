import type {
  CheckDataSourceDraft,
  DataSourceCheckResult,
  DataSourcePage,
  DataSourceStatusChange,
  DataSourceView,
  SaveDataSource,
} from '@merine/api-contract';
import { request } from '../../shared/http';

export const dataSourceKeys = ['agent', 'datasources'] as const;

/** 列表：只返回配置与最近检查结论，不含密码。 */
export function fetchDataSources(search: string, page: number, signal?: AbortSignal) {
  return request<DataSourcePage>(
    `/api/agent/datasources?${new URLSearchParams({
      search,
      page: String(page),
      pageSize: '12',
    })}`,
    { signal },
  );
}

export function createDataSource(input: SaveDataSource) {
  return request<DataSourceView>('/api/agent/datasources', {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

/** 编辑：目标与账号未变更时密码留空表示保留；连接参数变化会让旧检查结果失效。 */
export function updateDataSource(id: string, input: SaveDataSource) {
  return request<DataSourceView>(`/api/agent/datasources/${encodeURIComponent(id)}`, {
    method: 'PUT',
    body: JSON.stringify(input),
  });
}

/** 未保存配置的测试连接：不落库；编辑态可不填密码，由服务端复用已保存的密码。 */
export function checkDataSourceDraft(input: CheckDataSourceDraft) {
  return request<DataSourceCheckResult>('/api/agent/datasources/check', {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

/** 已保存配置的检查：服务端记录结论，目标仍是保存的连接参数。 */
export function checkDataSourceSaved(id: string) {
  return request<DataSourceCheckResult>(`/api/agent/datasources/${encodeURIComponent(id)}/check`, {
    method: 'POST',
  });
}

/** 启停：启用会重新检查；通过且已勾选视图才会真正生效。 */
export function changeDataSourceStatus(id: string, input: DataSourceStatusChange) {
  return request<DataSourceView>(`/api/agent/datasources/${encodeURIComponent(id)}/status`, {
    method: 'POST',
    body: JSON.stringify(input),
  });
}
