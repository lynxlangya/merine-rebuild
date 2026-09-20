/**
 * 字典查询 key 与 hooks。
 *
 * 会话级缓存：AppShell 挂载时用 useDictionariesQuery() 拉一次全部字典，
 * 页面里用 useDictionary(code) 读同一份缓存（同一个 queryKey，不会重复请求）。
 * 字典维护成功后失效 ['dictionaries'] 前缀；登录/退出/401 由 queryClient.clear() 兜底。
 * 刻意不写 localStorage：跨账号串用与版本陈旧都比"省一次请求"代价更大。
 */
import { useQuery } from '@tanstack/react-query';
import { fetchDictionaries, fetchDictionaryAdminList, fetchDictionaryDetail } from './api';

/** 字典是低频参考数据：一次会话内缓存 30 分钟，写路径会显式失效。 */
export const DICTIONARY_STALE_TIME = 30 * 60 * 1000;

export const dictionaryKeys = {
  all: ['dictionaries'] as const,
  // 显式带 'list' 段：否则失效列表 key 会按前缀命中详情 key，一次保存要发两遍详情请求。
  adminList: ['system', 'dictionaries', 'list'] as const,
  adminDetail: (code: string) => ['system', 'dictionaries', 'detail', code] as const,
};

export function useDictionariesQuery(codes?: string[]) {
  return useQuery({
    queryKey:
      codes && codes.length > 0 ? [...dictionaryKeys.all, [...codes].sort()] : dictionaryKeys.all,
    queryFn: ({ signal }) => fetchDictionaries(codes, signal),
    staleTime: DICTIONARY_STALE_TIME,
  });
}

/** 单本字典：与批量查询共用同一个缓存条目，挂载顺序不影响请求次数。 */
export function useDictionary(code: string) {
  return useQuery({
    queryKey: dictionaryKeys.all,
    queryFn: ({ signal }) => fetchDictionaries(undefined, signal),
    select: (dictionaries) => dictionaries.find((dictionary) => dictionary.code === code),
    staleTime: DICTIONARY_STALE_TIME,
  });
}

export function useDictionaryAdminListQuery(enabled: boolean) {
  return useQuery({
    queryKey: dictionaryKeys.adminList,
    queryFn: ({ signal }) => fetchDictionaryAdminList(signal),
    enabled,
    staleTime: 0,
  });
}

export function useDictionaryDetailQuery(code: string | null) {
  return useQuery({
    queryKey: dictionaryKeys.adminDetail(code ?? ''),
    queryFn: ({ signal }) => fetchDictionaryDetail(code as string, signal),
    enabled: code !== null,
    staleTime: 0,
  });
}
