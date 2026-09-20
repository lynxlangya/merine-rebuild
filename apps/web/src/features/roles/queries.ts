/**
 * 角色管理的查询 key 与查询 hooks。
 *
 * roleKeys.all 是角色相关缓存的前缀：角色写成功后失效它，列表、详情、成员与选项
 * （用户表单与筛选也读同一份选项缓存）都会一起刷新。
 */
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import {
  fetchRoleDetail,
  fetchRoleMembers,
  fetchRoleOptions,
  fetchRolePage,
  fetchRolePermissionTree,
} from './api';
import type { RoleListQuery } from './model';

export const roleKeys = {
  all: ['system', 'roles'] as const,
  lists: ['system', 'roles', 'list'] as const,
  list: (query: RoleListQuery) => [...roleKeys.lists, query] as const,
  options: ['system', 'roles', 'options'] as const,
  details: ['system', 'roles', 'detail'] as const,
  detail: (code: string) => [...roleKeys.details, code] as const,
  members: (code: string, page: number, pageSize: number) =>
    ['system', 'roles', 'members', code, page, pageSize] as const,
  permissionTree: ['system', 'roles', 'permission-tree'] as const,
};

const OPTION_STALE_TIME = 5 * 60 * 1000;

/** 角色选项是低频变更的选择数据：一次会话内缓存 5 分钟，表单与筛选共用一份。 */
export function useRoleOptionsQuery() {
  return useQuery({
    queryKey: roleKeys.options,
    queryFn: ({ signal }) => fetchRoleOptions(signal),
    staleTime: OPTION_STALE_TIME,
  });
}

export function useRoleListQuery(query: RoleListQuery, enabled: boolean) {
  return useQuery({
    queryKey: roleKeys.list(query),
    queryFn: ({ signal }) => fetchRolePage(query, signal),
    enabled,
    // 翻页与改条件时保留上一页结果，配合表格 loading 表达「后台刷新」，
    // 不打回骨架屏；失败时旧结果仍在，界面据此提示刷新失败而不是清空。
    placeholderData: keepPreviousData,
  });
}

/** 编辑抽屉打开时才读详情：列表行不带权限集合。 */
export function useRoleDetailQuery(code: string | null) {
  return useQuery({
    queryKey: roleKeys.detail(code ?? ''),
    queryFn: ({ signal }) => fetchRoleDetail(code as string, signal),
    enabled: code !== null,
    staleTime: 0,
  });
}

export function useRoleMembersQuery(code: string | null, page: number, pageSize: number) {
  return useQuery({
    queryKey: roleKeys.members(code ?? '', page, pageSize),
    queryFn: ({ signal }) => fetchRoleMembers(code as string, page, pageSize, signal),
    enabled: code !== null,
    placeholderData: keepPreviousData,
  });
}

/** 权限勾选树同样是低频参考数据：角色编辑抽屉把它渲染成树形勾选。 */
export function useRolePermissionTreeQuery(enabled: boolean) {
  return useQuery({
    queryKey: roleKeys.permissionTree,
    queryFn: ({ signal }) => fetchRolePermissionTree(signal),
    enabled,
    staleTime: OPTION_STALE_TIME,
  });
}
