import { Button, Empty, Select, Space, Table, Typography } from 'antd';
import type { TableColumnsType } from 'antd';
import { useId, useState, type MouseEvent } from 'react';
import type { TaskDetail } from '@merine/api-contract';
import { PageHeader } from '../../shared/ui/PageHeader';
import { DetailBackLink } from '../../shared/ui/DetailBackLink';
import { TaskIntelligenceBackground } from './components/TaskIntelligenceBackground';
import { BranchDrawer } from './components/BranchDrawer';
import { TaskHistoryList } from './components/TaskHistoryList';
import { TaskTag } from './components/TaskTag';
import { TaskText, TaskTime } from './components/TaskTime';
import {
  branchRows,
  findRow,
  historyEntries,
  parentRowKeys,
  taskStatusTag,
  taskSummary,
  taskTodos,
  taskCommands,
  type TaskLabels,
  type BranchRow,
  type OutcomeLabel,
  type TaskActionHandler,
} from './model';
import styles from './TaskDetailView.module.css';

/** 任务要求与预期结果默认两行，可展开看全文。 */
const CLAMP = {
  rows: 2,
  expandable: 'collapsible',
  symbol: (expanded: boolean) => (expanded ? '收起' : '展开'),
} as const;

function DueCell({ row }: { row: BranchRow }) {
  if (!row.dueAt) return <span className={styles.muted}>—</span>;
  return (
    <div className={styles.dueCell}>
      <span>
        期限 <TaskTime value={row.dueAt} />
      </span>
      {row.answeredAt ? (
        <span>
          {row.branch.status === 'RECALLED' ? '撤回' : '答复'} <TaskTime value={row.answeredAt} />
          {row.timing === 'late' ? (
            <>
              {' · '}
              <span className={styles.late}>逾期</span>
            </>
          ) : null}
        </span>
      ) : null}
      {row.timing === 'overdue' ? <TaskTag label="已逾期" tone="danger" /> : null}
    </div>
  );
}

interface TaskDetailViewProps {
  task: TaskDetail;
  outcomeLabel: OutcomeLabel;
  labels: TaskLabels;
  backTo: string;
  refreshing: boolean;
  onRefresh: () => void;
  onAction: TaskActionHandler;
}

/**
 * 任务详情：先看本单位要做什么，再看任务本身与各单位的结论；
 * 一个单位的完整结果、交接与经过放进分支抽屉，整单办理记录默认收起。
 */
