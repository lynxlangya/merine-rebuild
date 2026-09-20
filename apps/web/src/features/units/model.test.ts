import assert from 'node:assert/strict';
import { test } from 'node:test';
import type { UnitSummary, UnitTreeNode } from '@merine/api-contract';
import {
  buildParentSelectTree,
  buildUnitSelectTree,
  collectUnitTreeKeys,
  defaultUnitTreeExpandedKeys,
  filterUnitTree,
  flattenUnitTree,
  findUnitTreeNode,
  unitLevelLabel,
} from './model.ts';

function summary(
  code: string,
  name: string,
  parentCode: string | null,
  level: number,
  status = 'ENABLED',
): UnitSummary {
  return { code, name, parentCode, level, status };
}

function node(
  code: string,
  name: string,
  parentCode: string | null,
  level: number,
  children: UnitTreeNode[] = [],
): UnitTreeNode {
  return {
    code,
    name,
    parentCode,
    level,
    status: 'ENABLED',
    areaCode: code,
    childCount: children.length,
    userCount: 0,
    version: 0,
    updatedAt: '2026-09-20T00:00:00Z',
    children,
  };
}

test('扁平单位选项按 parentCode 还原层级，当前停用单位仍可选择', () => {
  const units = [
    summary('ROOT', '总队', null, 1),
    summary('A', '甲支队', 'ROOT', 2),
    summary('A1', '甲大队', 'A', 3, 'DISABLED'),
  ];

  const tree = buildUnitSelectTree(units, 'A1');
  assert.equal(tree.length, 1);
  assert.equal(tree[0].value, 'ROOT');
  assert.equal(tree[0].children?.[0].value, 'A');
  assert.equal(tree[0].children?.[0].children?.[0].disabled, false);
  assert.equal(tree[0].children?.[0].children?.[0].selectable, true);
});

test('单位选择器把停用单位标为不可选，除非它是当前值', () => {
  const units = [summary('ROOT', '总队', null, 1), summary('A', '甲支队', 'ROOT', 2, 'DISABLED')];
  const tree = buildUnitSelectTree(units);
  assert.equal(tree[0].children?.[0].disabled, true);
  assert.equal(tree[0].children?.[0].selectable, false);
});

test('上级候选排除自身和后代，三级单位只能显示不能作为上级', () => {
  const root = node('ROOT', '总队', null, 1, [
    node('A', '甲支队', 'ROOT', 2, [node('A1', '甲大队', 'A', 3)]),
    node('B', '乙支队', 'ROOT', 2),
  ]);

  const candidates = buildParentSelectTree([root], 'A');
  assert.equal(candidates[0].value, 'ROOT');
  assert.equal(candidates[0].children?.length, 1);
  assert.equal(candidates[0].children?.[0].value, 'B');

  const withLevelThree = buildParentSelectTree([root]);
  assert.equal(withLevelThree[0].children?.[0].disabled, false);
  assert.equal(withLevelThree[0].children?.[0].children?.[0].disabled, true);
  assert.equal(withLevelThree[0].children?.[0].children?.[0].selectable, false);
});

test('搜索只保留命中节点和通向命中后代的路径', () => {
  const root = node('ROOT', '总队', null, 1, [
    node('A', '甲支队', 'ROOT', 2, [node('A1', '港湾大队', 'A', 3)]),
    node('B', '乙支队', 'ROOT', 2),
  ]);

  const filtered = filterUnitTree([root], '港湾');
  assert.equal(filtered.length, 1);
  assert.equal(filtered[0].children.length, 1);
  assert.equal(filtered[0].children[0].children[0].code, 'A1');
  assert.equal(findUnitTreeNode(filtered, 'B'), null);
  assert.equal(collectUnitTreeKeys(filtered).join(','), 'ROOT,A,A1');
});

test('层级文案使用总队、支队、大队', () => {
  assert.equal(unitLevelLabel(1), '总队');
  assert.equal(unitLevelLabel(2), '支队');
  assert.equal(unitLevelLabel(3), '大队');
});

test('默认展开总队和最后一个支队，并保持树的展开顺序', () => {
  const root = node('ROOT', '总队', null, 1, [
    node('A', '甲支队', 'ROOT', 2, [node('A1', '甲大队', 'A', 3)]),
    node('B', '乙支队', 'ROOT', 2),
  ]);

  assert.equal(
    flattenUnitTree([root])
      .map((item) => item.code)
      .join(','),
    'ROOT,A,A1,B',
  );
  assert.deepEqual(defaultUnitTreeExpandedKeys([root]), ['ROOT', 'B']);
});
