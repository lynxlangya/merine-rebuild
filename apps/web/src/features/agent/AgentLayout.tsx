import { BarChartOutlined, HomeOutlined, ReadOutlined } from '@ant-design/icons';
import { Tooltip } from 'antd';
import { Suspense } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router';
import { AgentAccountMenu } from './AgentAccountMenu';
import { AgentChatProvider } from './AgentChatContext';
import { AgentSessionSidebar } from './AgentSessionSidebar';
import type { AgentNavigationResolver } from './navigation';
import styles from './AgentLayout.module.css';

/** 对话栏只在聊天主页展示：设置、资料库等页面保留图标栏，不显示会话列表。 */
function isChatRoute(pathname: string): boolean {
  return pathname === '/agent' || pathname === '/agent/';
}

/**
 * 助手外壳：图标栏 + 对话栏（仅聊天主页）+ 工作区。
 * 对话状态由 AgentChatProvider 统一持有；站内来源解析由 app 装配时注入（feature 不导入 app）。
 */
export function AgentLayout({ resolveSource }: { resolveSource: AgentNavigationResolver }) {
  const location = useLocation();
  return (
    <AgentChatProvider resolveSource={resolveSource}>
      <div className={styles.layout}>
        <nav className={styles.rail} aria-label="助手导航">
          <Tooltip title="主页" placement="right">
            <NavLink
              to="/agent"
              end
              aria-label="主页"
              className={({ isActive }) => `${styles.navItem} ${isActive ? styles.active : ''}`}
            >
              <HomeOutlined />
            </NavLink>
          </Tooltip>
          <Tooltip title="资料库" placement="right">
            <NavLink
              to="/agent/library"
              aria-label="资料库"
              className={({ isActive }) => `${styles.navItem} ${isActive ? styles.active : ''}`}
            >
              <ReadOutlined />
            </NavLink>
          </Tooltip>
          <Tooltip title="用量" placement="right">
            <NavLink
              to="/agent/usage"
              aria-label="用量"
              className={({ isActive }) => `${styles.navItem} ${isActive ? styles.active : ''}`}
            >
              <BarChartOutlined />
            </NavLink>
          </Tooltip>
          <div className={styles.account}>
            <AgentAccountMenu />
          </div>
        </nav>
        {isChatRoute(location.pathname) && <AgentSessionSidebar id="agent-sessions" />}
        <main className={styles.workspace}>
          <Suspense fallback={<div className={styles.pageLoading}>正在加载助手页面…</div>}>
            <Outlet />
          </Suspense>
        </main>
      </div>
    </AgentChatProvider>
  );
}
