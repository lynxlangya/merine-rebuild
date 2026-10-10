import type { ChatRunStats } from '@merine/api-contract';

type DailyPoint = ChatRunStats['series'][number];

/** 耗时：小于 1 秒显示毫秒，其余保留一位小数的秒。 */
export function formatDuration(ms: number): string {
  return ms < 1000 ? `${ms} ms` : `${(ms / 1000).toFixed(1)} s`;
}

export const RUN_STATE_LABELS: Record<string, string> = {
  RUNNING: '执行中',
  SUCCEEDED: '成功',
  AWAITING_INPUT: '等待作答',
  FAILED: '失败',
  ABORTED: '已停止',
};

export function stateLabel(state: string): string {
  return RUN_STATE_LABELS[state] ?? state;
}

export type UsageMetric = 'tokens' | 'runs' | 'chars' | 'duration';

/** 主图可切换的口径；KPI 卡不随它变化，始终展示全部口径。 */
export const USAGE_METRICS: { value: UsageMetric; label: string }[] = [
  { value: 'tokens', label: 'Token' },
  { value: 'runs', label: '次数' },
  { value: 'chars', label: '字符' },
  { value: 'duration', label: '时长' },
];

/** 客户端本地偏移（分钟，东八区为 +480）：服务端按它把 UTC 时间换算成本地日期分桶。 */
export function localOffsetMinutes(date = new Date()): number {
  return -date.getTimezoneOffset();
}

export function localDayKey(date = new Date()): string {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

export function metricValue(
  point: Pick<
    DailyPoint,
    'runs' | 'promptTokens' | 'completionTokens' | 'inputChars' | 'outputChars' | 'durationMs'
  >,
  metric: UsageMetric,
): number {
  switch (metric) {
    case 'runs':
      return point.runs;
    case 'chars':
      return point.inputChars + point.outputChars;
    case 'duration':
      return point.durationMs;
    default:
      return point.promptTokens + point.completionTokens;
  }
}

/**
 * 把服务端返回的分桶补齐成连续的天（含今天），缺的天补零。
 * 服务端只返回有记录的天，直接画图会把「没有使用」和「漏数据」混在一起。
 */
export function fillDays(series: DailyPoint[], days: number, now = new Date()): DailyPoint[] {
  const byDate = new Map(series.map((point) => [point.date, point]));
  const filled: DailyPoint[] = [];
  for (let offset = days - 1; offset >= 0; offset -= 1) {
    const date = new Date(now.getFullYear(), now.getMonth(), now.getDate() - offset);
    const key = localDayKey(date);
    filled.push(
      byDate.get(key) ?? {
        date: key,
        runs: 0,
        failed: 0,
        promptTokens: 0,
        completionTokens: 0,
        inputChars: 0,
        outputChars: 0,
        durationMs: 0,
      },
    );
  }
  return filled;
}

/** 成功率：没有记录时返回 null，界面显示「—」而不是 0%。 */
export function successRate(totals: { runs: number; succeeded: number }): number | null {
  if (totals.runs === 0) return null;
  return Math.round((totals.succeeded / totals.runs) * 1000) / 10;
}

/** 平均耗时：没有记录时返回 null。 */
export function averageDuration(totals: { runs: number; durationMs: number }): number | null {
  if (totals.runs === 0) return null;
  return Math.round(totals.durationMs / totals.runs);
}

/** 压缩成大数字：1.2k / 3.4M，用于指标卡与图表轴。 */
export function formatCompact(value: number | null | undefined): string {
  if (value == null) return '—';
  if (value < 1000) return String(value);
  if (value < 1_000_000) return `${(value / 1000).toFixed(1)}k`;
  return `${(value / 1_000_000).toFixed(1)}M`;
}

/** 日期轴标签：只保留 月-日，避免轴上的重复信息。 */
export function shortDate(date: string): string {
  const [, month, day] = date.split('-');
  return month && day ? `${month}-${day}` : date;
}

export interface TrendSummary {
  /** 区间总值的日均（按区间天数，不按活跃天数）。 */
  averagePerDay: number;
  /** 有记录的天数。 */
  activeDays: number;
  /** 峰值日与当天取值；没有记录时为 null。 */
  peak: { date: string; value: number } | null;
}

/** 趋势卡的区间小结：补零后的序列参与日均，峰值只取有记录的天。 */
export function trendSummary(points: DailyPoint[], metric: UsageMetric): TrendSummary {
  const values = points.map((point) => metricValue(point, metric));
  const total = values.reduce((sum, value) => sum + value, 0);
  let peak: TrendSummary['peak'] = null;
  points.forEach((point, index) => {
    if (point.runs === 0) return;
    if (!peak || values[index] >= peak.value) peak = { date: point.date, value: values[index] };
  });
  return {
    averagePerDay: points.length === 0 ? 0 : Math.round(total / points.length),
    activeDays: points.filter((point) => point.runs > 0).length,
    peak,
  };
}

/** 总耗时、日均与图表轴统一带时间单位，避免把毫秒误看成次数。 */
export function formatUsageValue(value: number, metric: UsageMetric): string {
  if (metric !== 'duration') return formatCompact(value);
  if (value < 60_000) return formatDuration(value);
  if (value < 3_600_000) return `${Math.round(value / 6000) / 10} min`;
  return `${Math.round(value / 360_000) / 10} h`;
}

/** 坐标上界取 1/2/5，零值也保留有效刻度。 */
export function usageAxisMax(value: number): number {
  if (value <= 0) return 1;
  const magnitude = 10 ** Math.floor(Math.log10(value));
  return [1, 2, 5, 10].find((step) => step * magnitude >= value)! * magnitude;
}

export function modelMetricValue(
  model: ChatRunStats['models'][number],
  metric: UsageMetric,
): number {
  if (metric === 'chars' || metric === 'runs') return model.runs;
  if (metric === 'duration') return model.durationMs;
  return model.promptTokens + model.completionTokens;
}

/** 聚合按执行次数返回，切换口径需重新排序；不修改查询缓存。 */
export function rankUsageModels(
  models: ChatRunStats['models'],
  metric: UsageMetric,
): ChatRunStats['models'] {
  return [...models].sort((a, b) => modelMetricValue(b, metric) - modelMetricValue(a, metric));
}
