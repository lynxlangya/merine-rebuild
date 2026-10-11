import type { DataSourceCheckResult, QueryScopeDetail, ScopeViewMeta } from '@merine/api-contract';

export type ScopeOutcome = NonNullable<DataSourceCheckResult['outcome']>;

/** 探测结论的展示映射；判定始终按服务端返回的 outcome，界面不自行推断。 */
export const CHECK_OUTCOME_META: Record<
  ScopeOutcome,
  { label: string; color: string; hint: string }
> = {
  PASSED: { label: '探测通过', color: 'success', hint: '当时未检测到写权限' },
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
  VIEW_DENIED: { label: '视图未授权', color: 'warning', hint: '视图不存在或缺少 SELECT 授权' },
};

export function outcomeMeta(outcome: ScopeOutcome | null | undefined) {
  return outcome ? CHECK_OUTCOME_META[outcome] : undefined;
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

/** 可勾选视图：来自本次探测或已保存范围；detected 为 null 表示尚未探测。 */
export interface ScopeCandidate {
  name: string;
  saved: boolean;
  detected: boolean | null;
}

/**
 * 合并"本次探测到的可读视图"、"已保存白名单"和"已保存说明"：
 * 说明保留的视图即使暂时移出白名单也显示，便于重新选择；未探测时全部按未知处理。
 */
export function scopeCandidates(
  detail: QueryScopeDetail | undefined,
  inspected: DataSourceCheckResult | null,
): ScopeCandidate[] {
  const saved = new Set(detail?.source?.views ?? []);
  const meta = new Set(
    (detail?.meta ?? [])
      .map((item) => item.view)
      .filter((value): value is string => Boolean(value)),
  );
  // 只有探测成功（或明确判定视图未授权）时才有完整的可读视图清单；
  // 连接失败、超时、授权无法确认都返回空数组，不能据此把已有视图标成“未检测到授权”。
  const confirmed =
    inspected !== null && (inspected.outcome === 'PASSED' || inspected.outcome === 'VIEW_DENIED');
  const detected = confirmed ? new Set(inspected.detectedViews ?? []) : null;
  const names = new Set<string>([...saved, ...meta, ...(inspected?.detectedViews ?? [])]);
  return [...names]
    .sort((left, right) => left.localeCompare(right, 'en'))
    .map((name) => ({
      name,
      saved: saved.has(name),
      detected: detected === null ? null : detected.has(name),
    }));
}

export function viewMetaOf(
  detail: QueryScopeDetail | undefined,
  view: string,
): ScopeViewMeta | undefined {
  return detail?.meta?.find((item) => item.view === view);
}

/** 说明编辑行：字段来自目标库实时元数据与已保存覆盖值的并集。 */
export interface ScopeColumnRow {
  name: string;
  type?: string;
  nullable?: boolean;
  comment?: string;
  override: string;
}

/**
 * 字段行按"目标库顺序优先，其次仅有覆盖值的字段"排列；
 * 未读取目标库时不猜测类型与注释，只展示已保存的覆盖值。
 */
export function columnRows(
  inspected: DataSourceCheckResult | null,
  detail: QueryScopeDetail | undefined,
  view: string,
): ScopeColumnRow[] {
  const dbColumns = inspected?.viewColumns ?? [];
  const metaColumns = viewMetaOf(detail, view)?.columns ?? [];
  const overrides = new Map(metaColumns.map((item) => [item.name ?? '', item.description ?? '']));
  const rows: ScopeColumnRow[] = dbColumns.map((column) => ({
    name: column.name ?? '',
    type: column.type,
    nullable: column.nullable,
    comment: column.comment,
    override: overrides.get(column.name ?? '') ?? '',
  }));
  const seen = new Set(rows.map((row) => row.name));
  for (const item of metaColumns) {
    const name = item.name ?? '';
    if (seen.has(name)) continue;
    rows.push({ name, override: item.description ?? '' });
    seen.add(name);
  }
  return rows;
}

/** 有效说明 = 应用覆盖值 > 目标库列注释 > 未提供；界面据此提示来源。 */
export function effectiveDescription(row: ScopeColumnRow): { text: string; source: string } {
  const override = row.override.trim();
  if (override) return { text: override, source: '应用覆盖' };
  const comment = (row.comment ?? '').trim();
  if (comment) return { text: comment, source: '数据库注释' };
  return { text: '未提供说明', source: '—' };
}
