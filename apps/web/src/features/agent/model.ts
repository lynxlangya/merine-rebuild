/**
 * 首页问候语：按本机时间选时段，再拼上当前登录用户的显示名。
 * 时段边界集中在这里，调整只用改 greetingAt；传入 Date 便于测试固定时间。
 */
export function greetingAt(date: Date): string {
  const hour = date.getHours();
  if (hour >= 5 && hour < 11) return '早上好';
  if (hour >= 11 && hour < 13) return '中午好';
  if (hour >= 13 && hour < 18) return '下午好';
  return '晚上好';
}

export function welcomeMessage(displayName: string, date: Date): string {
  const name = displayName.trim();
  return name
    ? `${greetingAt(date)}，${name}，有什么可以帮你？`
    : `${greetingAt(date)}，有什么可以帮你？`;
}
