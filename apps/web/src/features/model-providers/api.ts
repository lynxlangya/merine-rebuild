import type {
  DiscoverProviderModels,
  ProviderEffortCatalog,
  ModelOption,
  ModelProvider,
  ModelProviderPage,
  ProviderModelCatalog,
  SaveModelProvider,
} from '@merine/api-contract';
import { request } from '../../shared/http';
export const providerKeys = ['agent', 'providers'] as const;
export function fetchProviders(search: string, page: number, signal?: AbortSignal) {
  return request<ModelProviderPage>(
    `/api/agent/providers?${new URLSearchParams({ search, page: String(page), pageSize: '12' })}`,
    { signal },
  );
}

/** 使用侧模型选项（登录即可）：已启用连接下的启用模型，不含地址、备注与密钥。 */
export function fetchModelOptions(signal?: AbortSignal) {
  return request<ModelOption[]>('/api/agent/models', { signal });
}
export function saveProvider(target: ModelProvider | null, input: SaveModelProvider) {
  return request<ModelProvider>(
    target ? `/api/agent/providers/${target.id}` : '/api/agent/providers',
    {
      method: target ? 'PUT' : 'POST',
      body: JSON.stringify({ ...input, version: target?.version }),
    },
  );
}
/**
 * 让服务端带着密钥去读供应商的模型清单（OpenAI 兼容的 GET {baseUrl}/models）。
 * 表单里刚输入的密钥优先；留空且连接已保存时，用服务端保存的那份。
 */
export function discoverProviderModels(input: DiscoverProviderModels) {
  return request<ProviderModelCatalog>('/api/agent/providers/models/discover', {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

/** 批量查官方推理强度目录：配置页按模型标识决定展示哪些档位。 */
export function fetchEffortCatalog(
  vendor: ModelProvider['vendor'],
  modelIds: string[],
  signal?: AbortSignal,
) {
  return request<ProviderEffortCatalog>('/api/agent/providers/reasoning-efforts', {
    method: 'POST',
    body: JSON.stringify({ vendor, modelIds }),
    signal,
  });
}

export function deleteProvider(target: ModelProvider) {
  return request<null>(`/api/agent/providers/${target.id}?version=${target.version}`, {
    method: 'DELETE',
  });
}
