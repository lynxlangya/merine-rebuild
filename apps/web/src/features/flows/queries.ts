export const flowKeys = {
  lists: (userId?: string) => ['intelligence', userId, 'list'] as const,
  list: (userId: string | undefined, view: string, status: string, keyword: string, page: number) =>
    [...flowKeys.lists(userId), view, status, keyword, page] as const,
  detail: (userId?: string, topicId?: string) =>
    ['intelligence', userId, 'detail', topicId] as const,
  units: (userId?: string) => ['intelligence', userId, 'units'] as const,
};
