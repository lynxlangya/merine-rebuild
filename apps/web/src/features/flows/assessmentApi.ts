import type {
  Assessment,
  AssessmentContext,
  PageResultAssessment,
  RecordAssessment,
} from '@merine/api-contract';
import { request } from '../../shared/http';
export const assessmentKeys = {
  root: (userId?: string) => ['assessments', userId] as const,
  list: (userId: string | undefined, id: string, page = 1) =>
    ['assessments', userId, id, 'list', page] as const,
  context: (userId: string | undefined, id: string) =>
    ['assessments', userId, id, 'context'] as const,
};
const url = (id: string) => `/api/intelligence-topics/${encodeURIComponent(id)}`;
export const fetchAssessments = (id: string, page = 1, signal?: AbortSignal) =>
  request<PageResultAssessment>(`${url(id)}/assessments?page=${page}&pageSize=20`, { signal });
export const fetchAssessmentContext = (id: string, signal?: AbortSignal) =>
  request<AssessmentContext>(`${url(id)}/assessment-context`, { signal });
export const recordAssessment = (id: string, input: RecordAssessment, key: string) =>
  request<Assessment>(`${url(id)}/assessments`, {
    method: 'POST',
    headers: { 'Idempotency-Key': key },
    body: JSON.stringify(input),
  });
