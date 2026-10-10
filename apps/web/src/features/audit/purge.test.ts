import assert from 'node:assert/strict';
import test from 'node:test';
import { purgeResultText } from './purge.ts';

test('复述保留期与删除条数', () => {
  const text = purgeResultText({
    retentionDays: 30,
    deleted: 12,
    earliest: '2026-08-11T02:00:00Z',
    latest: '2026-09-01T09:30:00Z',
  });
  assert.match(text, /已永久删除 12 条超过 30 天的记录/);
  assert.match(text, /覆盖 \d{4}\/\d{2}\/\d{2} \d{2}:\d{2} 至 \d{4}\/\d{2}\/\d{2} \d{2}:\d{2}/);
});

test('没有可删记录时给出明确说明', () => {
  assert.equal(
    purgeResultText({ retentionDays: 30, deleted: 0, earliest: null, latest: null }),
    '没有超过 30 天的记录，未删除任何内容',
  );
});
