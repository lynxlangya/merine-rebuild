import {
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  MoonOutlined,
  SunOutlined,
} from '@ant-design/icons';
import {
  App,
  Avatar,
  Breadcrumb,
  Button,
  Dropdown,
  Layout,
  Menu,
  Segmented,
  Tooltip,
  Typography,
} from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router';
import { useAuth } from '../features/auth/AuthProvider';
import { ApiError } from '../shared/http';
import { breadcrumbs, homeNavItem, navGroups, type NavItem } from './navigation';
import { useThemeMode } from './theme/ThemeProvider';
import styles from './AppShell.module.css';

const NAV_STORAGE_KEY = 'merine.nav';

function initialCollapsed(): boolean {
  try {
    const stored = localStorage.getItem(NAV_STORAGE_KEY);
    if (stored === 'collapsed') return true;
    if (stored === 'expanded') return false;
  } catch {
    // 读不到就按宽度判断
  }
  return window.innerWidth <= 1439;
}

function activeKeyOf(pathname: string, items: NavItem[]): string | undefined {
  const exact = items.find((item) => item.path === pathname);
  if (exact) return exact.key;
  return items.find((item) => item.path && pathname.startsWith(`${item.path}/`))?.key;
}

export function AppShell() {
  const { message } = App.useApp();
  const [signingOut, setSigningOut] = useState(false);
  const [collapsed, setCollapsed] = useState(initialCollapsed);
  const { state, signOut } = useAuth();
  const { mode, setMode } = useThemeMode();
  const location = useLocation();
  const navigate = useNavigate();

  // 窄屏（1366×768 是本轮验收尺寸）优先折叠导航，把宽度让给内容。
  // 只响应“跨入窄屏”，变宽不自动展开——那会覆盖用户刚刚的手动选择。
  useEffect(() => {
    const narrow = window.matchMedia('(max-width: 1439px)');
    const collapseWhenNarrow = (matches: boolean) =>
      setCollapsed((previous) => matches || previous);
    collapseWhenNarrow(narrow.matches);
    const listener = (event: MediaQueryListEvent) => collapseWhenNarrow(event.matches);
    narrow.addEventListener('change', listener);
    return () => narrow.removeEventListener('change', listener);
  }, []);

  const allItems = useMemo(() => [homeNavItem, ...navGroups.flatMap((group) => group.items)], []);
  const activeKey = activeKeyOf(location.pathname, allItems);
  const user = state.status === 'authenticated' ? state.user : null;

  const toggleNav = () => {
    setCollapsed((previous) => {
      const next = !previous;
      try {
        localStorage.setItem(NAV_STORAGE_KEY, next ? 'collapsed' : 'expanded');
      } catch {
        // 折叠偏好存不下不影响本次使用
      }
      return next;
    });
  };

  const menuItems = useMemo(
    () => [
      // 首页不分组，固定排在最上面
      { key: homeNavItem.key, icon: homeNavItem.icon, label: homeNavItem.label },
      ...navGroups.map((group) => ({
        type: 'group' as const,
        key: group.key,
        label: group.label,
        children: group.items.map((item) => ({
          key: item.key,
          icon: item.icon,
          label: item.label,
        })),
      })),
    ],
    [],
  );

  const pathByKey = useMemo(() => {
    const map = new Map<string, string>();
    for (const item of allItems) if (item.path) map.set(item.key, item.path);
    return map;
  }, [allItems]);

  const crumbs = breadcrumbs[location.pathname];

  const handleSignOut = async () => {
    if (signingOut) return;
    setSigningOut(true);
    try {
      await signOut();
    } catch (error) {
      message.error(`退出未完成：${error instanceof ApiError ? error.message : '请稍后重试'}`);
    } finally {
      setSigningOut(false);
    }
  };

  return (
    <Layout className={styles.app} data-nav={collapsed ? 'collapsed' : 'expanded'}>
      <Layout.Sider
        className={styles.nav}
        width={collapsed ? 64 : 224}
        collapsedWidth={64}
        collapsed={collapsed}
        trigger={null}
        theme="light"
      >
        <div className={styles.brand}>
          <img className={styles.brandMark} src="/brand-mark.svg" alt="海防研判标识" />
          {!collapsed && (
            <span className={styles.brandText}>
              <strong>海防研判工作台</strong>
            </span>
          )}
        </div>
        <Menu
          className={styles.menu}
          mode="inline"
          inlineCollapsed={collapsed}
          selectedKeys={activeKey ? [activeKey] : []}
          items={menuItems}
          onClick={({ key }) => {
            const path = pathByKey.get(key);
            if (path) void navigate(path);
          }}
        />
      </Layout.Sider>

      <Layout className={styles.main}>
        <Layout.Header className={styles.topbar}>
          <Tooltip title={collapsed ? '展开导航' : '折叠导航'}>
            <Button
              type="text"
              aria-label={collapsed ? '展开导航' : '折叠导航'}
              icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
              onClick={toggleNav}
            />
          </Tooltip>
          {crumbs && (
            <Breadcrumb
              className={styles.crumbs}
              items={crumbs.map((title, index) => ({
                title: index === crumbs.length - 1 ? <strong>{title}</strong> : title,
              }))}
            />
          )}
          <div className={styles.topbarRight}>
            <Segmented
              aria-label="主题模式"
              value={mode}
              onChange={(value) => setMode(value as 'light' | 'dark')}
              options={[
                { value: 'light', icon: <SunOutlined />, label: '亮色' },
                { value: 'dark', icon: <MoonOutlined />, label: '暗黑' },
              ]}
            />
            {user && (
              <Dropdown
                trigger={['click']}
                menu={{
                  items: [
                    {
                      key: 'sign-out',
                      icon: <LogoutOutlined />,
                      label: signingOut ? '正在退出…' : '退出登录',
                      disabled: signingOut,
                      // 退出后由 RequireAuth 统一跳转登录页：这里再 navigate 一次会与
                      // 它的重定向抢同一个结果，形成竞态，且回跳目标由谁写不确定。
                      onClick: () => void handleSignOut(),
                    },
                  ],
                }}
              >
                <Button type="text" className={styles.user}>
                  <Avatar size={28} className={styles.avatar}>
                    {user.displayName.slice(0, 1)}
                  </Avatar>
                  <span className={styles.userText}>
                    <Typography.Text strong>{user.displayName}</Typography.Text>
                    <Typography.Text type="secondary" className={styles.userMeta}>
                      {user.unitName} · {user.roleNames.join('、') || '未分配角色'}
                    </Typography.Text>
                  </span>
                </Button>
              </Dropdown>
            )}
          </div>
        </Layout.Header>

        <Layout.Content className={styles.workspace}>
          <Outlet />
        </Layout.Content>
      </Layout>
    </Layout>
  );
}
