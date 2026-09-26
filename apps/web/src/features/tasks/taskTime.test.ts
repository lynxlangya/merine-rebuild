import assert from 'node:assert/strict';
import test from 'node:test';
import { formatTaskTime, taskWallTimeToUtc } from './taskTime.ts';

test('任务期限始终按 UTC+8 显示和提交', () => {
  assert.equal(formatTaskTime('2026-09-30T10:00:00Z'), '2026/09/30 18:00:00');
  assert.equal(formatTaskTime(), '—');
  assert.equal(
    taskWallTimeToUtc({
      year: () => 2026,
      month: () => 8,
      date: () => 30,
      hour: () => 18,
      minute: () => 0,
      second: () => 0,
    }),
    '2026-09-30T10:00:00.000Z',
  );
});
