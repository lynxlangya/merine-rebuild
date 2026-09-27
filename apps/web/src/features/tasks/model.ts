import type { TaskAllowedAction, TaskBranch, TaskDetail, TaskTransfer } from '@merine/api-contract';

export type TaskAssignment = NonNullable<TaskBranch['assignments']>[number];
export type TaskAction = NonNullable<TaskDetail['actions']>[number];

/** 标签色调，对应 DESIGN 3.3 的成功、警示、失败、中性与一般提示。 */
export type TaskTone = 'neutral' | 'accent' | 'success' | 'warning' | 'danger';

/** 带色调的标签：含义由文字表达，色调只作辅助。 */
export interface TaskLabel {
  label: string;
  tone: TaskTone;
}

/** 结果类型的显示名来自字典；这里只接收解析函数，模型不依赖查询层。 */
export type OutcomeLabel = (code?: string) => string;

/** 打开办理弹窗：动作、所在分支、相关交接申请，以及发后续任务时的来源结果。 */
export type TaskActionHandler = (
  action: string,
  branch?: TaskBranch,
  transfer?: TaskTransfer,
  sourceResultId?: string,
) => void;

/** 状态与退回原因标签由页面字典解析；纯模型只接收函数。 */
export interface TaskLabels {
  order: OutcomeLabel;
  branch: OutcomeLabel;
  assignment: OutcomeLabel;
  transfer: OutcomeLabel;
  returnReason: OutcomeLabel;
}

export function taskStatusTag(status: string | undefined, label: OutcomeLabel): TaskLabel {
  return {
    label: label(status),
    tone: status === 'OPEN' ? 'accent' : status === 'AWAITING_CLOSE' ? 'warning' : 'neutral',
  };
}

/** 交接申请：待处理用警示色，已批准用成功色，未批准、拒绝与撤回是结束的中性状态。 */
export function transferTone(status?: string): TaskTone {
  switch (status) {
    case 'AWAITING_TARGET':
    case 'AWAITING_ISSUER':
      return 'warning';
    case 'APPROVED':
      return 'success';
    default:
      return 'neutral';
  }
}

/** 结果类型的色调按取值决定，不写进字典数据。 */
export function outcomeTone(code?: string): TaskTone {
  switch (code) {
    case 'FULFILLED':
      return 'success';
    case 'PARTIAL':
    case 'UNABLE_TO_VERIFY':
      return 'warning';
    case 'OUT_OF_JURISDICTION':
      return 'accent';
    default:
      return 'neutral';
  }
}

/** 汇总里结果类型的排列顺序；字典之外的取值排在最后。 */
const OUTCOME_ORDER = [
  'FULFILLED',
  'NOT_FOUND',
  'PARTIAL',
  'UNABLE_TO_VERIFY',
  'OUT_OF_JURISDICTION',
];

/** 交接时长：整天显示天，整小时显示小时，其余按分钟。 */
export function formatDuration(minutes?: number): string {
  if (!minutes) return '—';
  if (minutes % 1440 === 0) return `${minutes / 1440} 天`;
  if (minutes % 60 === 0) return `${minutes / 60} 小时`;
  return `${minutes} 分钟`;
}

export function currentAssignment(branch: TaskBranch): TaskAssignment | undefined {
  return (
    branch.assignments?.find((assignment) => assignment.id === branch.currentAssignmentId) ??
    branch.assignments?.at(-1)
  );
}

/** 分支上仍待处理的交接申请；后端保证同一分支同时最多一条。 */
export function pendingTransfer(branch: TaskBranch): TaskTransfer | undefined {
  return branch.transfers?.find(
    (transfer) => transfer.status === 'AWAITING_TARGET' || transfer.status === 'AWAITING_ISSUER',
  );
}

/** 交接目标方在批准前只拿到申请本身：看不到责任段，只有指向本单位的待处理申请。 */
export function isTransferCandidate(
  branch: TaskBranch,
  transfer: TaskTransfer | undefined,
): boolean {
  return !branch.assignments?.length && !!transfer?.targetMine;
}

export type BranchTiming = 'on-time' | 'late' | 'overdue';

export interface BranchRow {
  key: string;
  branch: TaskBranch;
  /** 承办单位：退回不改变承办单位；交接与退回后重新派发会换成新单位。 */
  unitName: string;
  /** 责任变化的一句话说明；一直由同一单位办理时为空。 */
  route?: string;
  tag: TaskLabel;
  summary: string;
  /** 没有实质内容的占位说明用次要文字显示。 */
  summaryMuted: boolean;
  dueAt?: string;
  answeredAt?: string;
  timing?: BranchTiming;
  children?: BranchRow[];
}

