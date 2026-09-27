/** 任务期限按业务约定的 UTC+8 输入和显示，不随设备时区改变；精确到分钟。 */
const taskTimeFormatter = new Intl.DateTimeFormat('zh-CN', {
  timeZone: 'Asia/Shanghai',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});

export interface TaskWallTime {
  year(): number;
  month(): number;
  date(): number;
  hour(): number;
  minute(): number;
}

/** 显示为 `YYYY-MM-DD HH:mm`；按部件拼接，不依赖区域格式里的分隔符。 */
export function formatTaskTime(value?: string): string {
  if (!value) return '—';
  const parts = Object.fromEntries(
    taskTimeFormatter.formatToParts(new Date(value)).map((part) => [part.type, part.value]),
  );
  return `${parts.year}-${parts.month}-${parts.day} ${parts.hour}:${parts.minute}`;
}

/** 选择器只到分钟，秒固定为 0，避免期限带着打开选择器那一刻的秒数。 */
export function taskWallTimeToUtc(value: TaskWallTime): string {
  return new Date(
    Date.UTC(value.year(), value.month(), value.date(), value.hour() - 8, value.minute()),
  ).toISOString();
}
