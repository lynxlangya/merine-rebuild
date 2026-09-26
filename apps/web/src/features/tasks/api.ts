import type {
  CreateTask,
  DispatchTask,
  PageResultTaskListItem,
  TaskDetail,
  TaskUnitOption,
  SubmitTaskResult,
  RequestTaskTransfer,
  RespondTaskTransfer,
  DecideTaskTransfer,
} from '@merine/api-contract';
import { request } from '../../shared/http';

export function fetchTasks(tab: string, page: number, signal?: AbortSignal) {
  return request<PageResultTaskListItem>(
    `/api/tasks?tab=${encodeURIComponent(tab)}&page=${page}&pageSize=20`,
    { signal },
  );
}
export function fetchTask(id: string, signal?: AbortSignal) {
  return request<TaskDetail>(`/api/tasks/${encodeURIComponent(id)}`, { signal });
}
export function fetchTaskTargets(action: string, taskId?: string, branchId?: string) {
  const params = new URLSearchParams({ action });
  if (taskId) params.set('taskId', taskId);
  if (branchId) params.set('branchId', branchId);
  return request<TaskUnitOption[]>(`/api/tasks/target-units?${params}`);
}
export function createTask(input: CreateTask, key: string) {
  return request<TaskDetail>('/api/tasks', {
    method: 'POST',
    headers: { 'Idempotency-Key': key },
    body: JSON.stringify(input),
  });
}
export function taskAction(
  id: string,
  branchId: string,
  action: string,
  key: string,
  input?:
    | DispatchTask
    | SubmitTaskResult
    | RequestTaskTransfer
    | RespondTaskTransfer
    | DecideTaskTransfer
    | Record<string, unknown>,
  transferId?: string,
) {
  const root = `/api/tasks/${encodeURIComponent(id)}/branches/${encodeURIComponent(branchId)}`;
  const path = transferId
    ? `${root}/transfer-requests/${encodeURIComponent(transferId)}/${action}`
    : `${root}/${action}`;
  return request<TaskDetail>(path, {
    method: 'POST',
    headers: { 'Idempotency-Key': key },
    ...(input ? { body: JSON.stringify(input) } : {}),
  });
}
