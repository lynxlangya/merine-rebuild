/**
 * 用户管理的请求函数、查询参数与失败分类。
 *
 * 只做参数拼装与类型绑定，不写进渲染逻辑；用户、单位、角色类型全部来自
 * @merine/api-contract 的生成契约，这里不手写平行模型。
 */
import type {
  ChangeStatus,
  CreateUser,
  PageResultUserSummary,
  RoleSummary,
  UnitSummary,
  UpdateUser,
  UserSummary,
} from '@merine/api-contract';
import { ApiError, request } from '../../shared/http';

/** 每页条数：与后端默认值一致，后端上限 100。 */
export const USER_PAGE_SIZE = 20;

/** 启用状态筛选：'' 表示全部，其余取值与后端 ENABLED / DISABLED 一致。 */
export type UserStatusFilter = '' | 'ENABLED' | 'DISABLED';

/** 查询条件（不含分页），'' 表示不限。 */
export interface UserFilters {
  keyword: string;
  unitCode: string;
  roleCode: string;
  status: UserStatusFilter;
}

/** 服务端分页查询，page 从 1 开始。 */
export interface UserListQuery extends UserFilters {
  page: number;
  pageSize: number;
}

export const EMPTY_USER_FILTERS: UserFilters = {
  keyword: '',
  unitCode: '',
  roleCode: '',
  status: '',
};

/** 条件变化一律回到第一页，避免把旧页码带到新结果上。 */
export function toUserListQuery(filters: UserFilters, page = 1): UserListQuery {
  return { ...filters, page, pageSize: USER_PAGE_SIZE };
}

/* ============================== 请求 ============================== */

export function fetchUserPage(query: UserListQuery, signal?: AbortSignal) {
  const params = new URLSearchParams();
  const keyword = query.keyword.trim();
  if (keyword) params.set('keyword', keyword);
  if (query.unitCode) params.set('unitCode', query.unitCode);
  if (query.roleCode) params.set('roleCode', query.roleCode);
  if (query.status) params.set('status', query.status);
  params.set('page', String(query.page));
  params.set('pageSize', String(query.pageSize));
  return request<PageResultUserSummary>(`/api/system/users?${params}`, { signal });
}

export function fetchUnits(signal?: AbortSignal) {
  return request<UnitSummary[]>('/api/system/units', { signal });
}

export function fetchRoles(signal?: AbortSignal) {
  return request<RoleSummary[]>('/api/system/roles', { signal });
}

export function createUser(input: CreateUser) {
  return request<UserSummary>('/api/system/users', {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

export function updateUser(id: string, input: UpdateUser) {
  return request<UserSummary>(`/api/system/users/${id}`, {
    method: 'PUT',
    body: JSON.stringify(input),
  });
}

/** 启用与停用走各自的动作路径；接口没有「改 status 字段」这种写法。 */
export function changeUserStatus(userIds: string[], enable: boolean) {
  const input: ChangeStatus = { userIds };
  return request<UserSummary[]>(`/api/system/users/${enable ? 'enable' : 'disable'}`, {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

/* ============================== 展示与失败分类 ============================== */

/**
 * 账号状态判定：状态取值与文案只在这里比较一次，列表、确认框与抽屉共用，
 * 避免各处各写一份 `status === 'ENABLED'`。
 */
export function isUserEnabled(status: UserSummary['status']): boolean {
  return status === 'ENABLED';
}

export function isForbiddenError(error: unknown): boolean {
  return error instanceof ApiError && (error.status === 403 || error.code === 'FORBIDDEN');
}

export function isUserNotFoundError(error: unknown): boolean {
  return error instanceof ApiError && (error.status === 404 || error.code === 'USER_NOT_FOUND');
}

/** 页码越界：服务端明确报错，界面据此回到第一页并说明原因。 */
export function isPageOutOfRangeError(error: unknown): boolean {
  return error instanceof ApiError && error.code === 'PAGE_OUT_OF_RANGE';
}

/** 失败提示统一带上请求编号，便于对照后端日志。 */
export function errorText(error: unknown): string {
  if (!(error instanceof ApiError)) return '操作失败，请稍后重试';
  return error.requestId ? `${error.message}（请求编号 ${error.requestId}）` : error.message;
}

/* ============================== 字段错误 ============================== */

/** 抽屉表单字段名，与后端请求字段一致（编辑请求的 newPassword 也落回 password）。 */
export type UserFormField = 'loginName' | 'displayName' | 'unitCode' | 'roleCodes' | 'password';

const FORM_FIELDS: readonly string[] = [
  'loginName',
  'displayName',
  'unitCode',
  'roleCodes',
  'password',
];

/**
 * 把后端 fieldErrors 映射到表单字段。
 * 字段名可能是 roleCodes[0] 这类带下标的形式，去掉下标后再匹配；
 * 认不出的字段返回给调用方展示为整体提示，不静默丢掉。
 */
export function toFormFieldErrors(fieldErrors: readonly { field: string; message: string }[]): {
  fields: Partial<Record<UserFormField, string>>;
  rest: string[];
} {
  const fields: Partial<Record<UserFormField, string>> = {};
  const rest: string[] = [];
  for (const { field, message } of fieldErrors) {
    const name = field.replace(/\[\d+\]$/, '');
    const mapped = name === 'newPassword' ? 'password' : name;
    if (FORM_FIELDS.includes(mapped)) {
      fields[mapped as UserFormField] ??= message;
    } else {
      rest.push(message);
    }
  }
  return { fields, rest };
}
