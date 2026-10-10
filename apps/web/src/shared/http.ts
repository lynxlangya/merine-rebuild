/**
 * 统一请求入口：Cookie 会话、CSRF、统一错误体与 401 去重都在这里处理。
 * 页面与 feature 只调用 request()，不直接使用 fetch。
 */

export interface FieldError {
  field: string;
  message: string;
}

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly code: string = 'UNKNOWN',
    readonly requestId?: string,
    readonly fieldErrors: FieldError[] = [],
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
  fieldErrors?: FieldError[];
}

/** CookieCsrfTokenRepository 的默认 Cookie 与头名。 */
const CSRF_COOKIE = 'XSRF-TOKEN';
const CSRF_HEADER = 'X-XSRF-TOKEN';
const UNSAFE_METHODS = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

function readCookie(name: string): string | null {
  const prefix = `${name}=`;
  for (const part of document.cookie.split('; ')) {
    if (part.startsWith(prefix)) return decodeURIComponent(part.slice(prefix.length));
  }
  return null;
}

let pendingCsrf: Promise<void> | null = null;

/**
 * 确保 CSRF Cookie 存在。并发调用共享同一次请求，避免重复取令牌。
 * 登录前也需要 Cookies，因此这里不依赖任何身份状态。
 */
export function ensureCsrfToken(): Promise<void> {
  if (readCookie(CSRF_COOKIE)) return Promise.resolve();
  pendingCsrf ??= fetch('/api/auth/csrf', { credentials: 'same-origin' })
    .then((response) => {
      if (!response.ok) throw new ApiError('无法初始化安全令牌，请刷新页面重试', response.status);
    })
    .finally(() => {
      pendingCsrf = null;
    });
  return pendingCsrf;
}

type UnauthorizedListener = () => void;
const unauthorizedListeners = new Set<UnauthorizedListener>();

/** 会话失效订阅。订阅方负责去重，避免并发请求触发重复跳转与提示。 */
export function onUnauthorized(listener: UnauthorizedListener): () => void {
  unauthorizedListeners.add(listener);
  return () => unauthorizedListeners.delete(listener);
}

export function isAbortError(error: unknown): boolean {
  return error instanceof DOMException && error.name === 'AbortError';
}

export async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const response = await sendRequest(path, options);
  return readEnvelope<T>(response);
}

/**
 * 流式请求：与 request() 相同的 Cookie、CSRF、401 与网络错误约定，但不等待响应结束。
 * 调用方负责检查状态与 Content-Type、读取 body，并在取消时 abort。
 */
export async function requestStream(path: string, options: RequestInit = {}): Promise<Response> {
  return sendRequest(path, options);
}

/** 把 ApiResponse 封套转为数据或带请求编号的 ApiError；流式响应的调用方按需使用。 */
export async function readEnvelope<T>(response: Response): Promise<T> {
  if (!response.headers.get('content-type')?.includes('application/json')) {
    throw new ApiError('服务暂不可用，请稍后重试', response.status, 'UNEXPECTED_RESPONSE');
  }
  const result = (await response.json()) as ApiEnvelope<T>;
  if (!response.ok || result.code !== 'OK') {
    throw new ApiError(
      result.message || '请求失败',
      response.status,
      result.code || 'UNKNOWN',
      result.requestId,
      result.fieldErrors ?? [],
    );
  }
  return result.data;
}

async function sendRequest(path: string, options: RequestInit): Promise<Response> {
  const method = (options.method ?? 'GET').toUpperCase();
  const headers = new Headers(options.headers);
  if (!headers.has('Accept')) headers.set('Accept', 'application/json');
  if (options.body) headers.set('Content-Type', 'application/json');
  if (UNSAFE_METHODS.has(method)) {
    await ensureCsrfToken();
    const token = readCookie(CSRF_COOKIE);
    if (token) headers.set(CSRF_HEADER, token);
  }

  let response: Response;
  try {
    response = await fetch(path, { ...options, method, headers, credentials: 'same-origin' });
  } catch (error) {
    if (isAbortError(error)) throw error;
    throw new ApiError('无法连接服务，请检查网络或稍后重试', 0, 'NETWORK_ERROR');
  }

  if (response.status === 401) {
    for (const listener of unauthorizedListeners) listener();
  }
  return response;
}
