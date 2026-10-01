import { ApiError } from './http.ts';

export interface SubmissionIntent<T> {
  key: string;
  input: T;
  fingerprint: string;
  uncertain: boolean;
}
export function submissionIntent<T>(
  previous: SubmissionIntent<T> | null,
  input: T,
): SubmissionIntent<T> {
  const fingerprint = JSON.stringify(input);
  if (previous?.fingerprint === fingerprint) return previous;
  if (previous?.uncertain) throw new Error('请先重试并确认上次提交结果');
  return { key: crypto.randomUUID(), input, fingerprint, uncertain: false };
}
export function submissionUncertain(error: unknown, previouslyUncertain = false) {
  // 关联创建先重放幂等命令，再检查源头；此冲突明确证明原请求未落单。
  if (error instanceof ApiError && error.status === 409 && error.code === 'SOURCE_CHANGED')
    return false;
  return (
    previouslyUncertain ||
    !(error instanceof ApiError) ||
    error.status === 0 ||
    error.status >= 500 ||
    (error.status >= 200 && error.status < 300) ||
    error.code === 'IDEMPOTENCY_PENDING'
  );
}
