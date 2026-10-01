import { Button, Drawer } from 'antd';
import type { ReactNode } from 'react';
import type { TaskDetail, TaskTransfer } from '@merine/api-contract';
import {
  branchCommands,
  branchHistory,
  currentAssignment,
  formatDuration,
  isTransferCandidate,
  pendingTransfer,
  transferTone,
  type BranchRow,
  type HistoryEntry,
  type TaskActionHandler,
  type TaskAssignment,
  type TaskLabels,
} from '../model';
import { TaskHistoryList } from './TaskHistoryList';
import { TaskTag } from './TaskTag';
import { TaskTime } from './TaskTime';
import styles from './BranchDrawer.module.css';

const SEGMENT_VERBS: Record<string, string> = {
  DOWNWARD: '下发给',
  RETURN: '退回给',
  REASSIGN: '重新派发给',
  PEER_TRANSFER: '交接给',
};

/** 责任段结束时写下的说明：退回段是退回原因，交接段是交接原因。 */
const END_REASON_LABELS: Record<string, string> = {
  RETURNED: '退回原因',
  TRANSFERRED: '交接原因',
  RECALLED: '撤回原因',
};

function Fact({ label, children }: { label: string; children?: ReactNode }) {
  return (
    <div>
      <dt>{label}</dt>
      <dd>{children || '—'}</dd>
    </div>
  );
}

function TransferItem({ transfer, labels }: { transfer: TaskTransfer; labels: TaskLabels }) {
  return (
    <div className={styles.transfer}>
      <div className={styles.transferHead}>
        交接给{transfer.targetUnitName ?? '目标支队'}
        <TaskTag label={labels.transfer(transfer.status)} tone={transferTone(transfer.status)} />
      </div>
      <dl className={styles.facts}>
        <Fact label="申请单位">{transfer.fromUnitName}</Fact>
        <Fact label="交接原因">{transfer.reason}</Fact>
        <Fact label="已做工作">{transfer.workDone}</Fact>
        <Fact label="依据">{transfer.evidenceSummary}</Fact>
        <Fact label="剩余事项">{transfer.remainingWork}</Fact>
        {transfer.requiredDurationMinutes ? (
          <Fact label="至少需要">{formatDuration(transfer.requiredDurationMinutes)}</Fact>
        ) : null}
        {transfer.approvedDueAt ? (
          <Fact label="批准期限">
            <TaskTime value={transfer.approvedDueAt} />
          </Fact>
        ) : null}
        {transfer.targetResponseReason ? (
          <Fact label="接收支队说明">{transfer.targetResponseReason}</Fact>
        ) : null}
        {transfer.issuerDecisionReason ? (
          <Fact label="总队说明">{transfer.issuerDecisionReason}</Fact>
        ) : null}
      </dl>
      <p className={styles.meta}>
        申请 <TaskTime value={transfer.requestedAt} />
        {transfer.targetRespondedAt ? (
          <>
            {' · 接收支队回应 '}
            <TaskTime value={transfer.targetRespondedAt} />
          </>
        ) : null}
        {transfer.issuerDecidedAt ? (
          <>
            {' · 总队决定 '}
            <TaskTime value={transfer.issuerDecidedAt} />
          </>
        ) : null}
      </p>
    </div>
  );
}

function SegmentItem({ segment, labels }: { segment: TaskAssignment; labels: TaskLabels }) {
  // 退回与交接生效时自动承接，只有下发与重新派发需要对方确认。
  const needsAccept = segment.sourceAction === 'DOWNWARD' || segment.sourceAction === 'REASSIGN';
  return (
    <li>
      <div>
        {segment.fromUnitName ?? '有关单位'}
        {SEGMENT_VERBS[segment.sourceAction ?? ''] ?? '交给'}
        {segment.toUnitName ?? '有关单位'}
        <span className={styles.muted}>
          {' · '}
          {labels.assignment(segment.status)}
        </span>
      </div>
      <p className={styles.meta}>
        截止 <TaskTime value={segment.dueAt} />
        {needsAccept && segment.acceptedAt ? (
          <>
            {' · 承接 '}
            <TaskTime value={segment.acceptedAt} />
          </>
        ) : null}
        {needsAccept && !segment.acceptedAt
          ? segment.endedAt
            ? ' · 未承接'
            : ' · 尚未承接'
          : null}
        {segment.endedAt ? (
          <>
            {' · 结束 '}
            <TaskTime value={segment.endedAt} />
          </>
        ) : null}
      </p>
      {segment.endReason ? (
        <p className={styles.note}>
          {END_REASON_LABELS[segment.status ?? ''] ?? '说明'}：
          {segment.endReasonCode ? `${labels.returnReason(segment.endReasonCode)} · ` : ''}
          {segment.endReason}
        </p>
      ) : null}
    </li>
  );
}

interface BranchDrawerProps {
  task: TaskDetail;
  /** 关闭动画结束前保留原行；刷新后行不再可见时为空，只剩标题栏随抽屉收起。 */
  row?: BranchRow;
  open: boolean;
  history: HistoryEntry[];
  labels: TaskLabels;
  onClose: () => void;
  afterClose: () => void;
  onAction: TaskActionHandler;
}

/**
 * 分支详情：一个承办单位的结果、当前责任、交接与办理经过都在这里看全。
 * 抽屉本身只读，操作打开页面上的办理弹窗，办完刷新后内容随之更新。
 */
