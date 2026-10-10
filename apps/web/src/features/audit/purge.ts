import type { AuditPurgeResult } from '@merine/api-contract';

/**
 * 清理结果文案：删除不可恢复，所以要把「保留几天、删了多少」说清楚，
 * 没有可删记录时也要给一句明确的话，而不是一个空提示。
 */
export function purgeResultText(result: AuditPurgeResult): string {
  if (result.deleted <= 0) {
    return `没有超过 ${result.retentionDays} 天的记录，未删除任何内容`;
  }
  const range =
    result.earliest && result.latest
      ? `，覆盖 ${formatInstant(result.earliest)} 至 ${formatInstant(result.latest)}`
      : '';
  return `已永久删除 ${result.deleted} 条超过 ${result.retentionDays} 天的记录${range}`;
}

/** 时间用本地时区展示：管理员关心的是「什么时候发生的」，不是 UTC 字面量。 */
function formatInstant(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  const pad = (part: number) => String(part).padStart(2, '0');
  return `${date.getFullYear()}/${pad(date.getMonth() + 1)}/${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}
