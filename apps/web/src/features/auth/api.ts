import type { AuthUser, LoginRequest } from '@merine/api-contract';
import { ApiError, request } from '../../shared/http.ts';

export type { AuthUser };

/**
 * 身份类型来自生成契约（packages/api-contract），不在这里重复声明字段。
 * 权限事实不由前端拼装：本轮后端只下发角色名，功能权限与数据范围留到后续阶段。
 */
export type LoginInput = LoginRequest;

/** 未登录时后端返回 401，由调用方按“未登录”处理而不是错误提示。 */
export function fetchSession(signal?: AbortSignal) {
  return request<AuthUser>('/api/auth/session', { signal });
}

export function login(input: LoginInput) {
  return request<AuthUser>('/api/auth/session', {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

export async function logout(): Promise<void> {
  try {
    await request<null>('/api/auth/session', { method: 'DELETE' });
  } catch (error) {
    // 401 已明确确认原会话失效；网络、CSRF 和服务失败仍需保留退出重试入口。
    if (!(error instanceof ApiError && error.status === 401)) throw error;
  }
}
