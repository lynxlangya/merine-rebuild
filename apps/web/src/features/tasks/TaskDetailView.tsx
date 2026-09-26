import { ArrowLeftOutlined } from '@ant-design/icons';
import { Button, Space, Tag } from 'antd';
import type { ReactNode } from 'react';
import type { TaskBranch, TaskDetail, TaskTransfer } from '@merine/api-contract';
import { PageHeader } from '../../shared/ui/PageHeader';
import { PERMISSIONS } from '../../shared/permissions';
import { formatTaskTime } from './taskTime';
import styles from './TaskDetailView.module.css';

type Assignment = NonNullable<TaskBranch['assignments']>[number];
type Action = NonNullable<TaskDetail['actions']>[number];

const statusLabels: Record<string, string> = {
  PENDING_ACCEPT: '待承接',
  IN_PROGRESS: '办理中',
  COMPLETED: '已反馈',
  RETURNED: '已退回',
  REASSIGNED: '已重新派发',
  TRANSFERRED: '已交接',
  AWAITING_TARGET: '待目标支队确认',
  AWAITING_ISSUER: '待总队审批',
  APPROVED: '已批准',
  TARGET_DECLINED: '目标支队拒绝',
  ISSUER_REJECTED: '总队未批准',
  WITHDRAWN: '已撤回',
};

const dateTime = formatTaskTime;

function duration(minutes?: number) {
  if (!minutes) return '—';
  if (minutes % 1440 === 0) return `${minutes / 1440} 天`;
  if (minutes % 60 === 0) return `${minutes / 60} 小时`;
  return `${minutes} 分钟`;
}

function outcomeColor(code?: string) {
  if (code === 'FULFILLED') return 'green';
  if (code === 'PARTIAL') return 'gold';
  if (code === 'OUT_OF_JURISDICTION') return 'blue';
  return 'default';
}

function actionSentence(action: Action, outcomeLabel: (code?: string) => string) {
  const actor = action.actorUnitName ?? '有关单位';
  const target = action.targetUnitName ? `给${action.targetUnitName}` : '';
  switch (action.code) {
    case 'CREATE':
      return `${actor}发起任务`;
    case 'DISPATCH':
      return `${actor}将任务下发${target}`;
    case 'ACCEPT':
      return `${actor}承接任务`;
    case 'PROGRESS':
      return `${actor}记录办理进展`;
    case 'RETURN':
      return `${actor}退回任务`;
    case 'REASSIGN':
      return `${actor}重新派发任务${target}`;
    case 'RESULT':
      return `${actor}提交处置结果：${outcomeLabel(action.note)}`;
    case 'TRANSFER_REQUEST':
      return `${actor}申请交接${target}`;
    case 'TRANSFER_ACCEPT':
      return `${actor}同意承接交接任务`;
    case 'TRANSFER_DECLINE':
      return `${actor}拒绝承接交接任务`;
    case 'TRANSFER_APPROVE':
      return `${actor}批准交接${target}`;
    case 'TRANSFER_REJECT':
      return `${actor}未批准交接`;
    case 'TRANSFER_WITHDRAW':
      return `${actor}撤回交接申请`;
    case 'EXTEND_DUE':
      return `${actor}调整任务期限至 ${dateTime(action.newDueAt)}`;
    default:
      return `${actor}办理了任务`;
  }
}

