import {
  ApartmentOutlined,
  BookOutlined,
  MenuOutlined,
  SafetyCertificateOutlined,
  SettingOutlined,
  SwapOutlined,
  TeamOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import { lazy, type ReactNode } from 'react';

/**
 * 页面按路由懒加载：每个模块单独成块，进哪页下哪页，首屏不再背着六个页面的代码。
 * Suspense 边界统一放在 AppShell 的 Outlet 外层，切换时保留导航与骨架。
 */
const UserListPage = lazy(() =>
  import('../features/users/UserListPage').then((module) => ({ default: module.UserListPage })),
);
const RoleListPage = lazy(() =>
  import('../features/roles/RoleListPage').then((module) => ({ default: module.RoleListPage })),
);
const MenuListPage = lazy(() =>
  import('../features/menus/MenuListPage').then((module) => ({ default: module.MenuListPage })),
);
const UnitListPage = lazy(() =>
  import('../features/units/UnitListPage').then((module) => ({ default: module.UnitListPage })),
);
const DictionaryListPage = lazy(() =>
  import('../features/dictionaries/DictionaryListPage').then((module) => ({
    default: module.DictionaryListPage,
  })),
);
const DiagnosticsPage = lazy(() =>
  import('../features/diagnostics/DiagnosticsPage').then((module) => ({
    default: module.DiagnosticsPage,
  })),
);
export const TaskHandlingPage = lazy(() =>
  import('../features/tasks/TaskHandlingPage').then((module) => ({
    default: module.TaskHandlingPage,
  })),
);
const InformationFlowPage = lazy(() =>
  import('../features/flows/InformationFlowPage').then((module) => ({
    default: module.InformationFlowPage,
  })),
);

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
    key: 'collaboration.tasks',
    path: '/collaboration/tasks',
    element: <TaskHandlingPage />,
    icon: <ThunderboltOutlined />,
  },
  {
    key: 'collaboration.flows',
    path: '/collaboration/flows',
    element: <InformationFlowPage />,
    icon: <SwapOutlined />,
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
