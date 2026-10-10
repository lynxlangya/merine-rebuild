import assert from 'node:assert/strict';
import test from 'node:test';
import {
  averageDuration,
  fillDays,
  formatCompact,
  formatDuration,
  formatUsageValue,
  localOffsetMinutes,
  metricValue,
  modelMetricValue,
  rankUsageModels,
  shortDate,
  stateLabel,
  successRate,
  trendSummary,
  usageAxisMax,
} from './usage.ts';

test('duration and state labels read for humans', () => {
  assert.equal(formatDuration(742), '742 ms');
  assert.equal(formatDuration(1904), '1.9 s');
  assert.equal(stateLabel('SUCCEEDED'), '成功');
  assert.equal(stateLabel('ABORTED'), '已停止');
  assert.equal(stateLabel('UNKNOWN_X'), 'UNKNOWN_X');
});

test('daily series is filled to a continuous local range', () => {
  const now = new Date(2026, 9, 10, 12, 0, 0); // 本地 2026-10-10
  const filled = fillDays(
    [
      {
        date: '2026-10-10',
        runs: 2,
        failed: 1,
        promptTokens: 10,
        completionTokens: 20,
        inputChars: 5,
        outputChars: 6,
        durationMs: 100,
      },
    ],
    3,
    now,
  );
  assert.deepEqual(
    filled.map((point) => point.date),
    ['2026-10-08', '2026-10-09', '2026-10-10'],
  );
  assert.equal(filled[0].runs, 0);
  assert.equal(filled[2].runs, 2);
  assert.equal(filled[1].promptTokens, 0);
});

test('metric values follow the selected perspective', () => {
  const point = {
    runs: 3,
    promptTokens: 10,
    completionTokens: 5,
    inputChars: 100,
    outputChars: 20,
    durationMs: 1234,
  };
  assert.equal(metricValue(point, 'tokens'), 15);
  assert.equal(metricValue(point, 'runs'), 3);
  assert.equal(metricValue(point, 'chars'), 120);
  assert.equal(metricValue(point, 'duration'), 1234);
});

test('success rate and average duration stay unknown without runs', () => {
  assert.equal(successRate({ runs: 0, succeeded: 0 }), null);
  assert.equal(successRate({ runs: 8, succeeded: 7 }), 87.5);
  assert.equal(averageDuration({ runs: 0, durationMs: 0 }), null);
  assert.equal(averageDuration({ runs: 4, durationMs: 1000 }), 250);
  assert.equal(formatCompact(4_800_000), '4.8M');
  assert.equal(formatCompact(12_345), '12.3k');
  assert.equal(shortDate('2026-10-04'), '10-04');
  assert.equal(
    localOffsetMinutes(new Date(2026, 9, 10)),
    localOffsetMinutes(new Date(2026, 9, 10)),
  );
});

test('trend summary averages over the range and finds the peak day', () => {
  const points = [
    {
      date: '2026-10-08',
      runs: 0,
      failed: 0,
      promptTokens: 0,
      completionTokens: 0,
      inputChars: 0,
      outputChars: 0,
      durationMs: 0,
    },
    {
      date: '2026-10-09',
      runs: 1,
      failed: 0,
      promptTokens: 10,
      completionTokens: 10,
      inputChars: 1,
      outputChars: 1,
      durationMs: 100,
    },
    {
      date: '2026-10-10',
      runs: 2,
      failed: 0,
      promptTokens: 20,
      completionTokens: 20,
      inputChars: 2,
      outputChars: 2,
      durationMs: 200,
    },
  ];
  const summary = trendSummary(points, 'tokens');
  assert.equal(summary.averagePerDay, 20); // (0 + 20 + 40) / 3
  assert.equal(summary.activeDays, 2);
  assert.deepEqual(summary.peak, { date: '2026-10-10', value: 40 });
  assert.deepEqual(trendSummary([], 'runs'), { averagePerDay: 0, activeDays: 0, peak: null });
});

test('usage values carry a time unit for the duration metric', () => {
  assert.equal(formatUsageValue(1234, 'tokens'), '1.2k');
  assert.equal(formatUsageValue(742, 'duration'), '742 ms');
  assert.equal(formatUsageValue(90_000, 'duration'), '1.5 min');
  assert.equal(formatUsageValue(7_200_000, 'duration'), '2 h');
});

test('axis maximum picks a readable 1/2/5 step and never zero', () => {
  assert.equal(usageAxisMax(0), 1);
  assert.equal(usageAxisMax(1), 1);
  assert.equal(usageAxisMax(1.2), 2);
  assert.equal(usageAxisMax(4_800_000), 5_000_000);
  assert.equal(usageAxisMax(9_000), 10_000);
});

test('model ranking follows the selected metric and keeps the input order', () => {
  const models = [
    {
      providerId: 'p',
      providerName: '甲',
      modelId: 'a',
      runs: 1,
      failed: 0,
      promptTokens: 10,
      completionTokens: 90,
      durationMs: 100,
    },
    {
      providerId: 'p',
      providerName: '甲',
      modelId: 'b',
      runs: 3,
      failed: 0,
      promptTokens: 10,
      completionTokens: 10,
      durationMs: 50,
    },
  ];
  assert.deepEqual(
    rankUsageModels(models, 'runs').map((model) => model.modelId),
    ['b', 'a'],
  );
  assert.deepEqual(
    rankUsageModels(models, 'tokens').map((model) => model.modelId),
    ['a', 'b'],
  );
  assert.equal(modelMetricValue(models[0], 'chars'), 1);
  assert.deepEqual(
    models.map((model) => model.modelId),
    ['a', 'b'],
  );
});
