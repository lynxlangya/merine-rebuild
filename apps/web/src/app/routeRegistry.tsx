import {
  ApartmentOutlined,
  BookOutlined,
  MenuOutlined,
  SafetyCertificateOutlined,
  SettingOutlined,
  TeamOutlined,
} from '@ant-design/icons';
import type { ReactNode } from 'react';
import { DiagnosticsPage } from '../features/diagnostics/DiagnosticsPage';
import { DictionaryListPage } from '../features/dictionaries/DictionaryListPage';
import { MenuListPage } from '../features/menus/MenuListPage';
import { RoleListPage } from '../features/roles/RoleListPage';
import { UnitListPage } from '../features/units/UnitListPage';
import { UserListPage } from '../features/users/UserListPage';

/**
 * 前端已注册页面：路由 key → 组件、路径与图标。
 *
 * 后端菜单只能引用这里存在的 key（后端 RegisteredRoutes 保存同一份 key 清单用于校验）：
 * 「要不要显示、排第几、叫什么」由数据库决定，「这个页面长什么样」由前端决定。
 * 数据库出现前端没有注册的 key 时，导航会跳过它并在控制台告警，不会把用户带进死链。
 */
export interface RegisteredRoute {
  key: string;
  path: string;
  element: ReactNode;
  icon: ReactNode;
}

export const appRoutes: RegisteredRoute[] = [
  {
    key: 'system.users',
    path: '/system/users',
    element: <UserListPage />,
    icon: <TeamOutlined />,
  },
  {
    key: 'system.roles',
    path: '/system/roles',
    element: <RoleListPage />,
    icon: <SafetyCertificateOutlined />,
  },
  {
    key: 'system.menus',
    path: '/system/menus',
    element: <MenuListPage />,
    icon: <MenuOutlined />,
  },
  {
    key: 'system.units',
    path: '/system/units',
    element: <UnitListPage />,
    icon: <ApartmentOutlined />,
  },
  {
    key: 'system.dictionaries',
    path: '/system/dictionaries',
    element: <DictionaryListPage />,
    icon: <BookOutlined />,
  },
  {
    key: 'dev.diagnostics',
    path: '/dev/diagnostics',
    element: <DiagnosticsPage />,
    icon: <SettingOutlined />,
  },
];

const byKey = new Map(appRoutes.map((route) => [route.key, route]));

export function routeByKey(key: string): RegisteredRoute | undefined {
  return byKey.get(key);
}

export function routeByPath(pathname: string): RegisteredRoute | undefined {
  return appRoutes.find(
    (route) => pathname === route.path || pathname.startsWith(`${route.path}/`),
  );
}