function branchUnit(branch: TaskBranch, transfer: TaskTransfer | undefined): string {
  const segments = branch.assignments ?? [];
  if (!segments.length) return transfer?.targetUnitName ?? '待确认承办单位';
  const holder = segments.filter((segment) => segment.sourceAction !== 'RETURN').at(-1);
  return (holder ?? segments[0]).toUnitName ?? '—';
}

function branchRoute(
  branch: TaskBranch,
  transfer: TaskTransfer | undefined,
  labels: TaskLabels,
): string | undefined {
  const segments = branch.assignments ?? [];
  const pieces: string[] = [];
  segments.forEach((segment, index) => {
    if (segment.sourceAction === 'PEER_TRANSFER') {
      pieces.push(`自${segment.fromUnitName ?? '原承办单位'}交接`);
    } else if (segment.sourceAction === 'REASSIGN') {
      pieces.push(`${segments[index - 1]?.fromUnitName ?? '原承办单位'}退回后重新派发`);
    } else if (segment.sourceAction === 'RETURN' && index === segments.length - 1) {
      const to = segment.toUnitName ?? '发送单位';
      pieces.push(
        branch.status === 'COMPLETED'
          ? `退回后由${to}提交结果`
          : branch.status === 'RECALLED'
            ? `退回后由${to}撤回`
            : `已退回${to}，待处理`,
      );
    }
  });
  if (transfer && segments.length) {
    pieces.push(
      `申请交接给${transfer.targetUnitName ?? '目标支队'} · ${labels.transfer(transfer.status)}`,
    );
  }
  return pieces.length ? pieces.join(' · ') : undefined;
}

function branchTag(
  branch: TaskBranch,
  transfer: TaskTransfer | undefined,
  outcomeLabel: OutcomeLabel,
  labels: TaskLabels,
): TaskLabel {
  if (branch.result) {
    return {
      label: outcomeLabel(branch.result.outcomeCode),
      tone: outcomeTone(branch.result.outcomeCode),
    };
  }
  if (isTransferCandidate(branch, transfer)) {
    return { label: labels.transfer(transfer?.status), tone: 'warning' };
  }
  if (branch.status === 'COMPLETED' || branch.status === 'RECALLED') {
    return { label: labels.branch(branch.status), tone: 'neutral' };
  }
  const segment = currentAssignment(branch);
  if (segment?.sourceAction === 'RETURN') {
    return { label: labels.assignment('RETURNED'), tone: 'warning' };
  }
  return { label: labels.assignment(segment?.status), tone: 'accent' };
}

/** 进展与退回说明取较新的一条；不是承办单位本身写的，前面带上单位名。 */
function latestNote(
  branch: TaskBranch,
  actions: TaskAction[],
  unitName: string,
): string | undefined {
  const latest = actions
    .filter(
      (action) =>
        action.branchId === branch.id &&
        (action.code === 'PROGRESS' || action.code === 'RETURN' || action.code === 'RECALL') &&
        !!action.note,
    )
    .at(-1);
  if (branch.status === 'RECALLED')
    return `撤回原因：${currentAssignment(branch)?.endReason ?? latest?.note ?? '—'}`;
  if (!latest) return undefined;
  const own = latest.actorUnitName === unitName;
  if (latest.code === 'RETURN') {
    return own ? `退回原因：${latest.note}` : `${latest.actorUnitName}退回：${latest.note}`;
  }
  return own ? latest.note : `${latest.actorUnitName}：${latest.note}`;
}

function branchTiming(
  branch: TaskBranch,
  dueAt: string | undefined,
  now: number,
): BranchTiming | undefined {
  if (!dueAt) return undefined;
  const due = Date.parse(dueAt);
  const endedAt =
    branch.result?.submittedAt ??
    (branch.status === 'RECALLED' ? currentAssignment(branch)?.endedAt : undefined);
  if (endedAt) return Date.parse(endedAt) <= due ? 'on-time' : 'late';
  return branch.status === 'OPEN' && now > due ? 'overdue' : undefined;
}

/**
 * 各单位办理情况的树表数据。根节点是本单位能看到的最上层分支
 *（上级分支不可见时，下级分支也作为根）；顺序保持接口返回的顺序。
 */
