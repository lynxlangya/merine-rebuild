import { useQueryClient } from '@tanstack/react-query';
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import { ApiError, isAbortError, onUnauthorized } from '../../shared/http';
import {
  fetchSession,
  login,
  loginForDevelopment,
  logout,
  type AuthUser,
  type LoginInput,
} from './api';

export type AuthState =
  | { status: 'restoring' }
  | { status: 'authenticated'; user: AuthUser }
  | { status: 'anonymous' }
  | { status: 'unavailable'; message: string };

interface AuthContextValue {
  state: AuthState;
  signIn: (input: LoginInput) => Promise<AuthUser>;
  signInDev: (loginName: string, rememberMe: boolean) => Promise<AuthUser>;
  signOut: () => Promise<void>;
  retryRestore: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [state, setState] = useState<AuthState>({ status: 'restoring' });
  const [attempt, setAttempt] = useState(0);
  const statusRef = useRef<AuthState['status']>('restoring');
  statusRef.current = state.status;

  // 恢复身份：服务端是唯一事实来源，前端不保留任何登录标志。
  useEffect(() => {
    const controller = new AbortController();
    setState({ status: 'restoring' });
    fetchSession(controller.signal)
      .then((user) => setState({ status: 'authenticated', user }))
      .catch((error: unknown) => {
        if (isAbortError(error)) return;
        // 401 表示未登录这一正常状态；其余按服务不可用处理，避免误导成“请登录”。
        if (error instanceof ApiError && (error.status === 401 || error.status === 403)) {
          setState({ status: 'anonymous' });
        } else {
          setState({
            status: 'unavailable',
            message: error instanceof ApiError ? error.message : '无法连接服务，请稍后重试',
          });
        }
      });
    return () => controller.abort();
  }, [attempt]);

  // 会话失效统一处理：只在确已登录时清理一次，并发 401 不会再触发第二轮跳转与提示。
  useEffect(
    () =>
      onUnauthorized(() => {
        if (statusRef.current !== 'authenticated') return;
        statusRef.current = 'anonymous';
        void queryClient.cancelQueries();
        queryClient.clear();
        setState({ status: 'anonymous' });
      }),
    [queryClient],
  );

  const acceptLogin = useCallback(
    (user: AuthUser) => {
      queryClient.clear();
      statusRef.current = 'authenticated';
      setState({ status: 'authenticated', user });
      return user;
    },
    [queryClient],
  );

  const signIn = useCallback(
    async (input: LoginInput) => acceptLogin(await login(input)),
    [acceptLogin],
  );

  const signInDev = useCallback(
    async (loginName: string, rememberMe: boolean) =>
      acceptLogin(await loginForDevelopment({ loginName, rememberMe })),
    [acceptLogin],
  );

  const signOut = useCallback(async () => {
    await logout();
    // 只有服务端确认退出或原会话已失效，才完成本地退出；失败由调用方提示重试。
    queryClient.clear();
    statusRef.current = 'anonymous';
    setState({ status: 'anonymous' });
  }, [queryClient]);

  const value = useMemo<AuthContextValue>(
    () => ({ state, signIn, signInDev, signOut, retryRestore: () => setAttempt((n) => n + 1) }),
    [state, signIn, signInDev, signOut],
  );

  return <AuthContext value={value}>{children}</AuthContext>;
}

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext);
  if (!value) throw new Error('useAuth 必须在 AuthProvider 内使用');
  return value;
}