export function BranchDrawer({
  task,
  row,
  open,
  history,
  labels,
  onClose,
  afterClose,
  onAction,
}: BranchDrawerProps) {
  const first = row?.branch.assignments?.[0];
  const subtitle = row?.route ?? (first ? `由${first.fromUnitName ?? '有关单位'}下发` : undefined);
  return (
    <Drawer
      open={open}
      size={640}
      onClose={onClose}
      destroyOnHidden
      afterOpenChange={(visible) => {
        if (!visible) afterClose();
      }}
      title={
        row ? (
          <div>
            <div className={styles.title}>
              {row.unitName}
              <TaskTag {...row.tag} />
            </div>
            {subtitle ? <p className={styles.subtitle}>{subtitle}</p> : null}
          </div>
        ) : null
      }
    >
      {row ? (
        <BranchBody task={task} row={row} history={history} labels={labels} onAction={onAction} />
      ) : null}
    </Drawer>
  );
}

function BranchBody({
  task,
  row,
  history,
  labels,
  onAction,
}: Pick<BranchDrawerProps, 'task' | 'history' | 'labels' | 'onAction'> & {
  row: BranchRow;
}) {
  const { branch } = row;
  const commands = branchCommands(task, branch);
  const primaryIndex = commands.findIndex((command) => command.primary);
  const assignment = currentAssignment(branch);
  const result = branch.result;
  const candidate = isTransferCandidate(branch, pendingTransfer(branch));
  const ownInstruction =
    branch.instruction && branch.instruction !== task.instruction ? branch.instruction : undefined;
  const ownExpected =
    branch.expectedResult && branch.expectedResult !== task.expectedResult
      ? branch.expectedResult
      : undefined;
  const segments = branch.assignments ?? [];
  const transfers = branch.transfers ?? [];
  const entries = branchHistory(history, task, branch);

  return (
    <div className={styles.body}>
      {commands.length ? (
        <div className={styles.commands}>
          {commands.map((command, index) => (
            <Button
              key={command.key}
              type={index === primaryIndex ? 'primary' : 'default'}
              disabled={!!command.disabledReason}
              onClick={() => onAction(command.action, branch, command.transfer)}
            >
              {command.label}
            </Button>
          ))}
        </div>
      ) : null}

      {commands
        .filter((command) => command.disabledReason)
        .map((command) => (
          <p key={command.key} className={styles.meta}>
            {command.label}：{command.disabledReason}
          </p>
        ))}

      {result ? (
        <section className={styles.section}>
          <h3>办理结果</h3>
          <p className={styles.meta}>
            由{assignment?.toUnitName ?? '承办单位'} · {result.submittedByName ?? '—'} 于{' '}
            <TaskTime value={result.submittedAt} /> 提交
            {row.timing === 'on-time' ? ' · 按期' : null}
            {row.timing === 'late' ? (
              <>
                {' · '}
                <span className={styles.late}>逾期</span>
                {'（期限 '}
                <TaskTime value={row.dueAt} />
                {'）'}
              </>
            ) : null}
          </p>
          <dl className={styles.facts}>
            <Fact label="结论">{result.conclusion}</Fact>
            <Fact label="办理经过">{result.handlingDetail}</Fact>
            {result.suggestedUnitName ? (
              <Fact label="历史建议单位">{result.suggestedUnitName}</Fact>
            ) : null}
          </dl>
        </section>
      ) : null}

      {branch.status === 'RECALLED' ? (
        <section className={styles.section}>
          <h3>撤回原因</h3>
          <p className={styles.text}>{assignment?.endReason ?? '—'}</p>
        </section>
      ) : null}

      {!result && branch.status === 'OPEN' ? (
        <section className={styles.section}>
          <h3>当前状态</h3>
          {candidate ? (
            <p className={styles.text}>
              {pendingTransfer(branch)?.status === 'AWAITING_ISSUER'
                ? '本单位已同意承接，等待总队决定。批准生效前，原承办单位仍负责此分支。'
                : '请确认是否接收交接。批准生效前，原承办单位仍负责此分支。'}
            </p>
          ) : (
            <dl className={styles.facts}>
              <Fact label="当前责任">
                {assignment
                  ? `${assignment.toUnitName ?? '—'} · ${labels.assignment(assignment.status)}`
                  : undefined}
              </Fact>
              <Fact label="本段截止">
                <span className={styles.inline}>
                  <TaskTime value={assignment?.dueAt} />
                  {row.timing === 'overdue' ? <TaskTag label="已逾期" tone="danger" /> : null}
                </span>
              </Fact>
              <Fact label="最新进展">{row.summaryMuted ? undefined : row.summary}</Fact>
            </dl>
          )}
        </section>
      ) : null}

      {ownInstruction || ownExpected ? (
        <section className={styles.section}>
          <h3>本分支要求</h3>
          <dl className={styles.facts}>
            {ownInstruction ? <Fact label="任务要求">{ownInstruction}</Fact> : null}
            {ownExpected ? <Fact label="预期结果">{ownExpected}</Fact> : null}
          </dl>
        </section>
      ) : null}

      {transfers.length ? (
        <section className={styles.section}>
          <h3>交接申请</h3>
          {transfers.map((transfer) => (
            <TransferItem key={transfer.id} transfer={transfer} labels={labels} />
          ))}
        </section>
      ) : null}

      {segments.length > 1 ? (
        <section className={styles.section}>
          <h3>历次责任与期限</h3>
          <ol className={styles.segments}>
            {segments.map((segment) => (
              <SegmentItem key={segment.id} segment={segment} labels={labels} />
            ))}
          </ol>
        </section>
      ) : null}

      {entries.length ? (
        <section className={styles.section}>
          <h3>办理经过</h3>
          <TaskHistoryList entries={entries} />
        </section>
      ) : null}
    </div>
  );
}
