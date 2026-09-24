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
  /** 菜单配置的图标名称；渲染侧用 iconRegistry 解析，未配置时用默认图标 */
  iconName?: string;
  path?: string;
  children?: NavItem[];
}

export interface HomeEntry {
  key: string;
  title: string;
  description: string;
  path: string;
  icon?: ReactNode;
  /** 菜单配置的图标名称；渲染侧用 iconRegistry 解析，未配置时用默认图标 */
  iconName?: string;
}

/**
 * 导航树只保留目录与页面：页签与按钮属于页面内部，不进左侧菜单。
 * 页面必须能在前端注册表里找到组件与默认图标；找不到就跳过并告警——
 * 后端只校验 route key 在注册清单里，前端注册表滞后时不能把用户带进死链。
 * 数据库配置的 iconName 原样带上，由渲染侧（iconRegistry）解析并回落到默认图标。
 */
export function toNavItems(
  nodes: readonly NavigationNode[],
  resolve: (routeKey: string) => ResolvedRoute | undefined,
): NavItem[] {
  const items: NavItem[] = [];
  for (const node of nodes) {
    if (node.type === 'DIRECTORY') {
      const children = toNavItems(node.children, resolve);
      if (children.length > 0) {
        items.push({
          key: node.id,
          label: node.name,
          iconName: node.iconName ?? undefined,
          children,
        });
      }
      continue;
    }
    if (node.type !== 'PAGE' || !node.routeKey) continue;
    const route = resolve(node.routeKey);
    if (!route) {
      console.warn(`菜单 ${node.name} 引用了未注册的路由 key：${node.routeKey}`);
      continue;
    }
    items.push({
      key: node.id,
      label: node.name,
      icon: route.icon,
      iconName: node.iconName ?? undefined,
      path: route.path,
    });
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
      iconName: node.iconName ?? undefined,
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
 * 表格行：叶子节点（按钮、没有下级的页签）不保留 children 字段。
 * 接口对没有子节点的节点返回空数组，而 AntD 表格只要看到 children 就会画展开图标，
 * 于是按钮行前面会多出一个点了没反应的折叠箭头。
 */
export type MenuRow = Omit<MenuNode, 'children'> & { children?: MenuRow[] };

export function toMenuRows(nodes: readonly MenuNode[]): MenuRow[] {
  return nodes.map((node) => {
    const { children, ...rest } = node;
    return children.length > 0 ? { ...rest, children: toMenuRows(children) } : rest;
  });
}

/**
 * 默认展开：每个一级（目录）都展开到二级，再展开第一个还有下级的二级节点到三级。
 * 一进来能看清结构，又不会把每个页面的按钮一次性铺满整屏。
 */
export function defaultMenuExpandedKeys(rows: readonly MenuRow[]): string[] {
  const keys: string[] = [];
  let expandedFirstChild = false;
  for (const row of rows) {
    if (!row.children?.length) continue;
    keys.push(row.id);
    if (expandedFirstChild) continue;
    const firstWithChildren = row.children.find((child) => child.children?.length);
    if (firstWithChildren) {
      keys.push(firstWithChildren.id);
      expandedFirstChild = true;
    }
  }
  return keys;
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
