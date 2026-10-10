import { Tooltip } from 'antd';
import { useEffect, useRef, useState } from 'react';
import type { ChatRunStats } from '@merine/api-contract';
import {
  formatDuration,
  formatUsageValue,
  metricValue,
  modelMetricValue,
  rankUsageModels,
  shortDate,
  stateLabel,
  usageAxisMax,
  type UsageMetric,
} from '../usage';
import styles from './UsageCharts.module.css';

type Point = ChatRunStats['series'][number];
const CHART_HEIGHT = 164;
const PAD_LEFT = 56;
const PAD_RIGHT = 20;
const PAD_TOP = 14;
const PAD_BOTTOM = 32;

/** 按天柱状图：固定高度，宽度跟随容器；Token 区分输入与输出。 */
export function UsageTrendChart({
  points,
  metric,
  label,
}: {
  points: Point[];
  metric: UsageMetric;
  label: string;
}) {
  const container = useRef<HTMLDivElement>(null);
  const [width, setWidth] = useState(720);
  const [hover, setHover] = useState<number>();
  useEffect(() => {
    const observer = new ResizeObserver(([entry]) =>
      setWidth(Math.max(240, entry.contentRect.width)),
    );
    if (container.current) observer.observe(container.current);
    return () => observer.disconnect();
  }, []);
  const values = points.map((point) => metricValue(point, metric));
  const max = usageAxisMax(Math.max(0, ...values));
  const plotHeight = CHART_HEIGHT - PAD_TOP - PAD_BOTTOM;
  const stepX = (width - PAD_LEFT - PAD_RIGHT) / Math.max(1, points.length);
  const barWidth = Math.max(2, Math.min(24, stepX * 0.55));
  const x = (index: number) => PAD_LEFT + stepX * (index + 0.5);
  const y = (value: number) => PAD_TOP + plotHeight * (1 - value / max);
  const ticks = [
    ...new Set([0, max / 2, max].map((value) => (metric === 'runs' ? Math.round(value) : value))),
  ];
  const labelStep = Math.max(
    1,
    Math.ceil(points.length / Math.max(2, Math.floor((width - PAD_LEFT) / 64))),
  );
  const active = hover == null ? undefined : Math.min(hover, points.length - 1);
  return (
    <div ref={container} className={styles.trend}>
      <svg
        viewBox={`0 0 ${width} ${CHART_HEIGHT}`}
        role="group"
        aria-label={`按天${label}趋势，聚焦日期可查看用量`}
        className={styles.svg}
      >
        {ticks.map((value) => (
          <g key={value}>
            <line
              x1={PAD_LEFT}
              x2={width - PAD_RIGHT}
              y1={y(value)}
              y2={y(value)}
              className={styles.grid}
            />
            <text x={PAD_LEFT - 10} y={y(value) + 4} className={styles.axisLabel} textAnchor="end">
              {formatUsageValue(value, metric)}
            </text>
          </g>
        ))}
        {active != null && (
          <rect
            x={x(active) - stepX / 2}
            y={PAD_TOP}
            width={stepX}
            height={plotHeight}
            className={styles.highlight}
          />
        )}
        {points.map((point, index) => {
          const value = values[index];
          const inputHeight = (plotHeight * point.promptTokens) / max;
          const outputHeight = (plotHeight * point.completionTokens) / max;
          const showDate =
            index === 0 ||
            index === points.length - 1 ||
            (index % labelStep === 0 && points.length - 1 - index >= labelStep);
          return (
            <g key={point.date}>
              {metric === 'tokens' ? (
                <>
                  <rect
                    x={x(index) - barWidth / 2}
                    y={y(value)}
                    width={barWidth}
                    height={outputHeight}
                    rx={2}
                    className={styles.outputBar}
                  />
                  <rect
                    x={x(index) - barWidth / 2}
                    y={y(point.promptTokens)}
                    width={barWidth}
                    height={inputHeight}
                    rx={2}
                    className={styles.inputBar}
                  />
                </>
              ) : (
                <rect
                  x={x(index) - barWidth / 2}
                  y={y(value)}
                  width={barWidth}
                  height={(plotHeight * value) / max}
                  rx={2}
                  className={styles.outputBar}
                />
              )}
              {showDate && (
                <text
                  x={x(index)}
                  y={CHART_HEIGHT - 10}
                  className={styles.axisLabel}
                  textAnchor="middle"
                >
                  {shortDate(point.date)}
                </text>
              )}
              <Tooltip
                trigger={['hover', 'focus']}
                title={
                  <div className={styles.tip}>
                    <strong>{point.date}</strong>
                    <span>
                      执行 {point.runs} 次 · 失败 {point.failed} 次
                    </span>
                    <span>
                      Token：{point.promptTokens.toLocaleString('zh-CN')} /{' '}
                      {point.completionTokens.toLocaleString('zh-CN')}
                    </span>
                    <span>
                      字符：{point.inputChars.toLocaleString('zh-CN')} /{' '}
                      {point.outputChars.toLocaleString('zh-CN')}
                    </span>
                    <span>累计耗时：{formatUsageValue(point.durationMs, 'duration')}</span>
                  </div>
                }
              >
                <rect
                  x={x(index) - stepX / 2}
                  y={PAD_TOP}
                  width={stepX}
                  height={plotHeight}
                  tabIndex={0}
                  role="img"
                  aria-label={`${point.date}，${point.runs} 次执行，${label} ${formatUsageValue(value, metric)}`}
                  className={styles.hit}
                  onMouseEnter={() => setHover(index)}
                  onMouseLeave={() => setHover(undefined)}
                  onFocus={() => setHover(index)}
                  onBlur={() => setHover(undefined)}
                />
              </Tooltip>
            </g>
          );
        })}
      </svg>
    </div>
  );
}

