import { QueryClientProvider } from '@tanstack/react-query';
import { App as AntApp, ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { useMemo } from 'react';
import { RouterProvider } from 'react-router';
import { AuthProvider } from '../features/auth/AuthProvider';
import { queryClient } from './queryClient';
import { router } from './routes';
import { buildTheme } from './theme/antdTheme';
import { ThemeProvider, useThemeMode } from './theme/ThemeProvider';

function ThemedApp() {
  const { mode } = useThemeMode();
  // 取色依赖 data-theme 已经切换，ThemeProvider 保证它先于本次渲染落地
  const theme = useMemo(() => buildTheme(mode === 'dark'), [mode]);
  return (
    <ConfigProvider locale={zhCN} theme={theme}>
      <AntApp>
        <QueryClientProvider client={queryClient}>
          <AuthProvider>
            <RouterProvider router={router} />
          </AuthProvider>
        </QueryClientProvider>
      </AntApp>
    </ConfigProvider>
  );
}

export function App() {
  return (
    <ThemeProvider>
      <ThemedApp />
    </ThemeProvider>
  );
}
