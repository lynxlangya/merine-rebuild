import type { DictionaryItemView, DictionaryView } from '@merine/api-contract';

/**
 * 代码里用到的字典编码。这里只是编码常量，标签与顺序一律来自接口，
 * 前端不写死文案——枚举字典也能在线改标签，改完刷新即可生效。
 */
export const DICTIONARY_CODES = {
  status: 'common.status',
  menuType: 'system.menu.type',
  unitLevel: 'system.unit.level',
} as const;

export type DictTone = 'neutral' | 'warning';

/** 选择项：只列启用项，按排序升序；停用项只在标签解析里用得到。 */
export function toDictOptions(
  dictionary: DictionaryView | undefined,
): { value: string; label: string }[] {
  if (!isDictionaryUsable(dictionary)) {
    return [];
  }
  return orderedItems(dictionary)
    .filter((item) => item.status === 'ENABLED')
    .map((item) => ({ value: item.value, label: item.label }));
}

/**
 * 值 → 标签：包含停用项，因此历史数据（引用了已停用取值）仍能显示业务标签；
 * 字典里找不到时回退原始值，绝不显示成空白。
 */
export function dictLabel(dictionary: DictionaryView | undefined, value: string): string {
  if (!dictionary) return value;
  return orderedItems(dictionary).find((item) => item.value === value)?.label ?? value;
}

/** 标签色调：停用类取值用警示色，其余中性；颜色属于展示，不写进字典数据。 */
export function dictTone(value: string): DictTone {
  return value === 'DISABLED' ? 'warning' : 'neutral';
}

/**
 * 字典是否可用作选项来源：类型停用或还没加载出来时都不算。
 * 写成类型谓词，调用方在 `if (!isDictionaryUsable(dict))` 之后能直接收窄成已加载。
 */
export function isDictionaryUsable(
  dictionary: DictionaryView | undefined,
): dictionary is DictionaryView {
  return dictionary?.status === 'ENABLED';
}

/** 字典项按排序、再按取值排序，保证同一份数据在哪里渲染顺序都一致。 */
export function orderedItems(dictionary: DictionaryView): DictionaryItemView[] {
  return [...dictionary.items].sort(
    (left, right) => left.sortOrder - right.sortOrder || left.value.localeCompare(right.value),
  );
}
