const FALLBACK = '/';

/**
 * 登录回跳目标只允许本应用内的绝对路径。
 * 拒绝协议相对地址（//evil.example）、反斜杠变体与带协议的绝对 URL，
 * 避免登录后被带到站外。查询串与 hash 保留，路径本身不做重写。
 */
export function safeRedirectTarget(raw: string | null | undefined): string {
  if (!raw) return FALLBACK;
  if (!raw.startsWith('/')) return FALLBACK;
  if (raw.startsWith('//') || raw.startsWith('/\\')) return FALLBACK;
  // 解析后再确认仍是同源相对路径，挡住 /..//、控制字符等变体
  try {
    const url = new URL(raw, window.location.origin);
    if (url.origin !== window.location.origin) return FALLBACK;
    if (!url.pathname.startsWith('/')) return FALLBACK;
    return `${url.pathname}${url.search}${url.hash}`;
  } catch {
    return FALLBACK;
  }
}

/** 登录后的默认入口。 */
export const defaultEntryPath = FALLBACK;
