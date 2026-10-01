import { useEffect, useRef } from 'react';

/** 条件挂载的弹窗卸载后归还焦点；已跳页的入口和 StrictMode 的模拟清理不抢焦点。 */
export function useRestoreFocus() {
  const trigger = useRef(document.activeElement);
  const mounted = useRef(false);
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
      requestAnimationFrame(() => {
        const element = trigger.current;
        if (!mounted.current && element instanceof HTMLElement && element.isConnected)
          element.focus({ preventScroll: true });
      });
    };
  }, []);
}
