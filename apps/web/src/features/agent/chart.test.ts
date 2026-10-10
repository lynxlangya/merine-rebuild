import assert from 'node:assert/strict';
import test from 'node:test';
import { barLayout, readBarChart } from './chart.ts';

const part = (spec: unknown) => ({ type: 'CHART', spec }) as Parameters<typeof readBarChart>[0];

test('合法 BAR 图返回数据与刻度', () => {
  const result = readBarChart(
    part({
      chartType: 'BAR',
      title: '立案数',
      unit: '起',
      categories: ['甲港', '乙港'],
      series: [{ name: '立案', data: [8, 4] }],
      meta: { period: '2026-09', coverage: '合成数据' },
    }),
  );
  assert.equal(result.ok, true);
  if (result.ok) {
    assert.deepEqual(result.data.categories, ['甲港', '乙港']);
    assert.equal(result.data.max, 8);
    assert.equal(result.data.period, '2026-09');
  }
});

test('非 BAR、长度不一致、异常数值与超量分类都要拒绝', () => {
  assert.equal(readBarChart(part({ chartType: 'PIE', categories: ['a'], series: [] })).ok, false);
  assert.equal(
    readBarChart(
      part({ chartType: 'BAR', categories: ['a', 'b'], series: [{ name: 's', data: [1] }] }),
    ).ok,
    false,
  );
  assert.equal(
    readBarChart(
      part({ chartType: 'BAR', categories: ['a'], series: [{ name: 's', data: [Number.NaN] }] }),
    ).ok,
    false,
  );
  assert.equal(
    readBarChart(part({ chartType: 'BAR', categories: ['a'], series: [{ name: 's', data: [-1] }] }))
      .ok,
    false,
  );
  assert.equal(
    readBarChart(
      part({
        chartType: 'BAR',
        categories: Array.from({ length: 31 }, (_, index) => `c${index}`),
        series: [{ name: 's', data: Array.from({ length: 31 }, () => 1) }],
      }),
    ).ok,
    false,
  );
  assert.equal(
    readBarChart(part({ chartType: 'BAR', categories: ['a'], series: [{ name: 's', data: [0] }] }))
      .ok,
    false,
  );
});

test('柱子按槽位居中，标签与柱心对齐，高度按比例', () => {
  const data = {
    categories: ['甲港', '乙港'],
    series: [{ name: '立案', data: [8, 4] }],
    max: 8,
  };
  const layout = barLayout(data);
  // 每个分类的槽位固定 110，整体宽度不随分类数量被拉伸
  assert.equal(layout.width, 24 * 2 + 110 * 2);
  assert.equal(layout.labels.length, 2);
  assert.equal(layout.bars.length, 2);
  for (const [index, bar] of layout.bars.entries()) {
    const center = bar.x + bar.width / 2;
    assert.ok(Math.abs(center - layout.labels[index].x) < 0.001, '柱心与标签必须对齐');
  }
  // 8 -> 满高 140，4 -> 一半
  assert.equal(layout.bars[0].height, 140);
  assert.equal(layout.bars[1].height, 70);
  // 数值标注在柱顶之上
  assert.equal(layout.valueLabels[0].y, layout.baseline - 140 - 4);
  assert.ok(
    layout.bars.every((bar) => bar.y >= 0 && bar.x >= 0 && bar.x + bar.width <= layout.width),
  );
});

test('多系列时整组居中且不超出槽位', () => {
  const data = {
    categories: ['甲港', '乙港', '丙港'],
    series: [
      { name: '甲', data: [3, 6, 9] },
      { name: '乙', data: [2, 4, 6] },
    ],
    max: 9,
  };
  const layout = barLayout(data);
  assert.equal(layout.bars.length, 6);
  for (let group = 0; group < 3; group++) {
    const bars = layout.bars.slice(group * 2, group * 2 + 2);
    const left = Math.min(...bars.map((bar) => bar.x));
    const right = Math.max(...bars.map((bar) => bar.x + bar.width));
    const groupLeft = 24 + 110 * group;
    assert.ok(left >= groupLeft - 0.001 && right <= groupLeft + 110 + 0.001);
    assert.ok(Math.abs((left + right) / 2 - layout.labels[group].x) < 0.001);
  }
});
