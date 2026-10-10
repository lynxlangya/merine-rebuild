import assert from 'node:assert/strict';
import test from 'node:test';
import { greetingAt, welcomeMessage } from './model.ts';

const at = (hour: number) => new Date(2026, 9, 9, hour, 30);

test('按本机小时选择问候语，边界归入后一个时段', () => {
  assert.equal(greetingAt(at(0)), '晚上好');
  assert.equal(greetingAt(at(4)), '晚上好');
  assert.equal(greetingAt(at(5)), '早上好');
  assert.equal(greetingAt(at(10)), '早上好');
  assert.equal(greetingAt(at(11)), '中午好');
  assert.equal(greetingAt(at(12)), '中午好');
  assert.equal(greetingAt(at(13)), '下午好');
  assert.equal(greetingAt(at(17)), '下午好');
  assert.equal(greetingAt(at(18)), '晚上好');
  assert.equal(greetingAt(at(23)), '晚上好');
});

test('拼上显示名；名称为空白时不留多余逗号', () => {
  assert.equal(welcomeMessage('演示人员甲', at(9)), '早上好，演示人员甲，有什么可以帮你？');
  assert.equal(welcomeMessage('   ', at(9)), '早上好，有什么可以帮你？');
});
