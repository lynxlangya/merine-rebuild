import type { MessagePart } from '@merine/api-contract';

/**
 * BAR 图的展示前校验：本期只渲染柱状图，且只接受有限、非负、长度一致的数值。
 * 校验失败返回原因，由 ChartPart 以 NOTICE 呈现；异常数据不静默渲染成成功结果。
 */
export interface BarChartData {
  title?: string;
  unit?: string;
  categories: string[];
  series: { name: string; data: number[] }[];
  max: number;
  period?: string;
  coverage?: string;
}

export type BarChartResult = { ok: true; data: BarChartData } | { ok: false; reason: string };

export interface BarBar {
  x: number;
  y: number;
  width: number;
  height: number;
  value: number;
  series: string;
}

export interface BarLayout {
  width: number;
  height: number;
  baseline: number;
  bars: BarBar[];
  labels: { x: number; text: string }[];
  valueLabels: { x: number; y: number; text: string }[];
}

const MAX_CATEGORIES = 30;
const PADDING_X = 24;
/** 每个分类的固定槽位宽度：柱子在其中居中，不随分类数量被拉伸。 */
const GROUP_WIDTH = 110;
const PLOT_HEIGHT = 140;
const TOP_PADDING = 20;

/** 纯几何：柱高按最大值等比、组内居中，标签与数值位置一并产出，便于测试与复用。 */
export function barLayout(data: BarChartData): BarLayout {
  const width = PADDING_X * 2 + GROUP_WIDTH * data.categories.length;
  const baseline = TOP_PADDING + PLOT_HEIGHT;
  // slot 含柱间空隙；drawn 是实际矩形宽度。总宽不含最后一个空隙，保证整组居中。
  const slot = Math.max(10, Math.min(34, Math.floor((GROUP_WIDTH - 16) / data.series.length)));
  const drawnWidth = Math.max(6, slot - 6);
  const totalWidth = slot * data.series.length - 6;
  const bars: BarBar[] = [];
  const labels: { x: number; text: string }[] = [];
  const valueLabels: { x: number; y: number; text: string }[] = [];
  data.categories.forEach((category, index) => {
    const groupLeft = PADDING_X + GROUP_WIDTH * index;
    labels.push({ x: groupLeft + GROUP_WIDTH / 2, text: category });
    data.series.forEach((series, seriesIndex) => {
      const value = series.data[index];
      const barHeight = data.max === 0 ? 0 : Math.round((value / data.max) * PLOT_HEIGHT);
      const x = groupLeft + (GROUP_WIDTH - totalWidth) / 2 + seriesIndex * slot;
      bars.push({
        x,
        y: baseline - barHeight,
        width: drawnWidth,
        height: barHeight,
        value,
        series: series.name,
      });
      valueLabels.push({ x: x + drawnWidth / 2, y: baseline - barHeight - 4, text: String(value) });
    });
  });
  return { width, height: baseline + 24, baseline, bars, labels, valueLabels };
}

export function readBarChart(part: Extract<MessagePart, { type: 'CHART' }>): BarChartResult {
  const spec = part.spec;
  if (!spec) return { ok: false, reason: '图表缺少数据定义' };
  if (spec.chartType !== 'BAR') {
    return { ok: false, reason: `当前版本只支持柱状图（收到 ${spec.chartType ?? '未知类型'}）` };
  }
  const categories = (spec.categories ?? []).filter(
    (item): item is string => typeof item === 'string',
  );
  if (categories.length === 0) return { ok: false, reason: '图表没有分类数据' };
  if (categories.length > MAX_CATEGORIES) {
    return { ok: false, reason: `图表分类超过 ${MAX_CATEGORIES} 个，未渲染` };
  }
  const series: { name: string; data: number[] }[] = [];
  for (const item of spec.series ?? []) {
    if (!item || typeof item.name !== 'string' || !Array.isArray(item.data)) continue;
    const data: number[] = [];
    for (const value of item.data) {
      if (typeof value !== 'number' || !Number.isFinite(value) || value < 0) {
        return { ok: false, reason: '图表包含缺失或异常的数值' };
      }
      data.push(value);
    }
    if (data.length !== categories.length) {
      return { ok: false, reason: '图表分类与数据长度不一致' };
    }
    series.push({ name: item.name, data });
  }
  if (series.length === 0) return { ok: false, reason: '图表没有系列数据' };
  const max = Math.max(...series.flatMap((item) => item.data), 0);
  if (max <= 0) return { ok: false, reason: '图表数值全为零，未渲染' };
  return {
    ok: true,
    data: {
      title: spec.title,
      unit: spec.unit,
      categories,
      series,
      max,
      period: spec.meta?.period,
      coverage: spec.meta?.coverage,
    },
  };
}
