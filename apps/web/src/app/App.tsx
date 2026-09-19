import { MoonOutlined, SunOutlined } from '@ant-design/icons';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App as AntApp, Button, ConfigProvider, Result, Tag, theme } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { useEffect, useState } from 'react';
import { BrowserRouter, Link, Route, Routes } from 'react-router';
import { BootstrapPage } from '../features/bootstrap/BootstrapPage';

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 1, refetchOnWindowFocus: false } },
});

export function App() {
  const [dark, setDark] = useState(() => localStorage.getItem('merine-theme') === 'dark');
  useEffect(() => {
    document.documentElement.dataset.theme = dark ? 'dark' : 'light';
    localStorage.setItem('merine-theme', dark ? 'dark' : 'light');
  }, [dark]);

  return (
    <ConfigProvider
      locale={zhCN}
      theme={{
        algorithm: dark ? theme.darkAlgorithm : theme.defaultAlgorithm,
        token: {
          colorPrimary: dark ? '#60a5fa' : '#2563eb',
          borderRadius: 10,
          fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", sans-serif',
        },
      }}
    >
      <AntApp>
        <QueryClientProvider client={queryClient}>
          <BrowserRouter>
            <header className="app-header">
              <Link to="/" className="brand">
                <span className="brand-mark">M</span>
                <span>
                  MERINE <small>rebuild</small>
                </span>
              </Link>
              <div className="header-actions">
                <Tag>本地开发</Tag>
                <Button
                  icon={dark ? <SunOutlined /> : <MoonOutlined />}
                  onClick={() => setDark(!dark)}
                >
                  {dark ? '切换亮色' : '切换暗黑'}
                </Button>
              </div>
            </header>
            <Routes>
              <Route path="/" element={<BootstrapPage />} />
              <Route
                path="*"
                element={
                  <Result
                    status="404"
                    title="页面不存在"
                    extra={<Link to="/">返回工程工作台</Link>}
                  />
                }
              />
            </Routes>
          </BrowserRouter>
        </QueryClientProvider>
      </AntApp>
    </ConfigProvider>
  );
}
