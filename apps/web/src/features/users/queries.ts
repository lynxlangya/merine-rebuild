/**
 * 用户管理的查询 key 与查询 hooks。
 *
 * key 里带上筛选与分页：条件不同就是不同的缓存条目，翻页与改条件不会串用旧结果。
 * 失效用 userKeys.lists 前缀，只影响列表，不动单位与角色的选项缓存。
 */
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { fetchRoles, fetchUserPage } from './api';
import type { UserListQuery } from './model';

export const userKeys = {
  lists: ['system', 'users', 'list'] as const,
  list: (query: UserListQuery) => [...userKeys.lists, query] as const,
  roles: ['system', 'roles'] as const,
};

export function useUserListQuery(query: UserListQuery) {
  return useQuery({
    queryKey: userKeys.list(query),
    queryFn: ({ signal }) => fetchUserPage(query, signal),
    // 翻页与改条件时保留上一页结果，配合表格 loading 表达「后台刷新」，
    // 不打回骨架屏；失败时旧结果仍在，界面据此提示刷新失败而不是清空。
    placeholderData: keepPreviousData,
  });
}

/** 角色是低频变更的选项数据：一次会话内缓存 5 分钟，表单与筛选共用一份。 */
export function useRoleOptionsQuery() {
  return useQuery({
    queryKey: userKeys.roles,
    queryFn: ({ signal }) => fetchRoles(signal),
    staleTime: 5 * 60 * 1000,
  });
}
