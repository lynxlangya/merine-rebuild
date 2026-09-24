/**
 * 菜单与导航的查询 key 与 hooks。
 *
 * menuKeys.all 是菜单相关缓存的前缀：菜单写成功后失效它，管理树、页面清单与
 * 当前账号导航会一起刷新；导航单独放在 me 前缀下，避免与系统管理缓存耦合。
 */
import { useQuery } from '@tanstack/react-query';
import { fetchIconOptions, fetchMenuTree, fetchMyMenus, fetchRouteKeys } from './api';

export const menuKeys = {
  all: ['system', 'menus'] as const,
  tree: ['system', 'menus', 'tree'] as const,
  routeKeys: ['system', 'menus', 'route-keys'] as const,
  iconOptions: ['system', 'menus', 'icons'] as const,
  myMenus: ['me', 'menus'] as const,
};

/** 导航：登录后渲染左侧菜单；菜单调整后由写路径显式失效。 */
export function useMyMenusQuery() {
  return useQuery({
    queryKey: menuKeys.myMenus,
    queryFn: ({ signal }) => fetchMyMenus(signal),
    staleTime: 60 * 1000,
  });
}

export function useMenuTreeQuery(enabled: boolean) {
  return useQuery({
    queryKey: menuKeys.tree,
    queryFn: ({ signal }) => fetchMenuTree(signal),
    enabled,
    staleTime: 0,
  });
}

export function useRouteKeysQuery(enabled: boolean) {
  return useQuery({
    queryKey: menuKeys.routeKeys,
    queryFn: ({ signal }) => fetchRouteKeys(signal),
    enabled,
    staleTime: 5 * 60 * 1000,
  });
}

export function useIconOptionsQuery(enabled: boolean) {
  return useQuery({
    queryKey: menuKeys.iconOptions,
    queryFn: ({ signal }) => fetchIconOptions(signal),
    enabled,
    staleTime: 5 * 60 * 1000,
  });
}
