import type { IntelligenceReceipt, IntelligenceUnitOption } from '@merine/api-contract';

export function canToggleScopeUnit(unit: IntelligenceUnitOption, selected: string[]) {
  return unit.enabled || selected.includes(unit.code);
}
export function validDraftTargets(
  targets: string[],
  scope: string[],
  options: IntelligenceUnitOption[],
) {
  const allowed = new Set(
    options
      .filter((u) => u.enabled && u.targetEligible && scope.includes(u.code))
      .map((u) => u.code),
  );
  return targets.filter((code) => allowed.has(code));
}

/** 选中子树是显式操作；勾选某个叶子不自动增加其上级传播授权。 */
export function subtreeCodes(options: IntelligenceUnitOption[], root: string): string[] {
  const result: string[] = [];
  const visit = (code: string) => {
    const unit = options.find((u) => u.code === code);
    if (!unit) return;
    if (unit.enabled) result.push(code);
    for (const child of options.filter((u) => u.parentCode === code)) visit(child.code);
  };
  visit(root);
  return result;
}
export function receiptPath(receipt: IntelligenceReceipt, receipts: IntelligenceReceipt[]): string {
  const nodes = [receipt.toUnitName];
  let current: IntelligenceReceipt | undefined = receipt;
  const seen = new Set<string>();
  while (current && !seen.has(current.id)) {
    seen.add(current.id);
    if (!current.parentReceiptId) {
      nodes.unshift(current.fromUnitName);
      break;
    }
    current = receipts.find((r) => r.id === current?.parentReceiptId);
    if (current) nodes.unshift(current.toUnitName);
  }
  return nodes.join(' → ');
}
const flowTimeFormatter = new Intl.DateTimeFormat('sv-SE', {
  timeZone: 'Asia/Shanghai',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
});
export function formatFlowTime(value?: string | null) {
  return value ? flowTimeFormatter.format(new Date(value)) : '—';
}
