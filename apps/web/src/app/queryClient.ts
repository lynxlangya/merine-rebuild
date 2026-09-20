import { QueryClient } from '@tanstack/react-query';
import { ApiError, isAbortError } from '../shared/http';

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      // 4xx 是明确的业务结果，重试没有意义；网络与 5xx 才重试一次。
      retry: (failureCount, error) => {
        if (isAbortError(error)) return false;
        if (error instanceof ApiError && error.status >= 400 && error.status < 500) return false;
        return failureCount < 1;
      },
    },
    mutations: {
      // 写请求不自动重试，避免重复提交
      retry: false,
    },
  },
});
