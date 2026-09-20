import { Navigate, Outlet, useLocation } from 'react-router';
import { defaultEntryPath } from './redirect';
import { useAuth } from './AuthProvider';
import { FullPageError, FullPageLoading } from '../../shared/ui/FullPageState';

/**
 * 受保护路由。身份恢复期间只显示加载态，不提前跳转，避免刷新时闪一下登录页。
 */
export function RequireAuth() {
  const { state, retryRestore } = useAuth();
  const location = useLocation();

  if (state.status === 'restoring') {
    return <FullPageLoading description="正在恢复登录状态…" />;
  }

  if (state.status === 'unavailable') {
    return (
      <FullPageError
        title="暂时无法确认登录状态"
        description={`${state.message}。为免误判为未登录，本页没有跳转到登录页。`}
        onRetry={retryRestore}
      />
    );
  }

  if (state.status === 'anonymous') {
    const from = `${location.pathname}${location.search}`;
    const redirect = from === defaultEntryPath ? '' : `?redirect=${encodeURIComponent(from)}`;
    return <Navigate to={`/login${redirect}`} replace />;
  }

  return <Outlet />;
}
