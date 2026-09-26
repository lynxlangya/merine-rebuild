/** 任务期限按业务约定的 UTC+8 输入和显示，不随设备时区改变。 */
const taskTimeFormatter = new Intl.DateTimeFormat('zh-CN', {
  timeZone: 'Asia/Shanghai',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hourCycle: 'h23',
});

export interface TaskWallTime {
  year(): number;
  month(): number;
  date(): number;
  hour(): number;
  minute(): number;
  second(): number;
}

export function formatTaskTime(value?: string): string {
  return value ? taskTimeFormatter.format(new Date(value)) : '—';
}

export function taskWallTimeToUtc(value: TaskWallTime): string {
  return new Date(
    Date.UTC(
      value.year(),
      value.month(),
      value.date(),
      value.hour() - 8,
      value.minute(),
      value.second(),
    ),
  ).toISOString();
}
