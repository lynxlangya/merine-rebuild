import { useQuery } from '@tanstack/react-query';
import { fetchUnitOptions, fetchUnitTree } from './api';

/**
 * 单位缓存只有一个根 key。
 * 用户管理的选项与单位管理页面共用这份缓存；单位写成功后失效整个前缀，
 * 两个页面都不会继续展示旧组织关系。
 */
export const unitKeys = {
  all: ['system', 'units'] as const,
  list: ['system', 'units', 'list'] as const,
  tree: ['system', 'units', 'tree'] as const,
};

export function useUnitOptionsQuery() {
  return useQuery({
    queryKey: unitKeys.list,
    queryFn: ({ signal }) => fetchUnitOptions(signal),
    // 单位是低频变更的选择数据，一次会话内缓存 5 分钟。
    staleTime: 5 * 60 * 1000,
  });
}

export function useUnitTreeQuery() {
  return useQuery({
    queryKey: unitKeys.tree,
    queryFn: ({ signal }) => fetchUnitTree(signal),
    staleTime: 30 * 1000,
  });
}