/** 保留零值状态；一位小数避免 88% + 13% 的误读。 */
export function UsageStateBar({ totals }: { totals: ChatRunStats['totals'] }) {
  const segments = [
    { key: 'SUCCEEDED', value: totals.succeeded },
    { key: 'FAILED', value: totals.failed },
    { key: 'ABORTED', value: totals.aborted },
  ];
  return (
    <div className={styles.stateBar}>
      <div className={styles.bar} aria-hidden="true">
        {segments.map((segment) => (
          <span
            key={segment.key}
            className={`${styles.segment} ${styles[segment.key.toLowerCase()]}`}
            hidden={segment.value === 0}
            style={{ width: `${totals.runs ? (segment.value / totals.runs) * 100 : 0}%` }}
          />
        ))}
      </div>
      <ul className={styles.legend}>
        {segments.map((segment) => (
          <li key={segment.key}>
            <span className={styles.legendLabel}>
              <span
                className={`${styles.swatch} ${styles[segment.key.toLowerCase()]}`}
                aria-hidden="true"
              />
              {stateLabel(segment.key)}
            </span>
            <strong>
              {segment.value}
              <span> 次</span>
            </strong>
            <span className={styles.legendValue}>
              {totals.runs ? `${Math.round((segment.value / totals.runs) * 1000) / 10}%` : '—'}
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}

/** 按当前口径排序，占比的分母为全量聚合；字符口径明确使用执行次数。 */
export function UsageModelBars({
  models,
  metric,
  total,
}: {
  models: ChatRunStats['models'];
  metric: UsageMetric;
  total: number;
}) {
  if (models.length === 0) return <p className={styles.empty}>暂无模型用量</p>;
  const unit = metric === 'chars' || metric === 'runs' ? '次' : metric === 'tokens' ? 'Token' : '';
  return (
    <ul className={styles.modelBars}>
      {rankUsageModels(models, metric).map((model) => {
        const value = modelMetricValue(model, metric);
        const share = total ? (value / total) * 100 : 0;
        return (
          <li key={`${model.providerId}-${model.modelId}`}>
            <div className={styles.modelHead}>
              <span className={styles.modelName}>{model.modelId || '本地演示'}</span>
              <Tooltip
                title={
                  metric === 'duration'
                    ? formatDuration(value)
                    : `${value.toLocaleString('zh-CN')} ${unit}`
                }
              >
                <strong className={styles.modelValue}>
                  {formatUsageValue(value, metric === 'chars' ? 'runs' : metric)}
                  {unit && <span> {unit}</span>}
                </strong>
              </Tooltip>
            </div>
            <div className={styles.modelSub}>
              <span>
                {model.providerName || '本地演示'}
                {model.failed > 0 && (
                  <span className={styles.modelFailed}> · 失败 {model.failed}</span>
                )}
              </span>
              <span>{Math.round(share * 10) / 10}%</span>
            </div>
            <div className={styles.track} aria-hidden="true">
              <span className={styles.fill} style={{ width: `${Math.min(100, share)}%` }} />
            </div>
          </li>
        );
      })}
    </ul>
  );
}
