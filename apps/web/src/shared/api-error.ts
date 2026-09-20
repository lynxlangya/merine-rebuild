import { ApiError } from './http';

/** 403 统一分类：页面据此切到无权限状态，而不是把它当普通保存失败。 */
export function isForbiddenError(error: unknown): boolean {
  return error instanceof ApiError && (error.status === 403 || error.code === 'FORBIDDEN');
}

/** 失败提示统一带上请求编号，便于对照后端日志。 */
export function errorText(error: unknown): string {
  if (!(error instanceof ApiError)) return '操作失败，请稍后重试';
  return error.requestId ? `${error.message}（请求编号 ${error.requestId}）` : error.message;
}

export function isApiErrorCode(error: unknown, code: string): boolean {
  return error instanceof ApiError && error.code === code;
}
