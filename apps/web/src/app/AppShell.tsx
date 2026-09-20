import {
  FolderOutlined,
  HomeOutlined,
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
import {
  findBreadcrumb,
  toNavItems,
  useMyMenusQuery,
  type NavItem,
} from '../features/menus/public';
import { ApiError } from '../shared/http';
import { useThemeMode } from '../shared/theme/ThemeProvider';
import { routeByKey } from './routeRegistry';
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

/** 首页不属于菜单权限模型：登录即可见，固定排在最上面。 */
const HOME_ITEM: NavItem = { key: 'home', label: '首页', icon: <HomeOutlined />, path: '/' };

function flattenNav(items: readonly NavItem[]): NavItem[] {
  const all: NavItem[] = [];
  for (const item of items) {
    all.push(item);
    if (item.children) all.push(...flattenNav(item.children));
  }
  return all;
}

/**
 * 目录在前端注册表里没有图标（它不对应具体页面），但折叠态侧栏必须有图标才不会把标题竖排挤成两行，
 * 因此统一给目录一个文件夹图标。
 */
function withFolderIcons(item: NavItem): NavItem {
  return {
    ...item,
    icon: item.icon ?? <FolderOutlined />,
    children: item.children?.map(withFolderIcons),
  };
}

function activeKeyOf(pathname: string, items: readonly NavItem[]): string | undefined {
  const flat = flattenNav(items);
  const exact = flat.find((item) => item.path === pathname);
  if (exact) return exact.key;
  return flat.find((item) => item.path && pathname.startsWith(`${item.path}/`))?.key;
}

export function AppShell() {
  const { message } = App.useApp();
  const [signingOut, setSigningOut] = useState(false);
  const [collapsed, setCollapsed] = useState(initialCollapsed);
  const { state, signOut } = useAuth();
  const { mode, setMode } = useThemeMode();
  const location = useLocation();
  const navigate = useNavigate();
  const myMenus = useMyMenusQuery();

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

  const user = state.status === 'authenticated' ? state.user : null;
  // 导航完全由后端按会话权限下发；前端只把 route key 映射成组件路径与图标。
  const resolveRoute = useMemo(
    () => (routeKey: string) => {
      const route = routeByKey(routeKey);
      return route ? { path: route.path, icon: route.icon } : undefined;
    },
    [],
  );
  const navItems = useMemo(
    () => toNavItems(myMenus.data ?? [], resolveRoute).map(withFolderIcons),
    [myMenus.data, resolveRoute],
  );
  const visibleItems = useMemo(() => [HOME_ITEM, ...navItems], [navItems]);
  const activeKey = activeKeyOf(location.pathname, visibleItems);

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
      { key: HOME_ITEM.key, icon: HOME_ITEM.icon, label: HOME_ITEM.label },
      ...navItems.map((item) => ({
        key: item.key,
        icon: item.icon,
        label: item.label,
        children: item.children?.map((child) => ({
          key: child.key,
          icon: child.icon,
          label: child.label,
        })),
      })),
    ],
    [navItems],
  );

  const pathByKey = useMemo(() => {
    const map = new Map<string, string>();
    for (const item of flattenNav(visibleItems)) if (item.path) map.set(item.key, item.path);
    return map;
  }, [visibleItems]);

  const crumbs = useMemo(
    () => findBreadcrumb(myMenus.data ?? [], location.pathname, resolveRoute),
    [myMenus.data, location.pathname, resolveRoute],
  );

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
        {!collapsed && !myMenus.isPending && navItems.length === 0 && (
          <p className={styles.emptyNav}>
            当前账号没有任何可用菜单。请联系管理员为你的角色勾选页面权限。
          </p>
        )}
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
