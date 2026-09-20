import { createBrowserRouter } from 'react-router';
import { LoginPage } from '../features/auth/LoginPage';
import { RequireAuth } from '../features/auth/RequireAuth';
import { HomePage } from '../features/home/HomePage';
import { AppShell } from './AppShell';
import { NotFoundPage } from './NotFoundPage';
import { appRoutes } from './routeRegistry';

/** 应用路由表由前端注册表拼装：菜单只决定可见性，页面本身仍由这里注册。 */
export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  {
    element: <RequireAuth />,
    children: [
      {
        element: <AppShell />,
        children: [
          { index: true, element: <HomePage /> },
          ...appRoutes.map((route) => ({
            path: route.path.replace(/^\//, ''),
            element: route.element,
          })),
          { path: '*', element: <NotFoundPage /> },
        ],
      },
    ],
  },
]);
