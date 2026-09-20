import type { MenuNode, NavigationNode } from '@merine/api-contract';
import type { ReactNode } from 'react';

/** 菜单节点类型：目录 / 页面 / 页签 / 按钮。 */
export type MenuType = 'DIRECTORY' | 'PAGE' | 'TAB' | 'BUTTON';

export interface ResolvedRoute {
  path: string;
  icon: ReactNode;
}

export interface NavItem {
  key: string;
  label: string;
  icon?: ReactNode;
  path?: string;
  children?: NavItem[];
}

export interface HomeEntry {
  key: string;
  title: string;
  description: string;
  path: string;
  icon?: ReactNode;
}

/**
 * 导航树只保留目录与页面：页签与按钮属于页面内部，不进左侧菜单。
 * 页面必须能在前端注册表里找到组件与图标；找不到就跳过并告警——
 * 后端只校验 route key 在注册清单里，前端注册表滞后时不能把用户带进死链。
 */
export function toNavItems(
  nodes: readonly NavigationNode[],
  resolve: (routeKey: string) => ResolvedRoute | undefined,
): NavItem[] {
  const items: NavItem[] = [];
  for (const node of nodes) {
    if (node.type === 'DIRECTORY') {
      const children = toNavItems(node.children, resolve);
      if (children.length > 0) items.push({ key: node.id, label: node.name, children });
      continue;
    }
    if (node.type !== 'PAGE' || !node.routeKey) continue;
    const route = resolve(node.routeKey);
    if (!route) {
      console.warn(`菜单 ${node.name} 引用了未注册的路由 key：${node.routeKey}`);
      continue;
    }
    items.push({ key: node.id, label: node.name, icon: route.icon, path: route.path });
  }
  return items;
}

/** 首页「可用入口」：导航树里的页面（含停用/未授权页面已被后端过滤）。 */
export function toHomeEntries(
  nodes: readonly NavigationNode[],
  resolve: (routeKey: string) => ResolvedRoute | undefined,
): HomeEntry[] {
  const entries: HomeEntry[] = [];
  for (const node of nodes) {
    if (node.type === 'DIRECTORY') {
      entries.push(...toHomeEntries(node.children, resolve));
      continue;
    }
    if (node.type !== 'PAGE' || !node.routeKey) continue;
    const route = resolve(node.routeKey);
    if (!route) continue;
    entries.push({
      key: node.id,
      title: node.name,
      description: node.description ?? '',
      path: route.path,
      icon: route.icon,
    });
  }
  return entries;
}

/** 面包屑：按当前路径在导航树里回溯「目录 → 页面」的名字。 */
export function findBreadcrumb(
  nodes: readonly NavigationNode[],
  pathname: string,
  resolve: (routeKey: string) => ResolvedRoute | undefined,
): string[] | null {
  const walk = (list: readonly NavigationNode[], trail: string[]): string[] | null => {
    for (const node of list) {
      if (node.type === 'PAGE' && node.routeKey) {
        const route = resolve(node.routeKey);
        if (route && (pathname === route.path || pathname.startsWith(`${route.path}/`))) {
          return [...trail, node.name];
        }
      }
      if (node.type === 'DIRECTORY') {
        const found = walk(node.children, [...trail, node.name]);
        if (found) return found;
      }
    }
    return null;
  };
  return walk(nodes, []);
}

export function menuTypeLabel(type: MenuType | string): string {
  switch (type) {
    case 'DIRECTORY':
      return '目录';
    case 'PAGE':
      return '页面';
    case 'TAB':
      return '页签';
    case 'BUTTON':
      return '按钮';
    default:
      return type;
  }
}

export function isMenuEnabled(status: MenuNode['status']): boolean {
  return status === 'ENABLED';
}

/** 层级规则：与后端 MenuService.requireHierarchy 一致，前端只用来限制可选项。 */
export function allowedChildTypes(parentType: MenuType | null): MenuType[] {
  switch (parentType) {
    case null:
    case 'DIRECTORY':
      return ['DIRECTORY', 'PAGE'];
    case 'PAGE':
      return ['TAB', 'BUTTON'];
    case 'TAB':
      return ['BUTTON'];
    default:
      return [];
  }
}

/**
 * 权限码建议：在页面权限码前两段之后补动作段。
 * 例如页面 system:user:read → 建议前缀 system:user:，新增按钮时只需填动作。
 */
export function permissionCodePrefix(pageCode: string | null | undefined): string {
  if (!pageCode) return '';
  const parts = pageCode.split(':');
  return parts.length >= 2 ? `${parts.slice(0, 2).join(':')}:` : '';
}

/** 扁平化菜单树，供名称/权限码查找与前端侧的自检使用。 */
export function flattenMenuTree(nodes: readonly MenuNode[]): MenuNode[] {
  const all: MenuNode[] = [];
  for (const node of nodes) {
    all.push(node, ...flattenMenuTree(node.children));
  }
  return all;
}

/** 页面节点 → 权限码映射：角色勾选树用它把已选权限码翻译成名称。 */
export function permissionNameByCode(nodes: readonly MenuNode[]): Map<string, string> {
  const map = new Map<string, string>();
  for (const node of flattenMenuTree(nodes)) {
    if (node.permissionCode) map.set(node.permissionCode, node.name);
  }
  return map;
}
