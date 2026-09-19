export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly requestId?: string,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

interface ApiEnvelope<T> {
  code: string;
  message: string;
  data: T;
  requestId: string;
}

export async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers);
  headers.set('Accept', 'application/json');
  if (options.body) headers.set('Content-Type', 'application/json');
  const response = await fetch(path, { ...options, headers, credentials: 'same-origin' });
  if (!response.headers.get('content-type')?.includes('application/json')) {
    throw new ApiError('服务暂不可用，请检查 API 状态', response.status);
  }
  const result = (await response.json()) as ApiEnvelope<T>;
  if (!response.ok || result.code !== 'OK') {
    throw new ApiError(result.message || '请求失败', response.status, result.requestId);
  }
  return result.data;
}
