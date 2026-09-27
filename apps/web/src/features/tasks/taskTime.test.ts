import assert from 'node:assert/strict';
import test from 'node:test';
import { formatTaskTime, taskWallTimeToUtc } from './taskTime.ts';

test('任务时间始终按 UTC+8 显示到分钟', () => {
  assert.equal(formatTaskTime('2026-09-30T10:00:00Z'), '2026-09-30 18:00');
  assert.equal(formatTaskTime('2026-09-26T12:00:00.400Z'), '2026-09-26 20:00');
  assert.equal(formatTaskTime('2026-09-30T16:05:59.999Z'), '2026-10-01 00:05');
  assert.equal(formatTaskTime(), '—');
});

test('期限按 UTC+8 墙上时间提交，秒固定为 0', () => {
  assert.equal(
    taskWallTimeToUtc({
      year: () => 2026,
      month: () => 8,
      date: () => 30,
      hour: () => 18,
      minute: () => 0,
    }),
    '2026-09-30T10:00:00.000Z',
  );
  assert.equal(
    taskWallTimeToUtc({
      year: () => 2026,
      month: () => 9,
      date: () => 1,
      hour: () => 7,
      minute: () => 30,
    }),
    '2026-09-30T23:30:00.000Z',
  );
});
