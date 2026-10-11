import type { DataSourceCheckResult } from '@merine/api-contract';

export type DataSourceOutcome = NonNullable<DataSourceCheckResult['outcome']>;

/** 检查结论的展示映射：颜色与说明只影响呈现，判定始终按服务端返回的 outcome。 */
export const CHECK_OUTCOME_META: Record<
  DataSourceOutcome,
  { label: string; color: string; hint: string }
> = {
  PASSED: { label: '检查通过', color: 'success', hint: '当时未检测到写权限' },
  CONNECTION_FAILED: {
    label: '连接失败',
    color: 'error',
    hint: '目标不可达、超时或账号密码不正确',
  },
  WRITE_PRIVILEGE: { label: '存在写权限', color: 'error', hint: '只读账号不得有写权限或转授权限' },
  AUTHORIZATION_UNCONFIRMED: {
    label: '授权无法确认',
    color: 'warning',
    hint: '授权信息不完整，不能作为启用依据',
  },
  VIEW_DENIED: { label: '视图未授权', color: 'warning', hint: '勾选视图缺少 SELECT 授权' },
};

export function outcomeMeta(outcome: DataSourceOutcome | null | undefined) {
  return outcome ? CHECK_OUTCOME_META[outcome] : undefined;
}

export interface ViewChoice {
  name: string;
  /** false 表示已勾选但最近一次检查没有检测到该视图的 SELECT 授权。 */
  detected: boolean;
}

/**
 * 合并“服务端检测到的可读视图”和“已勾选视图”：
 * 检查前（detected 为 null）全部按未知处理，检查后未命中的已勾选项标出来，
 * 让管理员能看到历史勾选已经不在授权范围内。
 */
export function mergeViewChoices(
  detected: readonly string[] | null,
  selected: readonly string[],
): ViewChoice[] {
  const detectedNames = new Set(detected ?? []);
  const names = new Set<string>([...(detected ?? []), ...selected]);
  return [...names]
    .sort((a, b) => a.localeCompare(b, 'en'))
    .map((name) => ({ name, detected: detected === null || detectedNames.has(name) }));
}

const dateTimeFormat = new Intl.DateTimeFormat('zh-CN', {
  timeZone: 'Asia/Shanghai',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});

/** 服务端返回 ISO UTC；界面按明确的 UTC+8 展示，不跟随浏览器时区。 */
export function formatCheckedAt(value: string | null | undefined): string {
  if (!value) return '';
  const time = new Date(value);
  return Number.isNaN(time.getTime()) ? '' : dateTimeFormat.format(time);
}