export function branchRows(
  task: TaskDetail,
  outcomeLabel: OutcomeLabel,
  labels: TaskLabels,
  now = Date.now(),
): BranchRow[] {
  const branches = task.branches ?? [];
  const actions = task.actions ?? [];
  const visibleIds = new Set(branches.map((branch) => branch.id));

  function toRow(branch: TaskBranch): BranchRow {
    const transfer = pendingTransfer(branch);
    const unitName = branchUnit(branch, transfer);
    const note = latestNote(branch, actions, unitName);
    const summary = branch.result?.conclusion
      ? { summary: branch.result.conclusion, summaryMuted: false }
      : isTransferCandidate(branch, transfer)
        ? { summary: '收到交接申请；批准前仍由原承办单位负责', summaryMuted: true }
        : note
          ? { summary: note, summaryMuted: false }
          : { summary: '尚无进展记录', summaryMuted: true };
    const dueAt = currentAssignment(branch)?.dueAt;
    const children = branches.filter((child) => child.parentBranchId === branch.id).map(toRow);
    return {
      key: branch.id ?? '',
      branch,
      unitName,
      route: branchRoute(branch, transfer, labels),
      tag: branchTag(branch, transfer, outcomeLabel, labels),
      ...summary,
      dueAt,
      answeredAt:
        branch.result?.submittedAt ??
        (branch.status === 'RECALLED' ? currentAssignment(branch)?.endedAt : undefined),
      timing: branchTiming(branch, dueAt, now),
      ...(children.length ? { children } : {}),
    };
  }

  return branches
    .filter((branch) => !branch.parentBranchId || !visibleIds.has(branch.parentBranchId))
    .map(toRow);
}

/** 带下级的行；树表默认全部展开，由此得到展开键。 */
export function parentRowKeys(rows: BranchRow[]): string[] {
  return rows.flatMap((row) => (row.children ? [row.key, ...parentRowKeys(row.children)] : []));
}

export function findRow(rows: BranchRow[], key: string): BranchRow | undefined {
  for (const row of rows) {
    if (row.key === key) return row;
    const found = row.children && findRow(row.children, key);
    if (found) return found;
  }
  return undefined;
}

export interface TaskSummary {
  total: number;
  answered: number;
  recalled: number;
  onTime: number;
  outcomes: { code: string; label: string; tone: TaskTone; count: number }[];
  /** 已答复或撤回，但晚于期限。 */
  late: number;
  /** 仍未答复且已过期限。 */
  overdue: number;
}

/** 只统计根分支：它们直接向发起单位答复，下级分支的结果已汇入上级结论。 */
export function taskSummary(rows: BranchRow[]): TaskSummary {
  const outcomes = new Map<string, TaskSummary['outcomes'][number]>();
  for (const row of rows) {
    const code = row.branch.result?.outcomeCode;
    if (!code) continue;
    const entry = outcomes.get(code);
    if (entry) entry.count += 1;
    else outcomes.set(code, { code, label: row.tag.label, tone: row.tag.tone, count: 1 });
  }
  const rank = (code: string) => {
    const index = OUTCOME_ORDER.indexOf(code);
    return index < 0 ? OUTCOME_ORDER.length : index;
  };
  return {
    total: rows.length,
    answered: rows.filter((row) => row.branch.status === 'COMPLETED').length,
    recalled: rows.filter((row) => row.branch.status === 'RECALLED').length,
    onTime: rows.filter((row) => row.timing === 'on-time').length,
    outcomes: [...outcomes.values()].sort((left, right) => rank(left.code) - rank(right.code)),
    late: rows.filter((row) => row.timing === 'late').length,
    overdue: rows.filter((row) => row.timing === 'overdue').length,
  };
}

/** 带时间的说明文字：时间单独成段，界面按 TaskTime 显示，嵌在句中也不会被折开。 */
export type TaskTextPart = string | { time?: string };

export interface HistoryEntry {
  key: string;
  code: string;
  at?: string;
  branchId?: string;
  actor: string;
  actorUserName?: string;
  target?: string;
  sentence: TaskTextPart[];
  note?: string;
}

