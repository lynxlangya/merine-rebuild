import { HomeOutlined, SettingOutlined, TeamOutlined } from '@ant-design/icons';
import type { ReactNode } from 'react';

export interface NavItem {
  key: string;
  label: string;
  icon: ReactNode;
  path: string;
}

export interface NavGroup {
  key: string;
  label: string;
  items: NavItem[];
}

/** 置顶项：不属于任何分组，直接排在最上面。 */
export const homeNavItem: NavItem = {
  key: 'home',
  label: '首页',
  icon: <HomeOutlined />,
  path: '/',
};

export const navGroups: NavGroup[] = [
  {
    key: 'system',
    label: '系统管理',
    items: [{ key: 'users', label: '用户管理', icon: <TeamOutlined />, path: '/system/users' }],
  },
  {
    key: 'dev',
    label: '开发工具',
    items: [
      {
        key: 'diagnostics',
        label: '工程诊断',
        icon: <SettingOutlined />,
        path: '/dev/diagnostics',
      },
    ],
  },
];

/** 路由到面包屑的映射；未命中的路径不显示面包屑而不是猜标题。 */
export const breadcrumbs: Record<string, string[]> = {
  '/': ['首页'],
  '/system/users': ['系统管理', '用户管理'],
  '/dev/diagnostics': ['开发工具', '工程诊断'],
};
