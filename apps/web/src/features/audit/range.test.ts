import assert from 'node:assert/strict';
import test from 'node:test';
import { rangeFromDays } from './range.ts';

test('range start is stable within the same minute', () => {
  const now = new Date('2026-10-10T12:34:56.789Z');
  const later = new Date('2026-10-10T12:34:59.999Z');
  assert.equal(rangeFromDays(7, now), rangeFromDays(7, later));
  assert.equal(rangeFromDays(7, now), '2026-10-03T12:34:00.000Z');
});

test('range start moves with the selected window and crosses minutes', () => {
  const now = new Date('2026-10-10T12:34:56.789Z');
  assert.equal(rangeFromDays(1, now), '2026-10-09T12:34:00.000Z');
  assert.notEqual(rangeFromDays(7, now), rangeFromDays(30, now));
  // 跨分钟必然不同：缓存键随时间推进而失效是预期行为，但不能在渲染之间抖动
  assert.notEqual(rangeFromDays(7, now), rangeFromDays(7, new Date('2026-10-10T12:35:01.000Z')));
});