export function TaskDetailView({
  task,
  can,
  canCreateFollowup,
  outcomeLabel,
  onBack,
  onAction,
}: {
  task: TaskDetail;
  can: (permission: string) => boolean;
  canCreateFollowup: boolean;
  outcomeLabel: (code?: string) => string;
  onBack: () => void;
  onAction: (
    action: string,
    branch?: TaskBranch,
    transfer?: TaskTransfer,
    sourceResultId?: string,
  ) => void;
}) {
  const branches = task.branches ?? [];
  const childOpen = (branch: TaskBranch) =>
    branches.some((child) => child.parentBranchId === branch.id && child.status === 'OPEN');
  const assignmentOf = (branch: TaskBranch) =>
    branch.assignments?.find((assignment) => assignment.id === branch.currentAssignmentId) ??
    branch.assignments?.at(-1);

  function branchButtons(branch: TaskBranch, assignment?: Assignment): ReactNode[] {
    if (branch.status !== 'OPEN' || !branch.currentMine || !assignment) return [];
    const activeTransfer = branch.transfers?.find((transfer) =>
      ['AWAITING_TARGET', 'AWAITING_ISSUER'].includes(transfer.status ?? ''),
    );
    const buttons: ReactNode[] = [];
    if (assignment.status === 'PENDING_ACCEPT') {
      if (can(PERMISSIONS.taskAccept))
        buttons.push(
          <Button key="accept" type="primary" onClick={() => onAction('accept', branch)}>
            承接任务
          </Button>,
        );
      if (can(PERMISSIONS.taskReturn))
        buttons.push(
          <Button key="return" onClick={() => onAction('return', branch)}>
            说明原因并退回
          </Button>,
        );
    }
    if (assignment.status === 'IN_PROGRESS') {
      if (can(PERMISSIONS.taskProgress))
        buttons.push(
          <Button key="progress" onClick={() => onAction('progress', branch)}>
            记录进展
          </Button>,
        );
      if (!activeTransfer && branch.canDispatchDownward && can(PERMISSIONS.taskDispatch)) {
        const reassign = assignment.sourceAction === 'RETURN';
        buttons.push(
          <Button
            key="dispatch"
            onClick={() => onAction(reassign ? 'reassign' : 'dispatch', branch)}
          >
            {reassign ? '重新派发' : '下发下级'}
          </Button>,
        );
      }
      if (!activeTransfer && !childOpen(branch) && can(PERMISSIONS.taskSubmitResult))
        buttons.push(
          <Button key="result" type="primary" onClick={() => onAction('results', branch)}>
            提交处置结果
          </Button>,
        );
      if (
        !activeTransfer &&
        !childOpen(branch) &&
        branch.canTransferPeer &&
        can(PERMISSIONS.taskTransferRequest)
      )
        buttons.push(
          <Button key="transfer" onClick={() => onAction('transfer-requests', branch)}>
            申请支队交接
          </Button>,
        );
      if (activeTransfer && can(PERMISSIONS.taskTransferRequest))
        buttons.push(
          <Button key="withdraw" onClick={() => onAction('withdraw', branch, activeTransfer)}>
            撤回交接申请
          </Button>,
        );
    }
    return buttons;
  }

  const todos: { key: string; title: string; description: string; buttons: ReactNode[] }[] = [];
  for (const branch of branches) {
    const assignment = assignmentOf(branch);
    const buttons = branchButtons(branch, assignment);
    if (buttons.length) {
      const pending = assignment?.status === 'PENDING_ACCEPT';
      const transferPending = branch.transfers?.some((transfer) =>
        ['AWAITING_TARGET', 'AWAITING_ISSUER'].includes(transfer.status ?? ''),
      );
      todos.push({
        key: `branch-${branch.id}`,
        title: `${assignment?.toUnitName ?? '本单位'}：${pending ? '请确认是否承接' : '正在办理'}`,
        description: `${transferPending ? '交接生效前本单位仍负责 · ' : ''}截止 ${dateTime(assignment?.dueAt)}`,
        buttons,
      });
    }
    for (const transfer of branch.transfers ?? []) {
      if (
        transfer.targetMine &&
        transfer.status === 'AWAITING_TARGET' &&
        can(PERMISSIONS.taskTransferRespond)
      ) {
        todos.push({
          key: `respond-${transfer.id}`,
          title: '请确认交接申请',
          description: `${transfer.targetUnitName ?? '本单位'}收到一项交接申请；同意后仍需总队批准。`,
          buttons: [
            <Button
              key="respond"
              type="primary"
              onClick={() => onAction('respond', branch, transfer)}
            >
              确认或拒绝承接
            </Button>,
          ],
        });
      }
      if (
        task.issuerMine &&
        transfer.status === 'AWAITING_ISSUER' &&
        can(PERMISSIONS.taskTransferDecide)
      ) {
        todos.push({
          key: `decide-${transfer.id}`,
          title: '请决定是否批准交接',
          description: `目标单位：${transfer.targetUnitName ?? '目标支队'}。批准时须确定新期限。`,
          buttons: [
            <Button
              key="decide"
              type="primary"
              onClick={() => onAction('decide', branch, transfer)}
            >
              审批交接
            </Button>,
          ],
        });
      }
    }
  }

  const visibleIds = new Set(branches.map((branch) => branch.id));
  const roots = branches.filter(
    (branch) => !branch.parentBranchId || !visibleIds.has(branch.parentBranchId),
  );

  function renderBranch(branch: TaskBranch): ReactNode {
    const assignment = assignmentOf(branch);
    const firstUnit = branch.assignments?.[0]?.toUnitName;
    const currentUnit =
      assignment?.toUnitName ?? branch.transfers?.[0]?.targetUnitName ?? '待确认承办单位';
    const children = branches.filter((child) => child.parentBranchId === branch.id);
    const pendingTransfer = branch.transfers?.find((transfer) =>
      ['AWAITING_TARGET', 'AWAITING_ISSUER'].includes(transfer.status ?? ''),
    );
    const transferCandidate = !assignment && !!pendingTransfer?.targetMine;
    const hasBranchRequirements =
      (branch.instruction && branch.instruction !== task.instruction) ||
      (branch.expectedResult && branch.expectedResult !== task.expectedResult);
    const requirements = (
      <div className={styles.requirementRows}>
        {branch.instruction && branch.instruction !== task.instruction && (
          <div>
            <span>办理要求</span>
            <p>{branch.instruction}</p>
          </div>
        )}
        {branch.expectedResult && branch.expectedResult !== task.expectedResult && (
          <div>
            <span>需要反馈</span>
            <p>{branch.expectedResult}</p>
          </div>
        )}
      </div>
    );
    return (
      <div className={styles.branchGroup} key={branch.id}>
        <details className={styles.branch}>
          <summary className={styles.branchSummary}>
            <span className={styles.branchHeading}>
              <span className={styles.branchRoute}>
                {firstUnit && firstUnit !== currentUnit ? (
                  <>
                    <span className={styles.branchRouteLabel}>原派</span>
                    <strong>{firstUnit}</strong>
                    <span className={styles.branchRouteChange} aria-hidden="true">
                      →
                    </span>
                    <span className={styles.branchRouteLabel}>
                      {branch.status === 'COMPLETED' ? '最终反馈' : '当前单位'}
                    </span>
                    <strong>{currentUnit}</strong>
                  </>
                ) : (
                  <strong>
                    {transferCandidate ? `交接候选：${currentUnit}` : (firstUnit ?? currentUnit)}
                  </strong>
                )}
              </span>
              <span className={styles.branchTags}>
                <Tag color={branch.status === 'COMPLETED' ? 'default' : 'blue'}>
                  {branch.status === 'COMPLETED'
                    ? '已反馈'
                    : (statusLabels[
                        transferCandidate
                          ? (pendingTransfer?.status ?? '')
                          : (assignment?.status ?? '')
                      ] ?? '办理中')}
                </Tag>
                {branch.result && (
                  <Tag color={outcomeColor(branch.result.outcomeCode)}>
                    {outcomeLabel(branch.result.outcomeCode)}
                  </Tag>
                )}
                {pendingTransfer && !transferCandidate && (
                  <Tag color="gold">{statusLabels[pendingTransfer.status ?? '']}</Tag>
                )}
              </span>
            </span>
            <span className={styles.branchTakeaway}>
              <span className={styles.branchTakeawayLabel}>
                {branch.result ? '正式结论' : '当前情况'}
              </span>
              <span className={styles.branchTakeawayText}>
                {branch.result?.conclusion ??
                  (transferCandidate
                    ? '收到交接申请；批准生效前责任仍由原承办单位承担。'
                    : pendingTransfer
                      ? `交接申请${statusLabels[pendingTransfer.status ?? ''] ?? '处理中'}；批准前仍由${currentUnit}负责。`
                      : `${currentUnit}${assignment?.status === 'PENDING_ACCEPT' ? '待承接' : '正在办理'}。`)}
              </span>
            </span>
            <span className={styles.branchFoot}>
              <span>
                {transferCandidate
                  ? `申请 ${dateTime(pendingTransfer?.requestedAt)}`
                  : branch.result?.submittedAt
                    ? `反馈 ${dateTime(branch.result.submittedAt)}`
                    : `本段截止 ${dateTime(assignment?.dueAt)}`}
                {!!children.length && ` · 下级分支 ${children.length} 条`}
                {(branch.assignments?.length ?? 0) > 1 &&
                  ` · 历经 ${branch.assignments?.length} 段责任`}
              </span>
              <span className={styles.expandClosed}>查看完整结果与经过</span>
              <span className={styles.expandOpen}>收起详情</span>
            </span>
          </summary>
          <div className={styles.branchBody}>
            {branch.result ? (
              <section className={styles.result} aria-label="正式结果">
                <div className={styles.resultHeading}>
                  <h3>正式结果</h3>
                  <Tag color={outcomeColor(branch.result.outcomeCode)}>
                    {outcomeLabel(branch.result.outcomeCode)}
                  </Tag>
                </div>
                <div className={styles.detailRow}>
                  <span>结论与后续</span>
                  <p>{branch.result.conclusion}</p>
                </div>
                <div className={styles.detailRow}>
                  <span>已做工作与依据</span>
                  <p>{branch.result.handlingDetail}</p>
                </div>
                {branch.result.suggestedUnitName && (
                  <div className={styles.detailRow}>
                    <span>建议后续单位</span>
                    <p>{branch.result.suggestedUnitName}</p>
                  </div>
                )}
                <div className={styles.resultFoot}>
                  <span>
                    由{currentUnit}于 {dateTime(branch.result.submittedAt)} 反馈
                  </span>
                  {branch.result.id && canCreateFollowup && (
                    <Button
                      type="link"
                      onClick={() => onAction('create', undefined, undefined, branch.result?.id)}
                    >
                      基于此结果发后续任务
                    </Button>
                  )}
                </div>
              </section>
            ) : (
              <section className={styles.openResult}>
                <h3>尚未提交正式结果</h3>
                {transferCandidate ? (
                  <p>
                    {pendingTransfer?.status === 'AWAITING_ISSUER'
                      ? '本单位已同意承接，等待总队决定。批准生效前，原承办单位仍负责此分支。'
                      : '请确认是否接收交接。批准生效前，原承办单位仍负责此分支。'}
                  </p>
                ) : (
                  <>
                    <p>当前责任单位：{currentUnit}</p>
                    <p>本段截止：{dateTime(assignment?.dueAt)}</p>
                  </>
                )}
              </section>
            )}
            {hasBranchRequirements &&
              (branch.status === 'OPEN' ? (
                <section className={styles.activeRequirements}>
                  <h3>本分支要求</h3>
                  {requirements}
                </section>
              ) : (
                <details className={styles.detailFold}>
                  <summary>查看本分支要求</summary>
                  {requirements}
                </details>
              ))}
            {(!!branch.assignments?.length || !!branch.transfers?.length) && (
              <details className={styles.detailFold}>
                <summary>
                  查看责任、期限与交接记录（{branch.assignments?.length ?? 0} 段承办
                  {!!branch.transfers?.length && ` · ${branch.transfers.length} 次交接申请`}）
                </summary>
                {!!branch.assignments?.length && (
                  <section className={styles.processSection}>
                    <h4>历次责任与期限</h4>
                    <ol className={styles.assignmentList}>
                      {branch.assignments.map((item, index) => (
                        <li key={item.id}>
                          <span className={styles.assignmentStep}>{index + 1}</span>
                          <div>
                            <strong>{item.toUnitName}</strong>
                            <Tag>{statusLabels[item.status ?? ''] ?? '办理中'}</Tag>
                            <div className={styles.assignmentMeta}>
                              <span>截止 {dateTime(item.dueAt)}</span>
                              <span>
                                {item.acceptedAt ? `承接 ${dateTime(item.acceptedAt)}` : '尚未承接'}
                              </span>
                            </div>
                            {item.endReason && <p>责任结束说明：{item.endReason}</p>}
                          </div>
                        </li>
                      ))}
                    </ol>
                  </section>
                )}
                {(branch.transfers ?? []).map((transfer) => (
                  <section className={styles.processSection} key={transfer.id}>
                    <h4>
                      交接申请：{transfer.targetUnitName ?? '目标支队'} ·{' '}
                      {statusLabels[transfer.status ?? ''] ?? '处理中'}
                    </h4>
                    <div className={styles.detailRow}>
                      <span>交接原因</span>
                      <p>{transfer.reason}</p>
                    </div>
                    <div className={styles.detailRow}>
                      <span>已做工作</span>
                      <p>{transfer.workDone}</p>
                    </div>
                    <div className={styles.detailRow}>
                      <span>依据</span>
                      <p>{transfer.evidenceSummary}</p>
                    </div>
                    <div className={styles.detailRow}>
                      <span>剩余事项</span>
                      <p>{transfer.remainingWork}</p>
                    </div>
                    <div className={styles.transferTimes}>
                      <span>申请 {dateTime(transfer.requestedAt)}</span>
                      {transfer.targetRespondedAt && (
                        <span>对方回应 {dateTime(transfer.targetRespondedAt)}</span>
                      )}
                      {transfer.issuerDecidedAt && (
                        <span>总队决定 {dateTime(transfer.issuerDecidedAt)}</span>
                      )}
                    </div>
                    {transfer.requiredDurationMinutes && (
                      <p>对方要求交接生效后至少办理 {duration(transfer.requiredDurationMinutes)}</p>
                    )}
                    {transfer.approvedDueAt && (
                      <p>批准的新期限 {dateTime(transfer.approvedDueAt)}</p>
                    )}
                    {transfer.targetResponseReason && (
                      <p>对方说明：{transfer.targetResponseReason}</p>
                    )}
                    {transfer.issuerDecisionReason && (
                      <p>总队说明：{transfer.issuerDecisionReason}</p>
                    )}
                  </section>
                ))}
              </details>
            )}
          </div>
        </details>
        {!!children.length && <div className={styles.children}>{children.map(renderBranch)}</div>}
      </div>
    );
  }

  return (
    <div className={styles.page}>
      <Button type="link" icon={<ArrowLeftOutlined />} className={styles.back} onClick={onBack}>
        返回任务列表
      </Button>
      <PageHeader
        demo={false}
        title={task.title ?? '任务详情'}
        description={task.taskNo}
        actions={
          <Tag color={task.status === 'COMPLETED' ? 'default' : 'blue'}>
            {task.status === 'COMPLETED' ? '流程已办结' : '办理中'}
          </Tag>
        }
      />
      <div className={styles.content}>
        <section className={styles.overview} aria-label="任务概况">
          <div>
            <span>发起单位</span>
            <strong>{task.issuerUnitName}</strong>
          </div>
          <div>
            <span>原定期限</span>
            <strong>{dateTime(task.initialDueAt)}</strong>
          </div>
          <div>
            <span>当前期限</span>
            <strong>{dateTime(task.currentDueAt)}</strong>
          </div>
          {task.status === 'COMPLETED' && (
            <p className={styles.completionNote}>
              流程已办结表示各分支已提交正式结果；是否达成目标，请看下方各分支的结果类型与结论。
            </p>
          )}
        </section>

        {!!todos.length && (
          <section className={styles.section} aria-labelledby="task-todo-title">
            <h2 id="task-todo-title">我单位待办</h2>
            <div className={styles.todoList}>
              {todos.map((todo) => (
                <div className={styles.todo} key={todo.key}>
                  <div>
                    <strong>{todo.title}</strong>
                    <p>{todo.description}</p>
                  </div>
                  <Space wrap>{todo.buttons}</Space>
                </div>
              ))}
            </div>
          </section>
        )}

        <section className={styles.section} aria-labelledby="task-requirement-title">
          <h2 id="task-requirement-title">任务要求</h2>
          <div className={styles.requirements}>
            <div>
              <span>要做什么</span>
              <p>{task.instruction}</p>
            </div>
            <div>
              <span>需要交付</span>
              <p>{task.expectedResult}</p>
            </div>
            {task.sourceResultId && (
              <p className={styles.source}>此任务根据另一项任务的处置结果发起。</p>
            )}
          </div>
        </section>

        <section className={styles.section} aria-labelledby="task-units-title">
          <h2 id="task-units-title">各单位办理情况</h2>
          <p className={styles.sectionHint}>
            每条分支独立反馈结果；单位交接只更换承办单位，原责任和期限仍可展开查看。
          </p>
          <div className={styles.branchList}>{roots.map(renderBranch)}</div>
        </section>

        <details className={styles.history}>
          <summary>查看完整办理记录（{task.actions?.length ?? 0} 条）</summary>
          <ol>
            {(task.actions ?? []).map((action, index) => (
              <li key={`${action.occurredAt}-${index}`}>
                <time>{dateTime(action.occurredAt)}</time>
                <span>{actionSentence(action, outcomeLabel)}</span>
                {action.note && action.code !== 'RESULT' && action.code !== 'CREATE' && (
                  <p>{action.note}</p>
                )}
              </li>
            ))}
          </ol>
        </details>
      </div>
    </div>
  );
}