export function TaskDetailView({
  task,
  outcomeLabel,
  labels,
  backTo,
  refreshing,
  onRefresh,
  onAction,
}: TaskDetailViewProps) {
  const [drawer, setDrawer] = useState<{ key: string; open: boolean }>();
  const [collapsed, setCollapsed] = useState(() => new Set<string>());
  const [historyOpen, setHistoryOpen] = useState(false);
  const [historyUnit, setHistoryUnit] = useState<string>();
  const historyPanelId = useId();

  const rows = branchRows(task, outcomeLabel, labels);
  const summary = taskSummary(rows);
  const todos = taskTodos(task);
  const history = historyEntries(task.actions ?? [], outcomeLabel, labels.returnReason);
  const drawerRow = drawer ? findRow(rows, drawer.key) : undefined;
  const expandedRowKeys = parentRowKeys(rows).filter((key) => !collapsed.has(key));
  const units = [
    ...new Set(
      history.flatMap((entry) => (entry.target ? [entry.actor, entry.target] : [entry.actor])),
    ),
  ];
  const shownHistory = historyUnit
    ? history.filter((entry) => entry.actor === historyUnit || entry.target === historyUnit)
    : history;
  const dueChanged = !!task.initialDueAt && task.initialDueAt !== task.currentDueAt;
  const overdue =
    task.status === 'OPEN' && !!task.currentDueAt && Date.now() > Date.parse(task.currentDueAt);
  const closing = taskCommands(task);
  const allOnTime =
    summary.total > 0 &&
    summary.answered + summary.recalled === summary.total &&
    summary.onTime === summary.total;

  function openDrawer(key: string, trigger?: HTMLElement | null) {
    // 抽屉打开时记下当前焦点、关闭后交还；先把焦点放到单位名上，点整行打开时也能回到这一行。
    trigger?.focus({ preventScroll: true });
    setDrawer({ key, open: true });
  }

  function toggleRow(expanded: boolean, row: BranchRow) {
    setCollapsed((current) => {
      const next = new Set(current);
      if (expanded) next.delete(row.key);
      else next.add(row.key);
      return next;
    });
  }

  const columns: TableColumnsType<BranchRow> = [
    {
      title: '单位',
      key: 'unit',
      width: 300,
      render: (_, row) => (
        // 展开图标与缩进在单元格里是浮动的，块级格式化上下文让第二行不绕到图标下面。
        <div className={styles.unit}>
          <button
            type="button"
            data-drawer-trigger
            className={styles.unitButton}
            onClick={(event) => {
              event.stopPropagation();
              openDrawer(row.key, event.currentTarget);
            }}
          >
            {row.unitName}
          </button>
          {row.route ? <span className={styles.route}>{row.route}</span> : null}
        </div>
      ),
    },
    {
      title: '状态',
      key: 'status',
      width: 120,
      render: (_, row) => <TaskTag {...row.tag} />,
    },
    {
      title: '结论 · 最新进展',
      key: 'summary',
      render: (_, row) => (
        <span
          className={
            row.summaryMuted ? `${styles.summaryText} ${styles.muted}` : styles.summaryText
          }
          title={row.summary}
        >
          {row.summary}
        </span>
      ),
    },
    {
      title: '期限 · 答复',
      key: 'due',
      width: 224,
      render: (_, row) => <DueCell row={row} />,
    },
  ];

  return (
    <div className={styles.page}>
      <DetailBackLink to={backTo}>返回任务列表</DetailBackLink>
      <PageHeader
        demo={false}
        title={task.title ?? '任务详情'}
        actions={
          <Space>
            <TaskTag {...taskStatusTag(task.status, labels.order)} />
            <Button loading={refreshing} onClick={onRefresh}>
              刷新
            </Button>
          </Space>
        }
      />
      <div className={styles.content}>
        {task.id && <TaskIntelligenceBackground taskId={task.id} />}
        <div className={styles.headingMeta}>
          <span className={styles.mono}>{task.taskNo ?? '—'}</span>
          <span>· {task.issuerUnitName ?? '—'}</span>
          <span>· {task.issuerUserName ?? '—'}</span>
          <span>
            · 发起 <TaskTime value={task.createdAt} />
          </span>
          <span>
            · 期限 <TaskTime value={task.currentDueAt} />
          </span>
        </div>
        <section className={styles.card}>
          <div className={styles.sectionHead}>
            <h2>办理结论</h2>
            <div className={styles.summary}>
              <span>
                {summary.answered + summary.recalled === summary.total ? '全部答复' : '已答复'}{' '}
                <span className={styles.mono}>
                  {summary.answered + summary.recalled}/{summary.total}
                </span>
              </span>
              {summary.recalled ? (
                <TaskTag
                  label={`${labels.branch('RECALLED')} ${summary.recalled}`}
                  tone="neutral"
                />
              ) : null}
              {summary.outcomes.map((item) => (
                <TaskTag key={item.code} label={`${item.label} ${item.count}`} tone={item.tone} />
              ))}
              {summary.late ? <TaskTag label={`逾期结束 ${summary.late}`} tone="warning" /> : null}
              {summary.overdue ? (
                <TaskTag label={`已逾期 ${summary.overdue}`} tone="danger" />
              ) : null}
              {allOnTime ? (
                <span className={styles.muted}>全部按期</span>
              ) : summary.onTime ? (
                <span className={styles.muted}>按期 {summary.onTime}</span>
              ) : null}
              {task.issuerMine ? null : (
                <span className={styles.muted}>仅统计本单位可见的分支</span>
              )}
            </div>
          </div>
          <div className={styles.conclusion}>
            <Typography.Paragraph className={styles.conclusionText}>
              {task.issuerUnitName ?? '发起单位'}结论：{task.conclusion || '—'}
            </Typography.Paragraph>
            {task.completedAt ? (
              <span className={styles.muted}>
                {task.closedByName ?? '—'} · <TaskTime value={task.completedAt} />
              </span>
            ) : null}
          </div>
          {task.status === 'COMPLETED' && !task.conclusion ? (
            <p className={styles.note}>该任务在办结流程上线前已自动办结，没有总体结论</p>
          ) : null}
          {closing.length ? (
            <div className={styles.todoActions}>
              {closing.map((command) => (
                <Button
                  key={command.key}
                  type={command.primary ? 'primary' : 'default'}
                  disabled={!!command.disabledReason}
                  onClick={() => onAction(command.action)}
                >
                  {command.label}
                </Button>
              ))}
            </div>
          ) : null}
          {closing
            .filter((command) => command.disabledReason)
            .map((command) => (
              <p className={styles.note} key={command.key}>
                {command.label}：{command.disabledReason}
              </p>
            ))}
        </section>
        {todos.length ? (
          <section className={styles.card}>
            <h2>我单位待办</h2>
            <ul className={styles.todos}>
              {todos.map((todo) => (
                <li key={todo.key}>
                  <div className={styles.todoText}>
                    <strong>{todo.title}</strong>
                    <span className={styles.muted}>
                      <TaskText parts={todo.description} />
                    </span>
                  </div>
                  <div className={styles.todoActions}>
                    {todo.commands.map((command) => (
                      <Button
                        key={command.key}
                        type={command.primary ? 'primary' : 'default'}
                        disabled={!!command.disabledReason}
                        onClick={() => onAction(command.action, todo.branch, command.transfer)}
                      >
                        {command.label}
                      </Button>
                    ))}
                    {todo.branch ? (
                      <Button
                        type="link"
                        className={styles.todoLink}
                        onClick={(event) => openDrawer(todo.branch?.id ?? '', event.currentTarget)}
                      >
                        查看详情
                      </Button>
                    ) : null}
                  </div>
                  {todo.commands
                    .filter((command) => command.disabledReason)
                    .map((command) => (
                      <p key={command.key} className={styles.disabledReason}>
                        {command.label}：{command.disabledReason}
                      </p>
                    ))}
                </li>
              ))}
            </ul>
          </section>
        ) : null}

        <section className={styles.card}>
          <h2>任务概况</h2>
          <dl className={styles.facts}>
            <div>
              <dt>期限</dt>
              <dd className={styles.due}>
                <TaskTime value={task.currentDueAt} />
                {dueChanged ? (
                  <span className={styles.muted}>
                    （原定 <TaskTime value={task.initialDueAt} />）
                  </span>
                ) : null}
                {task.completedAt ? (
                  <span>
                    办结 <TaskTime value={task.completedAt} />
                  </span>
                ) : null}
                {overdue ? <TaskTag label="已逾期" tone="danger" /> : null}
              </dd>
            </div>
            <div>
              <dt>任务要求</dt>
              <dd>
                <Typography.Paragraph ellipsis={CLAMP}>
                  {task.instruction || '—'}
                </Typography.Paragraph>
              </dd>
            </div>
            <div>
              <dt>预期结果</dt>
              <dd>
                <Typography.Paragraph ellipsis={CLAMP}>
                  {task.expectedResult || '—'}
                </Typography.Paragraph>
              </dd>
            </div>
          </dl>
          {task.sourceResultId ? (
            <p className={styles.note}>历史记录：此任务曾根据另一项任务的处置结果发起。</p>
          ) : null}
        </section>

        <section className={`${styles.card} ${styles.tableCard}`}>
          <div className={styles.sectionHead}>
            <h2>各单位办理情况</h2>
            <span className={styles.hint}>点击单位查看完整结果与经过</span>
          </div>
          <div className={styles.tableWrap}>
            <Table<BranchRow>
              rowKey="key"
              columns={columns}
              dataSource={rows}
              pagination={false}
              tableLayout="fixed"
              scroll={{ x: 924 }}
              expandable={{ expandedRowKeys, onExpand: toggleRow }}
              rowClassName={(row) => (drawer?.key === row.key ? styles.selectedRow : '')}
              onRow={(row) => ({
                onClick: (event: MouseEvent<HTMLTableRowElement>) => {
                  // 正在选中文字（例如复制结论）时不打开抽屉。
                  if (window.getSelection()?.toString()) return;
                  openDrawer(
                    row.key,
                    event.currentTarget.querySelector<HTMLElement>('[data-drawer-trigger]'),
                  );
                },
              })}
              locale={{
                emptyText: (
                  <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无可见的分支" />
                ),
              }}
            />
          </div>
        </section>

        {history.length ? (
          <section className={styles.card}>
            <div className={styles.sectionHead}>
              <h2>办理记录</h2>
              <span className={styles.muted}>
                {historyOpen && historyUnit
                  ? `${shownHistory.length} / ${history.length} 条`
                  : `${history.length} 条`}
              </span>
              <Button
                type="link"
                size="small"
                className={styles.toggle}
                aria-expanded={historyOpen}
                aria-controls={historyPanelId}
                onClick={() => setHistoryOpen((value) => !value)}
              >
                {historyOpen ? '收起' : '展开'}
              </Button>
            </div>
            <div id={historyPanelId} hidden={!historyOpen} className={styles.historyPanel}>
              {units.length > 1 ? (
                <Select<string>
                  allowClear
                  placeholder="全部单位"
                  aria-label="按单位筛选办理记录"
                  className={styles.historyFilter}
                  value={historyUnit}
                  onChange={setHistoryUnit}
                  options={units.map((unit) => ({ value: unit, label: unit }))}
                />
              ) : null}
              <TaskHistoryList entries={shownHistory} />
            </div>
          </section>
        ) : null}
      </div>

      <BranchDrawer
        task={task}
        row={drawerRow}
        open={!!drawer?.open && !!drawerRow}
        history={history}
        labels={labels}
        onClose={() => setDrawer((current) => current && { ...current, open: false })}
        afterClose={() => setDrawer(undefined)}
        onAction={onAction}
      />
    </div>
  );
}
