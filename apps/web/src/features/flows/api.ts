import type {
  IntelligenceDetail,
  IntelligenceUnitOption,
  PageResultIntelligenceListItem,
} from '@merine/api-contract';
import { request } from '../../shared/http.ts';
import type { FlowCommand, UnitAction } from './actions';

const root = '/api/intelligence-topics';
export function fetchTopics(
  view: string,
  status: string,
  keyword: string,
  page: number,
  signal?: AbortSignal,
) {
  const query = new URLSearchParams({ view, status, keyword, page: String(page), pageSize: '20' });
  return request<PageResultIntelligenceListItem>(`${root}?${query}`, { signal });
}
export function fetchTopic(id: string, signal?: AbortSignal) {
  return request<IntelligenceDetail>(`${root}/${encodeURIComponent(id)}`, { signal });
}
export function fetchUnits(
  action: UnitAction,
  topicId?: string,
  receiptId?: string,
  signal?: AbortSignal,
) {
  const query = new URLSearchParams({ action });
  if (topicId) query.set('topicId', topicId);
  if (receiptId) query.set('receiptId', receiptId);
  return request<IntelligenceUnitOption[]>(`${root}/unit-options?${query}`, { signal });
}
export function executeFlowCommand(command: FlowCommand, key: string) {
  const topic = 'topicId' in command ? `${root}/${encodeURIComponent(command.topicId)}` : root;
  let path: string;
  switch (command.code) {
    case 'create':
    case 'update':
      path = topic;
      break;
    case 'send':
      path = `${topic}/send`;
      break;
    case 'supplement':
      path = `${topic}/supplements`;
      break;
    case 'sign':
    case 'feedbacks':
    case 'forward':
      path = `${topic}/receipts/${encodeURIComponent(command.receiptId)}/${command.code}`;
      break;
  }
  return request<IntelligenceDetail>(path, {
    method: command.code === 'update' ? 'PUT' : 'POST',
    headers: { 'Idempotency-Key': key },
    ...('body' in command ? { body: JSON.stringify(command.body) } : {}),
  });
}
