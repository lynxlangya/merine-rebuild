import assert from 'node:assert/strict';
import { test } from 'node:test';
import { CHECK_OUTCOME_META, formatCheckedAt, mergeViewChoices } from './model.ts';

test('mergeViewChoices 合并检测结果与已勾选项并排序去重', () => {
  assert.deepEqual(mergeViewChoices(['v_beta', 'v_alpha'], ['v_alpha', 'v_gamma']), [
    { name: 'v_alpha', detected: true },
    { name: 'v_beta', detected: true },
    { name: 'v_gamma', detected: false },
  ]);
});

test('mergeViewChoices 在尚未检查时按未知处理', () => {
  assert.deepEqual(mergeViewChoices(null, ['v_beta', 'v_alpha']), [
    { name: 'v_alpha', detected: true },
    { name: 'v_beta', detected: true },
  ]);
});

test('mergeViewChoices 空检测结果只保留已勾选并标记未检测', () => {
  assert.deepEqual(mergeViewChoices([], ['v_alpha']), [{ name: 'v_alpha', detected: false }]);
});

test('检查结论映射覆盖服务端全部取值', () => {
  assert.deepEqual(Object.keys(CHECK_OUTCOME_META).sort(), [
    'AUTHORIZATION_UNCONFIRMED',
    'CONNECTION_FAILED',
    'PASSED',
    'VIEW_DENIED',
    'WRITE_PRIVILEGE',
  ]);
});

test('检查时间按 UTC+8 展示，不跟随浏览器时区', () => {
  const text = formatCheckedAt('2026-10-10T16:05:00Z');
  assert.ok(text.includes('2026'));
  assert.ok(text.includes('10'));
  assert.ok(text.includes('11'));
  assert.ok(text.includes('00:05'));
  assert.equal(formatCheckedAt(null), '');
});
