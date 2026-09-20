/** 字典的 HTTP 请求与生成契约绑定。 */
import type {
  CreateDictionary,
  CreateDictionaryItem,
  DictionaryItemView,
  DictionaryListItem,
  DictionaryView,
  UpdateDictionary,
  UpdateDictionaryItem,
} from '@merine/api-contract';
import { request } from '../../shared/http';

/** 使用侧读取：登录即可，不占权限码；不传 codes 时返回全部字典。 */
export function fetchDictionaries(codes?: string[], signal?: AbortSignal) {
  const query = codes && codes.length > 0 ? `?codes=${encodeURIComponent(codes.join(','))}` : '';
  return request<DictionaryView[]>(`/api/dictionaries${query}`, { signal });
}

export function fetchDictionaryAdminList(signal?: AbortSignal) {
  return request<DictionaryListItem[]>('/api/system/dictionaries', { signal });
}

export function fetchDictionaryDetail(code: string, signal?: AbortSignal) {
  return request<DictionaryView>(`/api/system/dictionaries/${encodeURIComponent(code)}`, {
    signal,
  });
}

export function createDictionary(input: CreateDictionary) {
  return request<DictionaryView>('/api/system/dictionaries', {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

export function updateDictionary(code: string, input: UpdateDictionary) {
  return request<DictionaryView>(`/api/system/dictionaries/${encodeURIComponent(code)}`, {
    method: 'PUT',
    body: JSON.stringify(input),
  });
}

export function createDictionaryItem(code: string, input: CreateDictionaryItem) {
  return request<DictionaryItemView>(`/api/system/dictionaries/${encodeURIComponent(code)}/items`, {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

export function updateDictionaryItem(code: string, value: string, input: UpdateDictionaryItem) {
  return request<DictionaryItemView>(
    `/api/system/dictionaries/${encodeURIComponent(code)}/items/${encodeURIComponent(value)}`,
    { method: 'PUT', body: JSON.stringify(input) },
  );
}
