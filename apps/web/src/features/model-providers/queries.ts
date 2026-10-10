import type { ModelProvider } from '@merine/api-contract';
import { useQuery } from '@tanstack/react-query';
import { fetchEffortCatalog, fetchModelOptions } from './api';

/** 使用侧模型选项：低频参考数据，一次会话内缓存 5 分钟。 */
export const modelOptionKeys = { all: ['agent', 'model-options'] as const };

export function useModelOptionsQuery() {
  return useQuery({
    queryKey: modelOptionKeys.all,
    queryFn: ({ signal }) => fetchModelOptions(signal),
    staleTime: 5 * 60 * 1000,
  });
}

/**
 * 官方推理强度目录：按供应商 + 当前模型标识批量查询，供配置页限制可勾选的档位。
 * 标识变化频繁，调用方传入的是去抖后的列表；目录是纯静态数据，缓存 5 分钟。
 */
export function useEffortCatalogQuery(vendor: ModelProvider['vendor'], modelIds: string[]) {
  return useQuery({
    queryKey: ['agent', 'provider-efforts', vendor, ...modelIds],
    queryFn: ({ signal }) => fetchEffortCatalog(vendor, modelIds, signal),
    enabled: modelIds.length > 0,
    staleTime: 5 * 60 * 1000,
  });
}