function historySentence(
  action: TaskAction,
  outcomeLabel: OutcomeLabel,
  returnReasonLabel: OutcomeLabel,
  extension?: TaskAction,
): TaskTextPart[] {
  const actor = action.actorUnitName ?? '有关单位';
  const target = action.targetUnitName ?? '有关单位';
  const due = { time: action.newDueAt };
  switch (action.code) {
    case 'CREATE':
      return [`${actor}发起任务`];
    case 'DISPATCH':
      return [`${actor}下发给${target}，期限 `, due];
    case 'ACCEPT':
      return [`${actor}承接任务`];
    case 'PROGRESS':
      return [`${actor}记录进展`];
    case 'RETURN':
      return [
        `${actor}退回给${target}${action.reasonCode ? `：${returnReasonLabel(action.reasonCode)}` : ''}`,
      ];
    case 'RECALL':
      return [`${actor}撤回此分支`];
    case 'CLOSE':
      return [`${actor}办结任务`];
    case 'REASSIGN':
      return [`${actor}重新派发给${target}，期限 `, due];
    case 'RESULT':
      return [`${actor}提交结果：${outcomeLabel(action.note)}`];
    case 'TRANSFER_REQUEST':
      return [`${actor}申请交接给${target}`];
    case 'TRANSFER_ACCEPT':
      return [`${actor}同意承接交接`];
    case 'TRANSFER_DECLINE':
      return [`${actor}拒绝承接交接`];
    case 'TRANSFER_APPROVE':
      return extension
        ? [
            `${actor}批准交接给${target}，期限 `,
            due,
            '；任务期限由 ',
            { time: extension.oldDueAt },
            ' 延至 ',
            { time: extension.newDueAt },
          ]
        : [`${actor}批准交接给${target}，期限 `, due];
    case 'TRANSFER_REJECT':
      return [`${actor}未批准交接`];
    case 'TRANSFER_WITHDRAW':
      return [`${actor}撤回交接申请`];
    case 'EXTEND_DUE':
      return [`${actor}将任务期限调整至 `, due];
    default:
      return [`${actor}办理了任务`];
  }
}

/**
 * 办理记录：按时间排列，同一时刻的“发起任务”排在它带出的下发之前；
 * 批准交接时顺带延长整单期限的记录并入那条批准，不重复出现。
 */
export function historyEntries(
  actions: TaskAction[],
  outcomeLabel: OutcomeLabel,
  returnReasonLabel: OutcomeLabel,
): HistoryEntry[] {
  const at = (action: TaskAction) => Date.parse(action.occurredAt ?? '') || 0;
  const ordered = actions
    .map((action, index) => ({ action, index }))
    .sort(
      (left, right) =>
        at(left.action) - at(right.action) ||
        Number(right.action.code === 'CREATE') - Number(left.action.code === 'CREATE') ||
        left.index - right.index,
    );
  // 后端在同一事务、同一时刻写下 EXTEND_DUE 与 TRANSFER_APPROVE，按时刻配对。
  const approvedAt = new Set(
    actions
      .filter((action) => action.code === 'TRANSFER_APPROVE')
      .map((action) => action.occurredAt),
  );
  const extensions = new Map<string, TaskAction>();
  for (const action of actions) {
    if (action.code === 'EXTEND_DUE' && action.occurredAt && approvedAt.has(action.occurredAt)) {
      extensions.set(action.occurredAt, action);
    }
  }
  return ordered
    .filter(
      ({ action }) => !(action.code === 'EXTEND_DUE' && extensions.has(action.occurredAt ?? '')),
    )
    .map(({ action, index }) => {
      const hideNote = action.code === 'RESULT' || action.code === 'CREATE';
      const extension =
        action.code === 'TRANSFER_APPROVE' ? extensions.get(action.occurredAt ?? '') : undefined;
      return {
        key: `${action.occurredAt ?? ''}-${index}`,
        code: action.code ?? '',
        at: action.occurredAt,
        branchId: action.branchId,
        actor: action.actorUnitName ?? '有关单位',
        actorUserName: action.actorUserName,
        target: action.targetUnitName,
        sentence: historySentence(action, outcomeLabel, returnReasonLabel, extension),
        note: !hideNote && action.note ? action.note : undefined,
      };
    });
}

/** 分支抽屉里的办理经过：本分支自身的记录，加上从本分支往下派发的记录。 */
export function branchHistory(
  entries: HistoryEntry[],
  task: TaskDetail,
  branch: TaskBranch,
): HistoryEntry[] {
  const childIds = new Set(
    (task.branches ?? [])
      .filter((child) => child.parentBranchId === branch.id)
      .map((child) => child.id),
  );
  return entries.filter(
    (entry) =>
      entry.branchId === branch.id || (entry.code === 'DISPATCH' && childIds.has(entry.branchId)),
  );
}

