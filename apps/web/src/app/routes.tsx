import { Suspense, lazy } from 'react';
import { createBrowserRouter } from 'react-router';
import { LoginPage } from '../features/auth/LoginPage';
import { RequireAuth } from '../features/auth/RequireAuth';
import { HomePage } from '../features/home/HomePage';
import { resolveAgentSource } from './agentNavigation';
import { AppShell } from './AppShell';
import { NotFoundPage } from './NotFoundPage';
import { appRoutes, TaskHandlingPage, InformationFlowPage } from './routeRegistry';
import styles from './routes.module.css';

// 助手整条链路独立成块：未进入 /agent 的用户不加载对话与部件渲染代码。
const AgentLayout = lazy(() =>
  import('../features/agent/AgentLayout').then((module) => ({ default: module.AgentLayout })),
);
const AgentHomePage = lazy(() =>
  import('../features/agent/AgentHomePage').then((module) => ({ default: module.AgentHomePage })),
);
const AgentLibraryPage = lazy(() =>
  import('../features/agent/AgentLibraryPage').then((module) => ({
    default: module.AgentLibraryPage,
  })),
);
const AgentUsagePage = lazy(() =>
  import('../features/agent/AgentUsagePage').then((module) => ({
    default: module.AgentUsagePage,
  })),
);
const AgentSettingsPage = lazy(() =>
  import('../features/agent/AgentSettingsPage').then((module) => ({
    default: module.AgentSettingsPage,
  })),
);

function AgentFallback() {
  return (
    <div className={styles.fallback} role="status" aria-live="polite">
      正在加载海防助手…
    </div>
  );
}

/** 应用路由表由前端注册表拼装：菜单只决定可见性，页面本身仍由这里注册。 */
export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  {
    element: <RequireAuth />,
    children: [
      {
        path: 'agent',
        element: (
          <Suspense fallback={<AgentFallback />}>
            <AgentLayout resolveSource={resolveAgentSource} />
          </Suspense>
        ),
        children: [
          { index: true, element: <AgentHomePage /> },
          { path: 'library', element: <AgentLibraryPage /> },
          { path: 'usage', element: <AgentUsagePage /> },
          { path: 'settings', element: <AgentSettingsPage /> },
          { path: '*', element: <NotFoundPage /> },
        ],
      },
      {
        element: <AppShell />,
        children: [
          { index: true, element: <HomePage /> },
          ...appRoutes.map((route) => ({
            path: route.path.replace(/^\//, ''),
            element: route.element,
          })),
          { path: 'collaboration/tasks/:taskId', element: <TaskHandlingPage /> },
          { path: 'collaboration/flows/:topicId', element: <InformationFlowPage /> },
          { path: '*', element: <NotFoundPage /> },
        ],
      },
    ],
  },
]);
