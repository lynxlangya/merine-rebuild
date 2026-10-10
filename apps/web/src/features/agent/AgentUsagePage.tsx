import { useQuery } from '@tanstack/react-query';
import {
  Alert,
  Button,
  Empty,
  Pagination,
  Segmented,
  Select,
  Skeleton,
  Table,
  Tooltip,
} from 'antd';
import { InfoCircleOutlined, ReloadOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { useState } from 'react';
import type { ChatRunRecord } from '@merine/api-contract';
import { errorText } from '../../shared/api-error';
import { useModelOptionsQuery } from '../model-providers/public';
import { chatKeys, fetchChatRuns, fetchChatRunStats } from './api';
import { UsageModelBars, UsageStateBar, UsageTrendChart } from './components/UsageCharts';
import {
  USAGE_METRICS,
  averageDuration,
  fillDays,
  formatCompact,
  formatDuration,
  formatUsageValue,
  localOffsetMinutes,
  metricValue,
  shortDate,
  stateLabel,
  successRate,
  trendSummary,
  type UsageMetric,
} from './usage';
import styles from './AgentUsagePage.module.css';

const PAGE_SIZE = 20;
const RANGES = [
  { label: '近 7 天', value: 7 },
  { label: '近 14 天', value: 14 },
  { label: '近 30 天', value: 30 },
];
const STATE_FILTERS = [
  { label: '全部', value: '' },
  { label: '成功', value: 'SUCCEEDED' },
  { label: '失败', value: 'FAILED' },
  { label: '已停止', value: 'ABORTED' },
];
const exact = (value: number | null | undefined) =>
  value == null ? '—' : value.toLocaleString('zh-CN');

/** 统计周期只影响聚合；执行明细沿用全时间分页，模型条件两者共用。 */
export function AgentUsagePage() {
  const [days, setDays] = useState(14);
  const [model, setModel] = useState('');
  const [metric, setMetric] = useState<UsageMetric>('tokens');
  const [state, setState] = useState('');
  const [page, setPage] = useState(1);
  const models = useModelOptionsQuery();
  const modelChoices = (models.data ?? []).map((option) => ({
    value: option.id,
    label: `${option.providerName} · ${option.displayName}`,
    providerId: option.providerId,
    modelId: option.modelId,
  }));
  const chosen = modelChoices.find((choice) => choice.value === model);
  const filter = {
    days,
    providerId: chosen?.providerId ?? '',
    modelId: chosen?.modelId ?? '',
    offsetMinutes: localOffsetMinutes(),
  };
  const stats = useQuery({
    queryKey: chatKeys.runStats(filter),
    queryFn: ({ signal }) => fetchChatRunStats(filter, signal),
    staleTime: 30 * 1000,
  });
  const runs = useQuery({
    queryKey: chatKeys.runs(state, page, filter),
    queryFn: ({ signal }) => fetchChatRuns({ page, pageSize: PAGE_SIZE, state, ...filter }, signal),
    staleTime: 30 * 1000,
  });
  const totals = stats.data?.totals;
  const series = fillDays(stats.data?.series ?? [], days);
  const metricMeta = USAGE_METRICS.find((item) => item.value === metric)!;
  const metricTotal = series.reduce((sum, point) => sum + metricValue(point, metric), 0);
  const rate = totals ? successRate(totals) : null;
  const average = totals ? averageDuration(totals) : null;
  const summary = trendSummary(series, metric);
  const refreshing = stats.isFetching || runs.isFetching || models.isFetching;
  const paused = [stats, runs, models].some((query) => query.fetchStatus === 'paused');
  const changeState = (value: string) => {
    setState(value);
    setPage(1);
  };
  const refresh = () => {
    void stats.refetch();
    void runs.refetch();
    void models.refetch();
  };

  const columns: ColumnsType<ChatRunRecord> = [
    {
      title: '执行时间',
      dataIndex: 'startedAt',
      width: 152,
      render: (value: string) => {
        const date = new Date(value);
        return (
          <time className={styles.time} dateTime={value} title={date.toLocaleString('zh-CN')}>
            <span>
              {date.toLocaleDateString('zh-CN', {
                year: 'numeric',
                month: '2-digit',
                day: '2-digit',
              })}
            </span>
            <span className={styles.secondary}>
              {date.toLocaleTimeString('zh-CN', { hour12: false })}
            </span>
          </time>
        );
      },
    },
    {
      title: '模型 / 连接',
      key: 'model',
      width: 240,
      render: (_, run) => (
        <span className={styles.model}>
          <span className={styles.modelId}>{run.modelId || '本地演示'}</span>
          <span className={styles.secondary}>{run.providerName || '本地演示'}</span>
        </span>
      ),
    },
    {
      title: '状态',
      dataIndex: 'state',
      width: 104,
      render: (value?: string) => (
        <span className={`${styles.status} ${styles[(value ?? '').toLowerCase()]}`}>
          <span className={styles.statusDot} aria-hidden="true" />
          {value ? stateLabel(value) : '—'}
        </span>
      ),
    },
    { title: '耗时', dataIndex: 'durationMs', width: 100, align: 'right', render: formatDuration },
    {
      title: (
        <span>
          Token <span className={styles.columnHint}>输入 / 输出</span>
        </span>
      ),
      key: 'tokens',
      width: 170,
      align: 'right',
      render: (_, run) => (
        <span className={styles.numeric}>
          {exact(run.promptTokens)} <span className={styles.separator}>/</span>{' '}
          {exact(run.completionTokens)}
        </span>
      ),
    },
    {
      title: (
        <span>
          字符 <span className={styles.columnHint}>输入 / 输出</span>
        </span>
      ),
      key: 'chars',
      width: 154,
      align: 'right',
      render: (_, run) => (
        <span className={styles.numeric}>
          {exact(run.inputChars)} <span className={styles.separator}>/</span>{' '}
          {exact(run.outputChars)}
        </span>
      ),
    },
    {
      title: '说明',
      key: 'error',
      width: 190,
      render: (_, run) =>
        run.errorCode || run.errorMessage ? (
          <span className={styles.error}>
            {run.errorMessage || run.errorCode}
            {run.errorCode && run.errorMessage && (
              <span className={styles.errorCode}>{run.errorCode}</span>
            )}
          </span>
        ) : run.promptTokens == null && run.completionTokens == null ? (
          <span className={styles.secondary}>未返回 Token 用量</span>
        ) : (
          <span className={styles.secondary}>—</span>
        ),
    },
  ];

  return (
    <div className={styles.page}>
      <div className={styles.content}>
        <header className={styles.head}>
          <div>
            <div className={styles.titleLine}>
              <h1 className={styles.title}>用量概览</h1>
              <span className={styles.accountBadge}>仅当前账号</span>
            </div>
            <p className={styles.lead}>了解助手的使用情况，查看消耗与执行表现。</p>
          </div>
          <div className={styles.filters}>
            <label className={styles.filter}>
              <span>统计周期</span>
              <Select aria-label="时间范围" value={days} options={RANGES} onChange={setDays} />
            </label>
            <label className={`${styles.filter} ${styles.modelFilter}`}>
              <span>模型</span>
              <Select
                aria-label="按模型筛选"
                value={model}
                loading={models.isLoading}
                disabled={models.isError}
                options={[{ value: '', label: '全部模型' }, ...modelChoices]}
                onChange={(value) => {
                  setModel(value);
                  setPage(1);
                }}
              />
            </label>
            <Tooltip title="刷新用量与执行记录">
              <Button
                className={styles.refresh}
                icon={<ReloadOutlined spin={refreshing} />}
                aria-label="刷新用量与执行记录"
                disabled={refreshing || paused}
                onClick={refresh}
              />
            </Tooltip>
          </div>
        </header>
        {paused && (
          <Alert
            type="warning"
            title="网络连接已断开，恢复连接后会自动刷新"
            description={
              stats.data || runs.data ? '当前保留上次加载的结果。' : '正在等待连接恢复。'
            }
          />
        )}
        {models.isError && (
          <Alert
            type="warning"
            title={`模型选项加载失败：${errorText(models.error)}`}
            action={
              <Button size="small" onClick={() => void models.refetch()}>
                重试
              </Button>
            }
          />
        )}
        {stats.isError && (
          <Alert
            type="error"
            title={stats.data ? '统计刷新失败，当前显示上次结果' : '用量统计加载失败'}
            description={errorText(stats.error)}
            action={
              <Button size="small" onClick={() => void stats.refetch()}>
                重试
              </Button>
            }
          />
        )}

        <section className={styles.overview} aria-label="用量指标" aria-busy={stats.isLoading}>
          <Kpi
            label="Token 用量"
            value={totals ? formatCompact(totals.promptTokens + totals.completionTokens) : '—'}
            caption={
              totals
                ? `输入 ${exact(totals.promptTokens)} / 输出 ${exact(totals.completionTokens)}`
                : '输入 / 输出'
            }
            hint="仅累计上游已返回的 Token，不估算缺失用量"
            accent
            loading={stats.isLoading}
          />
          <Kpi
            label="执行次数"
            value={totals ? exact(totals.runs) : '—'}
            caption={`近 ${days} 天的助手执行`}
            loading={stats.isLoading}
          />
          <Kpi
            label="成功率"
            value={rate == null ? '—' : `${rate}%`}
            caption={
              totals
                ? `成功 ${totals.succeeded} · 失败 ${totals.failed} · 停止 ${totals.aborted}`
                : '成功执行 / 全部执行'
            }
            hint="成功次数除以全部执行次数，已停止计入分母"
            loading={stats.isLoading}
          />
          <Kpi
            label="平均耗时"
            value={average == null ? '—' : formatDuration(average)}
            caption="按全部执行计算"
            loading={stats.isLoading}
          />
          <Kpi
            label="字符用量"
            value={totals ? formatCompact(totals.inputChars + totals.outputChars) : '—'}
            caption={
              totals
                ? `输入 ${exact(totals.inputChars)} / 输出 ${exact(totals.outputChars)}`
                : '输入 / 输出'
            }
            hint="请求与回答的字符数，不含思考过程"
            loading={stats.isLoading}
          />
        </section>

        <div className={styles.charts}>
          <section
            className={`${styles.panel} ${styles.trendPanel}`}
            aria-labelledby="usage-trend-title"
          >
            <div className={styles.panelHead}>
              <div>
                <h2 id="usage-trend-title">使用趋势</h2>
                <p>
                  按天统计 · {shortDate(series[0].date)} 至{' '}
                  {shortDate(series[series.length - 1].date)}
                </p>
              </div>
              <Segmented
                size="small"
                aria-label="切换趋势口径"
                options={USAGE_METRICS}
                value={metric}
                onChange={(value) => setMetric(value as UsageMetric)}
              />
            </div>
            {stats.isLoading ? (
              <div className={styles.chartLoading}>
                <Skeleton active paragraph={{ rows: 5 }} />
              </div>
            ) : !totals ? (
              <div className={styles.chartEmpty}>统计暂不可用，请重试</div>
            ) : totals.runs === 0 ? (
              <div className={styles.chartEmpty}>
                <Empty
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                  description="这个统计周期内还没有执行记录"
                />
              </div>
            ) : (
              <>
                <div className={styles.trendMeta}>
                  <div>
                    <strong>{formatUsageValue(metricTotal, metric)}</strong>
                    <span>{metric === 'duration' ? '累计耗时' : `${metricMeta.label} 合计`}</span>
                  </div>
                  {metric === 'tokens' && (
                    <div className={styles.tokenLegend}>
                      <span>
                        <i />
                        输入
                      </span>
                      <span>
                        <i />
                        输出
                      </span>
                    </div>
                  )}
                </div>
                <UsageTrendChart points={series} metric={metric} label={metricMeta.label} />
                <dl className={styles.rangeSummary}>
                  <div>
                    <dt>日均{metricMeta.label}</dt>
                    <dd>{formatUsageValue(summary.averagePerDay, metric)}</dd>
                  </div>
                  <div>
                    <dt>活跃天数</dt>
                    <dd>
                      {summary.activeDays}
                      <span> / {days} 天</span>
                    </dd>
                  </div>
                  <div>
                    <dt>峰值日</dt>
                    <dd>
                      {summary.peak ? shortDate(summary.peak.date) : '—'}
                      {summary.peak && (
                        <span> · {formatUsageValue(summary.peak.value, metric)}</span>
                      )}
                    </dd>
                  </div>
                </dl>
              </>
            )}
          </section>
          <section className={`${styles.panel} ${styles.distribution}`} aria-label="执行与模型分布">
            {stats.isLoading ? (
              <Skeleton active paragraph={{ rows: 7 }} />
            ) : totals ? (
              <>
                <div className={styles.distributionHead}>
                  <h2>执行状态</h2>
                  <span>{exact(totals.runs)} 次执行</span>
                </div>
                <UsageStateBar totals={totals} />
                <div className={styles.modelSection}>
                  <div className={styles.distributionHead}>
                    <h2>模型分布</h2>
                    <span>
                      {metric === 'chars'
                        ? '按执行次数'
                        : metric === 'duration'
                          ? '累计耗时'
                          : metricMeta.label}
                    </span>
                  </div>
                  <UsageModelBars
                    models={stats.data?.models ?? []}
                    metric={metric}
                    total={metric === 'chars' ? totals.runs : metricTotal}
                  />
                  {(stats.data?.models.length ?? 0) >= 8 && (
                    <p className={styles.note}>展示执行次数最多的 8 个模型，占比按全部模型计算。</p>
                  )}
                  {metric === 'chars' && (
                    <p className={styles.note}>模型分布暂不支持字符统计，按执行次数展示。</p>
                  )}
                </div>
              </>
            ) : (
              <p className={styles.note}>统计暂不可用</p>
            )}
          </section>
        </div>
        {totals && totals.runsWithoutUsage > 0 && (
          <div className={styles.usageNote}>
            <InfoCircleOutlined />
            <span>
              {totals.runsWithoutUsage} 次执行未返回 Token 用量，未计入 Token
              合计；明细以「—」标记。
            </span>
          </div>
        )}

        <section
          className={`${styles.panel} ${styles.details}`}
          aria-labelledby="usage-details-title"
        >
          <div className={styles.panelHead}>
            <div>
              <div className={styles.detailTitle}>
                <h2 id="usage-details-title">执行明细</h2>
                {runs.data && <span>{exact(runs.data.total)} 条</span>}
              </div>
              <p>全部时间 · {chosen ? chosen.label : '全部模型'} · 时间按本地时区显示</p>
            </div>
            <Segmented
              size="small"
              aria-label="按状态筛选"
              options={STATE_FILTERS}
              value={state}
              onChange={(value) => changeState(String(value))}
            />
          </div>
          {runs.isError && (
            <Alert
              type="error"
              title={runs.data ? '明细刷新失败，当前显示上次结果' : '执行记录加载失败'}
              description={errorText(runs.error)}
              action={
                <Button size="small" onClick={() => void runs.refetch()}>
                  重试
                </Button>
              }
            />
          )}
          <Table<ChatRunRecord>
            className={styles.table}
            rowKey="id"
            size="small"
            columns={columns}
            dataSource={runs.data?.items ?? []}
            loading={runs.isFetching}
            pagination={false}
            locale={{
              emptyText: (
                <Empty
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                  description={
                    runs.isError
                      ? '执行记录暂不可用，请重试'
                      : state || model
                        ? '没有符合筛选条件的执行记录'
                        : '还没有执行记录'
                  }
                >
                  {!runs.isError && (state || model) && (
                    <Button
                      size="small"
                      onClick={() => {
                        setState('');
                        setModel('');
                        setPage(1);
                      }}
                    >
                      清除筛选
                    </Button>
                  )}
                </Empty>
              ),
            }}
            scroll={{ x: 1110 }}
          />
          <footer className={styles.foot}>
            <span>Token 与字符均按输入 / 输出展示；「—」表示上游未返回用量。</span>
            <Pagination
              current={page}
              pageSize={PAGE_SIZE}
              total={runs.data?.total ?? 0}
              showSizeChanger={false}
              onChange={setPage}
              hideOnSinglePage
            />
          </footer>
        </section>
      </div>
    </div>
  );
}

function Kpi({
  label,
  value,
  caption,
  hint,
  accent = false,
  loading,
}: {
  label: string;
  value: string;
  caption: string;
  hint?: string;
  accent?: boolean;
  loading: boolean;
}) {
  return (
    <div className={`${styles.kpi} ${accent ? styles.primaryKpi : ''}`}>
      <span className={styles.kpiLabel}>
        {label}
        {hint && (
          <Tooltip title={hint}>
            <button className={styles.hint} type="button" aria-label={hint}>
              <InfoCircleOutlined />
            </button>
          </Tooltip>
        )}
      </span>
      {loading ? (
        <Skeleton.Input active size="small" />
      ) : (
        <strong className={styles.kpiValue}>{value}</strong>
      )}
      <span className={styles.kpiCaption}>{caption}</span>
    </div>
  );
}
