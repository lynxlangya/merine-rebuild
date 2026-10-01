import type {
  CreateIntelligenceTask,
  IntelligenceTaskContext,
  PageResultLinkedIntelligenceTask,
  TaskDetail,
  TaskIntelligenceSource,
} from '@merine/api-contract';
import { request } from '../../shared/http';
export const taskIntelKeys = { root: (userId?: string) => ['task-intelligence', userId] as const };
const topic = (id: string) => `/api/intelligence-topics/${encodeURIComponent(id)}`;
const task = (id: string) => `/api/tasks/${encodeURIComponent(id)}`;
export const fetchIntelligenceTaskContext = (id: string, signal?: AbortSignal) =>
  request<IntelligenceTaskContext>(`${topic(id)}/task-context`, { signal });
export const createIntelligenceTask = (id: string, input: CreateIntelligenceTask, key: string) =>
  request<TaskDetail>(`${topic(id)}/tasks`, {
    method: 'POST',
    headers: { 'Idempotency-Key': key },
    body: JSON.stringify(input),
  });
export const fetchLinkedTasks = (id: string, page = 1, signal?: AbortSignal) =>
  request<PageResultLinkedIntelligenceTask>(`${topic(id)}/tasks?page=${page}&pageSize=20`, {
    signal,
  });
export const fetchTaskIntelligenceSource = (id: string, signal?: AbortSignal) =>
  request<TaskIntelligenceSource>(`${task(id)}/intelligence-source`, { signal });
