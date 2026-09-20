/** 用户管理的 HTTP 请求与生成契约绑定。 */
import type {
  ChangeStatus,
  CreateUser,
  PageResultUserSummary,
  RoleSummary,
  UpdateUser,
  UserSummary,
} from '@merine/api-contract';
import { request } from '../../shared/http';
import type { UserListQuery } from './model';

export function fetchUserPage(query: UserListQuery, signal?: AbortSignal) {
  const params = new URLSearchParams();
  const keyword = query.keyword.trim();
  if (keyword) params.set('keyword', keyword);
  if (query.unitCode) params.set('unitCode', query.unitCode);
  if (query.roleCode) params.set('roleCode', query.roleCode);
  if (query.status) params.set('status', query.status);
  params.set('page', String(query.page));
  params.set('pageSize', String(query.pageSize));
  return request<PageResultUserSummary>(`/api/system/users?${params}`, { signal });
}

export function fetchRoles(signal?: AbortSignal) {
  return request<RoleSummary[]>('/api/system/roles', { signal });
}

export function createUser(input: CreateUser) {
  return request<UserSummary>('/api/system/users', {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

export function updateUser(id: string, input: UpdateUser) {
  return request<UserSummary>(`/api/system/users/${id}`, {
    method: 'PUT',
    body: JSON.stringify(input),
  });
}

/** 启用与停用走各自的动作路径；接口没有「改 status 字段」这种写法。 */
export function changeUserStatus(userIds: string[], enable: boolean) {
  const input: ChangeStatus = { userIds };
  return request<UserSummary[]>(`/api/system/users/${enable ? 'enable' : 'disable'}`, {
    method: 'POST',
    body: JSON.stringify(input),
  });
}
