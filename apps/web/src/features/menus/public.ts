/**
 * 菜单 feature 的显式出口。
 *
 * 导航（/api/me/menus）由 app 外壳与首页消费，菜单管理页自己用管理树；
 * 两者都从这里导出，避免其它模块深导入内部文件。
 */
export { menuKeys, useMenuTreeQuery, useMyMenusQuery, useRouteKeysQuery } from './queries';
export {
  findBreadcrumb,
  flattenMenuTree,
  menuTypeLabel,
  toHomeEntries,
  toNavItems,
  type HomeEntry,
  type MenuType,
  type NavItem,
  type ResolvedRoute,
} from './model';
