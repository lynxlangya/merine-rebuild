/** 角色管理的 HTTP 请求与生成契约绑定。 */
import type {
  ChangeRoleStatus,
  CreateRole,
  MenuNode,
  PageResultRoleListItem,
  PageResultRoleMember,
  RoleDetail,
  RoleListItem,
  RoleSummary,
  UpdateRole,
} from '@merine/api-contract';
import { request } from '../../shared/http';
import type { RoleListQuery } from './model';

/** 角色选项：用户管理的筛选与表单选择。 */
export function fetchRoleOptions(signal?: AbortSignal) {
  return request<RoleSummary[]>('/api/system/roles/options', { signal });
}

export function fetchRolePage(query: RoleListQuery, signal?: AbortSignal) {
  const params = new URLSearchParams();
  const keyword = query.keyword.trim();
  if (keyword) params.set('keyword', keyword);
  if (query.status) params.set('status', query.status);
  params.set('page', String(query.page));
  params.set('pageSize', String(query.pageSize));
  return request<PageResultRoleListItem>(`/api/system/roles?${params}`, { signal });
}

export function fetchRoleDetail(code: string, signal?: AbortSignal) {
  return request<RoleDetail>(`/api/system/roles/${encodeURIComponent(code)}`, { signal });
}

export function fetchRoleMembers(
  code: string,
  page: number,
  pageSize: number,
  signal?: AbortSignal,
) {
  const params = new URLSearchParams({ page: String(page), pageSize: String(pageSize) });
  return request<PageResultRoleMember>(
    `/api/system/roles/${encodeURIComponent(code)}/members?${params}`,
    { signal },
  );
}

/** 权限勾选树：菜单资源树（目录/页面/页签/按钮，页面与按钮节点带权限码）。 */
export function fetchRolePermissionTree(signal?: AbortSignal) {
  return request<MenuNode[]>('/api/system/roles/permission-tree', { signal });
}

export function createRole(input: CreateRole) {
  return request<RoleDetail>('/api/system/roles', {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

export function updateRole(code: string, input: UpdateRole) {
  return request<RoleDetail>(`/api/system/roles/${encodeURIComponent(code)}`, {
    method: 'PUT',
    body: JSON.stringify(input),
  });
}

/** 启用与停用走各自的动作路径；接口没有「改 status 字段」这种写法。 */
export function changeRoleStatus(roleCodes: string[], enable: boolean) {
  const input: ChangeRoleStatus = { roleCodes };
  return request<RoleListItem[]>(`/api/system/roles/${enable ? 'enable' : 'disable'}`, {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

/** 删除需要提交打开页面时的角色版本，避免基于过期信息误删刚改过的角色。 */
export function deleteRole(code: string, version: number) {
  const params = new URLSearchParams({ version: String(version) });
  return request<null>(`/api/system/roles/${encodeURIComponent(code)}?${params}`, {
    method: 'DELETE',
  });
}
