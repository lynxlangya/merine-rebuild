import type { components } from './generated/auth';

/** 认证与公共错误字段：显式导出生成契约，字段定义仍来自后端。 */
export type AuthUser = components['schemas']['AuthUserResponse'];
export type LoginRequest = components['schemas']['LoginRequest'];
export type DevLoginRequest = components['schemas']['DevLoginRequest'];
export type DevLoginAccountOption = components['schemas']['DevLoginAccountOption'];
export type FieldError = components['schemas']['FieldError'];
