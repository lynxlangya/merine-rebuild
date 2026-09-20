/**
 * 单位管理的请求函数。
 *
 * 页面只消费这些函数和 @merine/api-contract 的生成类型，不手写平行接口模型。
 */
import type {
  CreateUnit,
  FieldError,
  UnitSummary,
  UnitTreeNode,
  UnitView,
  UpdateUnit,
} from '@merine/api-contract';
import { request } from '../../shared/http';
import { isApiErrorCode } from '../../shared/api-error';

export { errorText, isApiErrorCode, isForbiddenError } from '../../shared/api-error';

export function fetchUnitOptions(signal?: AbortSignal) {
  return request<UnitSummary[]>('/api/system/units', { signal });
}

export function fetchUnitTree(signal?: AbortSignal) {
  return request<UnitTreeNode[]>('/api/system/units/tree', { signal });
}

export function createUnit(input: CreateUnit) {
  return request<UnitView>('/api/system/units', {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

export function updateUnit(code: string, input: UpdateUnit) {
  return request<UnitView>(`/api/system/units/${encodeURIComponent(code)}`, {
    method: 'PUT',
    body: JSON.stringify(input),
  });
}

export function deleteUnit(code: string) {
  return request<null>(`/api/system/units/${encodeURIComponent(code)}`, {
    method: 'DELETE',
  });
}

/** 单位写操作的冲突码统一分类，表单和删除弹窗都能显示服务端原因。 */
export function isUnitConflict(error: unknown): boolean {
  return (
    isApiErrorCode(error, 'UNIT_CODE_TAKEN') ||
    isApiErrorCode(error, 'UNIT_VERSION_CONFLICT') ||
    isApiErrorCode(error, 'UNIT_HAS_CHILDREN') ||
    isApiErrorCode(error, 'UNIT_HAS_USERS') ||
    isApiErrorCode(error, 'UNIT_PARENT_CYCLE') ||
    isApiErrorCode(error, 'UNIT_LEVEL_LIMIT') ||
    isApiErrorCode(error, 'ROOT_ALREADY_EXISTS')
  );
}

export type UnitFormField = 'code' | 'name' | 'parentCode' | 'areaCode';

const FORM_FIELDS: readonly string[] = ['code', 'name', 'parentCode', 'areaCode'];

export function toUnitFormFieldErrors(fieldErrors: readonly FieldError[]): {
  fields: Partial<Record<UnitFormField, string>>;
  rest: string[];
} {
  const fields: Partial<Record<UnitFormField, string>> = {};
  const rest: string[] = [];
  for (const { field, message } of fieldErrors) {
    const name = field.replace(/\[\d+\]$/, '');
    if (FORM_FIELDS.includes(name)) {
      fields[name as UnitFormField] ??= message;
    } else {
      rest.push(message);
    }
  }
  return { fields, rest };
}
