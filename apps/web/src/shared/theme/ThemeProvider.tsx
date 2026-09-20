import {
  createContext,
  useCallback,
  useContext,
  useLayoutEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';

export type ThemeMode = 'light' | 'dark';

const STORAGE_KEY = 'merine.theme';

function readStoredMode(): ThemeMode {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored === 'light' || stored === 'dark') return stored;
  } catch {
    // 本地存储不可用（隐私模式等）时回落到系统偏好
  }
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

interface ThemeContextValue {
  mode: ThemeMode;
  setMode: (mode: ThemeMode) => void;
}

const ThemeContext = createContext<ThemeContextValue | null>(null);

/**
 * 主题只改变量值与 Ant Design 配置，不重挂载业务页面：
 * 切换后查询结果、选中对象、表单输入都保留。
 */
export function ThemeProvider({ children }: { children: ReactNode }) {
  const [mode, setModeState] = useState<ThemeMode>(readStoredMode);

  // 提前到 layout 阶段写入属性：Ant Design 主题要在同一次渲染里读到正确的色值。
  useLayoutEffect(() => {
    document.documentElement.dataset.theme = mode;
    try {
      localStorage.setItem(STORAGE_KEY, mode);
    } catch {
      // 存不下不影响本次会话使用
    }
  }, [mode]);

  const setMode = useCallback((next: ThemeMode) => {
    // 先落属性再更新状态，保证 ConfigProvider 重新取色时变量已经切换
    document.documentElement.dataset.theme = next;
    setModeState(next);
  }, []);
  const value = useMemo(() => ({ mode, setMode }), [mode, setMode]);

  return <ThemeContext value={value}>{children}</ThemeContext>;
}

export function useThemeMode(): ThemeContextValue {
  const value = useContext(ThemeContext);
  if (!value) throw new Error('useThemeMode 必须在 ThemeProvider 内使用');
  return value;
}
