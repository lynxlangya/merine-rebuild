/**
 * 单位管理的请求函数。
 *
 * 页面只消费这些函数和 @merine/api-contract 的生成类型，不手写平行接口模型。
 */
import type {
  CreateUnit,
  UnitSummary,
  UnitTreeNode,
  UnitView,
  UpdateUnit,
} from '@merine/api-contract';
import { request } from '../../shared/http';

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
