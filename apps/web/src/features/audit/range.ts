/**
 * 审计时间范围的起点：**秒与毫秒归零**。
 *
 * 为什么单独拿出来：查询键里放进随时间变化的值（`Date.now()` 直出的毫秒时间戳），
 * 每次渲染都会算出一个"新的"键，TanStack Query 据此判定需要重新取数 → 渲染 → 又变 →
 * 无限刷新（审计页实测踩过，网络面板里请求一秒几十条）。
 * 时间边界按分钟取整、并用 `useMemo` 固定，键就稳定了。
 */
export function rangeFromDays(days: number, now = new Date()): string {
  const boundary = new Date(now.getTime());
  boundary.setSeconds(0, 0);
  return new Date(boundary.getTime() - days * 24 * 60 * 60 * 1000).toISOString();
}