export interface BranchCommand {
  key: string;
  action: string;
  label: string;
  primary?: boolean;
  transfer?: TaskTransfer;
  disabledReason?: string;
}

const COMMAND_LABELS: Record<string, string> = {
  accept: '承接任务',
  return: '说明原因并退回',
  progress: '记录进展',
  dispatch: '下发下级',
  reassign: '重新派发',
  results: '提交处置结果',
  'transfer-requests': '申请支队交接',
  withdraw: '撤回交接申请',
  respond: '确认或拒绝承接',
  decide: '审批交接',
  recall: '撤回分支',
  close: '办结任务',
};

/** 操作与禁用原因由后端下发；前端只负责文案、排序与主按钮表现。 */
export function branchCommands(_task: TaskDetail, branch: TaskBranch): BranchCommand[] {
  return commandsFor(branch.allowedActions ?? [], branch.transfers);
}

export function taskCommands(task: TaskDetail): BranchCommand[] {
  return commandsFor(task.allowedActions ?? []);
}

function commandsFor(allowed: TaskAllowedAction[], transfers?: TaskTransfer[]): BranchCommand[] {
  const primary = allowed.find(
    (item) =>
      item.enabled &&
      ['accept', 'results', 'respond', 'decide', 'reassign', 'close'].includes(item.code ?? ''),
  );
  return allowed.map((item) => ({
    key: item.code ?? '',
    action: item.code ?? '',
    label: COMMAND_LABELS[item.code ?? ''] ?? item.code ?? '—',
    primary: item === primary,
    transfer: item.transferId
      ? transfers?.find((transfer) => transfer.id === item.transferId)
      : undefined,
    disabledReason: item.enabled ? undefined : (item.reason ?? '当前不可操作'),
  }));
}

export interface TaskTodo {
  key: string;
  branch?: TaskBranch;
  title: string;
  description: TaskTextPart[];
  commands: BranchCommand[];
}

/** 我单位待办：承办、回应交接、审批交接三类，各自一行；标题用表格里的单位名，便于对上是哪一行。 */
export function taskTodos(task: TaskDetail): TaskTodo[] {
  const todos: TaskTodo[] = [];
  const closing = taskCommands(task).filter(
    (command) => command.action === 'close' && !command.disabledReason,
  );
  if (closing.length)
    todos.push({
      key: 'close',
      title: '请填写总体结论并办结',
      description: ['全部分支已答复或撤回。'],
      commands: closing,
    });
  for (const branch of task.branches ?? []) {
    const commands = branchCommands(task, branch);
    const holder = commands.filter(
      (command) => command.key !== 'respond' && command.key !== 'decide',
    );
    const assignment = currentAssignment(branch);
    const transfer = pendingTransfer(branch);
    const unitName = branchUnit(branch, transfer);
    if (holder.length) {
      // 下级退回后责任回到本单位，但表格里这一行仍是原承办单位，标题要说清是它退回的。
      const state =
        assignment?.sourceAction === 'RETURN'
          ? '已退回，待本单位处理'
          : assignment?.status === 'PENDING_ACCEPT'
            ? '请确认是否承接'
            : '正在办理';
      todos.push({
        key: `branch-${branch.id}`,
        branch,
        title: `${unitName}：${state}`,
        description: [
          `${transfer ? '交接生效前本单位仍负责 · ' : ''}截止 `,
          { time: assignment?.dueAt },
        ],
        commands: holder,
      });
    }
    for (const command of commands) {
      if (command.key === 'respond') {
        todos.push({
          key: `respond-${transfer?.id}`,
          branch,
          title: '请确认交接申请',
          description: [
            `${transfer?.fromUnitName ?? '原承办单位'}申请把此分支交接给本单位；同意后仍需总队批准。`,
          ],
          commands: [command],
        });
      }
      if (command.key === 'decide') {
        todos.push({
          key: `decide-${transfer?.id}`,
          branch,
          title: '请决定是否批准交接',
          description: [
            `${unitName}申请交接给${transfer?.targetUnitName ?? '目标支队'}；批准时须确定新期限。`,
          ],
          commands: [command],
        });
      }
    }
  }
  return todos;
}
