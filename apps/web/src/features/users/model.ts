import type { UserSummary } from '@merine/api-contract';
import { ApiError } from '../../shared/http.ts';

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

/**
 * 账号状态判定：状态取值与文案只在这里比较一次，列表、确认框与抽屉共用，
 * 避免各处各写一份 `status === 'ENABLED'`。
 */
export function isUserEnabled(status: UserSummary['status']): boolean {
  return status === 'ENABLED';
}

export function isUserNotFoundError(error: unknown): boolean {
  return error instanceof ApiError && (error.status === 404 || error.code === 'USER_NOT_FOUND');
}

/** 页码越界：服务端明确报错，界面据此回到第一页并说明原因。 */
export function isPageOutOfRangeError(error: unknown): boolean {
  return error instanceof ApiError && error.code === 'PAGE_OUT_OF_RANGE';
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
