import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import type { DictionaryItemView, DictionaryView } from '@merine/api-contract';
import { dictLabel, dictTone, isDictionaryUsable, orderedItems, toDictOptions } from './model.ts';

function item(partial: Partial<DictionaryItemView> & Pick<DictionaryItemView, 'value' | 'label'>) {
  return {
    description: null,
    sortOrder: 0,
    status: 'ENABLED',
    ...partial,
  } as DictionaryItemView;
}

function dictionary(partial: Partial<DictionaryView> = {}): DictionaryView {
  return {
    code: 'vessel.type',
    name: '船舶类型',
    description: null,
    status: 'ENABLED',
    items: [],
    ...partial,
  } as DictionaryView;
}

describe('字典选项与标签', () => {
  const status = dictionary({
    code: 'common.status',
    items: [
      item({ value: 'DISABLED', label: '停用', sortOrder: 2 }),
      item({ value: 'ENABLED', label: '启用', sortOrder: 1 }),
    ],
  });

  it('选项按排序升序，且不列停用项', () => {
    const mixed = dictionary({
      items: [
        item({ value: 'CARGO', label: '货船', sortOrder: 2 }),
        item({ value: 'RETIRED', label: '已淘汰', sortOrder: 1, status: 'DISABLED' }),
        item({ value: 'FISHING', label: '渔船', sortOrder: 1 }),
      ],
    });
    assert.deepEqual(toDictOptions(mixed), [
      { value: 'FISHING', label: '渔船' },
      { value: 'CARGO', label: '货船' },
    ]);
  });

  it('排序相同的项按取值稳定排序', () => {
    const tied = dictionary({
      items: [item({ value: 'B', label: '乙' }), item({ value: 'A', label: '甲' })],
    });
    assert.deepEqual(
      orderedItems(tied).map((entry) => entry.value),
      ['A', 'B'],
    );
  });

  it('标签解析包含停用项，未知取值回退原始值', () => {
    const disabled = dictionary({
      items: [item({ value: 'RETIRED', label: '已淘汰', status: 'DISABLED' })],
    });
    assert.equal(dictLabel(disabled, 'RETIRED'), '已淘汰');
    assert.equal(dictLabel(disabled, 'UNKNOWN'), 'UNKNOWN');
    assert.equal(dictLabel(undefined, 'ENABLED'), 'ENABLED');
  });

  it('类型停用或未加载时不给选项，停用项仍能解析标签', () => {
    const disabledType = dictionary({
      status: 'DISABLED',
      items: [item({ value: 'ENABLED', label: '启用' })],
    });
    assert.deepEqual(toDictOptions(disabledType), []);
    assert.equal(isDictionaryUsable(disabledType), false);
    assert.equal(isDictionaryUsable(status), true);
    assert.equal(dictLabel(disabledType, 'ENABLED'), '启用');
  });

  it('色调按取值映射：停用类取值用警示色', () => {
    assert.equal(dictTone('DISABLED'), 'warning');
    assert.equal(dictTone('ENABLED'), 'neutral');
    assert.equal(dictTone('PAGE'), 'neutral');
  });
});
