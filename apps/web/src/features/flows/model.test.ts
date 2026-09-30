import assert from 'node:assert/strict';
import { test } from 'node:test';
import type { IntelligenceReceipt, IntelligenceUnitOption } from '@merine/api-contract';
import { subtreeCodes, receiptPath, canToggleScopeUnit, validDraftTargets } from './model.ts';

test('停用单位不允许新选，但草稿原有选择可以取消', () => {
  const disabled: IntelligenceUnitOption = {
    code: 'a',
    name: '甲',
    parentCode: null,
    level: 2,
    enabled: false,
    targetEligible: false,
  };
  assert.equal(canToggleScopeUnit(disabled, []), false);
  assert.equal(canToggleScopeUnit(disabled, ['a']), true);
});
test('范围缩小或单位停用后，首次目标只保留合法选择', () => {
  const options: IntelligenceUnitOption[] = [
    { code: 'a', name: '甲', parentCode: null, level: 2, enabled: true, targetEligible: true },
    { code: 'b', name: '乙', parentCode: null, level: 2, enabled: true, targetEligible: true },
    { code: 'c', name: '丙', parentCode: null, level: 2, enabled: false, targetEligible: false },
  ];
  assert.deepEqual(validDraftTargets(['a', 'b', 'c'], ['b', 'c'], options), ['b']);
  assert.deepEqual(validDraftTargets(['a'], ['b'], options), []);
});

test('显式选择子树包含中转单位，选择叶子不自动添加上级', () => {
  const options: IntelligenceUnitOption[] = [
    { code: 'hq', name: '总队', parentCode: null, level: 1, enabled: true, targetEligible: false },
    { code: 'a', name: '甲', parentCode: 'hq', level: 2, enabled: true, targetEligible: true },
    { code: 'aa', name: '甲一', parentCode: 'a', level: 3, enabled: true, targetEligible: false },
    { code: 'ab', name: '甲二', parentCode: 'a', level: 3, enabled: false, targetEligible: false },
  ];
  assert.deepEqual(subtreeCodes(options, 'a'), ['a', 'aa']);
  assert.deepEqual(subtreeCodes(options, 'aa'), ['aa']);
});
test('重复经过同一单位仍显示这份回执的实际路径', () => {
  const receipt = (
    id: string,
    parentReceiptId: string | null,
    fromUnitName: string,
    toUnitName: string,
  ): IntelligenceReceipt => ({
    id,
    sendId: id,
    parentReceiptId,
    fromUnitName,
    toUnitName,
    senderName: '演示人员',
    note: '',
    sentAt: '2026-09-30T00:00:00Z',
    mine: false,
    signedByName: null,
    signedAt: null,
    feedbacks: [],
    allowedActions: [],
  });
  const receipts = [
    receipt('1', null, '总队', '甲'),
    receipt('2', '1', '甲', '乙'),
    receipt('3', '2', '乙', '甲'),
  ];
  assert.equal(receiptPath(receipts[2]!, receipts), '总队 → 甲 → 乙 → 甲');
});
