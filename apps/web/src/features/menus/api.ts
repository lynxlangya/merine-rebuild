/** 菜单管理与导航的 HTTP 请求与生成契约绑定。 */
import type {
  CreateMenu,
  MenuDeleteImpact,
  MenuNode,
  NavigationNode,
  RestoreMenusResult,
  RouteKeyOption,
  UpdateMenu,
} from '@merine/api-contract';
import { request } from '../../shared/http';

/** 当前账号可见的导航树：后端按会话权限过滤，前端只负责渲染。 */
export function fetchMyMenus(signal?: AbortSignal) {
  return request<NavigationNode[]>('/api/me/menus', { signal });
}

export function fetchMenuTree(signal?: AbortSignal) {
  return request<MenuNode[]>('/api/system/menus', { signal });
}

export function fetchRouteKeys(signal?: AbortSignal) {
  return request<RouteKeyOption[]>('/api/system/menus/route-keys', { signal });
}

export function fetchMenuDeleteImpact(id: string, signal?: AbortSignal) {
  return request<MenuDeleteImpact>(`/api/system/menus/${encodeURIComponent(id)}/delete-impact`, {
    signal,
  });
}

export function createMenu(input: CreateMenu) {
  return request<MenuNode>('/api/system/menus', {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

export function updateMenu(id: string, input: UpdateMenu) {
  return request<MenuNode>(`/api/system/menus/${encodeURIComponent(id)}`, {
    method: 'PUT',
    body: JSON.stringify(input),
  });
}

export function deleteMenu(id: string, version: number) {
  const params = new URLSearchParams({ version: String(version) });
  return request<MenuDeleteImpact>(`/api/system/menus/${encodeURIComponent(id)}?${params}`, {
    method: 'DELETE',
  });
}

export function restoreMenus() {
  return request<RestoreMenusResult>('/api/system/menus/restore', { method: 'POST' });
}
