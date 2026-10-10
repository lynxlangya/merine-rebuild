import type { MessagePart } from '@merine/api-contract';
import { barLayout, readBarChart } from '../chart';
import { NoticePart } from './SimpleParts';
import { TablePart } from './TablePart';
import styles from './AgentParts.module.css';

/**
 * 图表部件：本期只渲染 BAR 的简单 SVG，并提供等价数据表；
 * 非 BAR 或异常数值一律降级为 NOTICE，不新增图表依赖。
 * 柱子在固定宽度槽位内居中（几何计算见 ../chart），数值标注在柱顶。
 */
export function ChartPart({ spec }: { spec: Extract<MessagePart, { type: 'CHART' }>['spec'] }) {
  const result = readBarChart({ type: 'CHART', spec });
  if (!result.ok) {
    return <NoticePart level="WARNING" text={result.reason} />;
  }
  const { title, unit, categories, series, period, coverage } = result.data;
  const layout = barLayout(result.data);
  const note = [unit ? `单位：${unit}` : null, period ? `期间：${period}` : null, coverage]
    .filter(Boolean)
    .join(' · ');
  return (
    <figure className={styles.chart}>
      {title && <figcaption className={styles.partTitle}>{title}</figcaption>}
      <svg
        className={styles.chartSvg}
        role="img"
        aria-label={`${title ?? '柱状图'}${unit ? `，单位 ${unit}` : ''}`}
        width={layout.width}
        height={layout.height}
        viewBox={`0 0 ${layout.width} ${layout.height}`}
      >
        <line
          className={styles.axisLine}
          x1={12}
          x2={layout.width - 12}
          y1={layout.baseline}
          y2={layout.baseline}
        />
        {layout.bars.map((bar, index) => (
          <rect
            key={`${bar.series}-${index}`}
            className={styles.bar}
            x={bar.x}
            y={bar.y}
            width={bar.width}
            height={bar.height}
            rx={2}
          />
        ))}
        {layout.valueLabels.map((label, index) => (
          <text
            key={`value-${index}`}
            className={styles.valueLabel}
            x={label.x}
            y={label.y}
            textAnchor="middle"
          >
            {label.text}
          </text>
        ))}
        {layout.labels.map((label) => (
          <text
            key={label.text}
            className={styles.axisLabel}
            x={label.x}
            y={layout.baseline + 18}
            textAnchor="middle"
          >
            {label.text}
          </text>
        ))}
      </svg>
      {note && <div className={styles.partNote}>{note}</div>}
      <details className={styles.chartData}>
        <summary>查看数据表</summary>
        <TablePart
          part={{
            type: 'TABLE',
            columns: [
              { key: 'category', title: '分类', numeric: false },
              ...series.map((item, index) => ({
                key: `s-${index}`,
                title: item.name,
                unit,
                numeric: true,
              })),
            ],
            rows: categories.map((category, rowIndex) => [
              category,
              ...series.map((item) => String(item.data[rowIndex])),
            ]),
          }}
        />
      </details>
    </figure>
  );
}
