import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import type { TaskDetail } from '@merine/api-contract';
import {
  branchCommands,
  branchHistory,
  branchRows,
  findRow,
  formatDuration,
  historyEntries,
  parentRowKeys,
  taskSummary,
  taskTodos,
  taskStatusTag,
  type TaskLabels,
} from './model.ts';

/*
 * 夹具按真实接口 JSON 的形状书写：缺省字段是 null 而不是省略，
 * 与生成类型里的可选字段不完全一致，所以整体转成 TaskDetail。
 */
type Json = Record<string, unknown>;

const HQ = '浙江省公安厅海防总队';
const NB = '宁波市公安局海防管理支队';
const WZ = '温州市公安局治安管理支队';
const ZS = '舟山市公安局海防管理支队';
const TZ = '台州市公安局海防管理支队';
const YH = '玉环市公安局';
const HS = '海曙区公安分局';
const JB = '江北区公安分局';

const OUTCOMES: Record<string, string> = {
  FULFILLED: '目标达成',
  NOT_FOUND: '经核查未发现',
  PARTIAL: '部分完成',
  OUT_OF_JURISDICTION: '转出辖区',
  UNABLE_TO_VERIFY: '无法核实',
};
const outcomeLabel = (code?: string) => (code && OUTCOMES[code]) || code || '—';
const labelsFor = (values: Record<string, string>) => (code?: string) =>
  code ? (values[code] ?? code) : '—';
const labels: TaskLabels = {
  order: labelsFor({ OPEN: '办理中', AWAITING_CLOSE: '待办结', COMPLETED: '已办结' }),
  branch: labelsFor({ OPEN: '办理中', COMPLETED: '已答复', RECALLED: '已撤回' }),
  assignment: labelsFor({
    PENDING_ACCEPT: '待承接',
    IN_PROGRESS: '办理中',
    RETURNED: '已退回',
    COMPLETED: '已答复',
    RECALLED: '已撤回',
  }),
  transfer: labelsFor({
    AWAITING_TARGET: '待接收支队确认',
    AWAITING_ISSUER: '待总队审批',
    APPROVED: '已批准',
  }),
  returnReason: labelsFor({
    WRONG_TARGET: '派错单位',
    NOT_OUR_DUTY: '不属本单位职责',
    UNCLEAR_REQUIREMENT: '要求不明确',
  }),
};
const allowed = (code: string, reason?: string, transferId?: string) => ({
  code,
  enabled: !reason,
  reason,
  reasonCode: reason ? 'BLOCKED' : undefined,
  transferId,
});

function segment(
  id: string,
  branchId: string,
  from: string,
  to: string,
  sourceAction: string,
  status: string,
  dueAt: string,
): Json {
  return {
    id,
    branchId,
    fromUnitName: from,
    toUnitName: to,
    sourceAction,
    status,
    dueAt,
    acceptedAt: null,
    endedAt: null,
    endReason: null,
  };
}

function result(
  branchId: string,
  outcomeCode: string,
  conclusion: string,
  submittedAt: string,
): Json {
  return {
    id: `r${branchId}`,
    branchId,
    outcomeCode,
    handlingDetail: conclusion,
    conclusion,
    suggestedUnitCode: null,
    suggestedUnitName: null,
    submittedAt,
  };
}

function branch(
  id: string,
  parentBranchId: string | null,
  assignments: Json[],
  outcome: Json | null,
  extra: Json = {},
): Json {
  return {
    id,
    parentBranchId,
    instruction: '任务要求',
    expectedResult: '交付目标',
    status: outcome ? 'COMPLETED' : 'OPEN',
    currentAssignmentId: assignments.at(-1)?.id ?? null,
    currentMine: false,
    canDispatchDownward: true,
    canTransferPeer: false,
    completedAt: outcome?.submittedAt ?? null,
    assignments,
    result: outcome,
    transfers: [],
    ...extra,
  };
}

function action(
  code: string,
  branchId: string | null,
  actor: string,
  occurredAt: string,
  extra: Json = {},
): Json {
  return {
    code,
    branchId,
    actorUnitName: actor,
    targetUnitName: null,
    note: null,
    oldDueAt: null,
    newDueAt: null,
    occurredAt,
    ...extra,
  };
}

function task(branches: Json[], actions: Json[], extra: Json = {}): TaskDetail {
  return {
    id: '1',
    taskNo: 'TASK-DEMO',
    title: '演示任务',
    instruction: '任务要求',
    expectedResult: '交付目标',
    issuerUnitName: HQ,
    issuerMine: true,
    status: branches.every((item) => item.status === 'COMPLETED') ? 'COMPLETED' : 'OPEN',
    initialDueAt: DUE,
    currentDueAt: DUE,
    completedAt: null,
    sourceResultId: null,
    branches,
    actions,
    ...extra,
  } as unknown as TaskDetail;
}

const DUE = '2026-09-27T01:00:00Z';
const T5_CREATED = '2026-09-26T08:06:48.268707Z';

/** 任务 5（总队视角）：宁波直接答复；舟山退回、总队自己提交结果；台州下发玉环后汇总。 */
const task5 = task(
  [
    branch(
      '12',
      null,
      [segment('13', '12', HQ, NB, 'DOWNWARD', 'COMPLETED', DUE)],
      result('12', 'FULFILLED', '宁波目标达成反馈', '2026-09-26T08:07:56.929755Z'),
    ),
    branch(
      '13',
      null,
      [
        {
          ...segment('14', '13', HQ, ZS, 'DOWNWARD', 'RETURNED', DUE),
          endReason: '舟山无相关警情',
        },
        segment('16', '13', ZS, HQ, 'RETURN', 'COMPLETED', DUE),
      ],
      result('13', 'UNABLE_TO_VERIFY', '人已经出海，已发通缉令', '2026-09-26T11:14:59.114190Z'),
      { currentMine: true },
    ),
    branch(
      '14',
      null,
      [segment('15', '14', HQ, TZ, 'DOWNWARD', 'COMPLETED', DUE)],
      result(
        '14',
        'UNABLE_TO_VERIFY',
        '人已经出海，建议总队再派任务排查',
        '2026-09-26T11:13:04.252903Z',
      ),
    ),
    branch(
      '15',
      '14',
      [segment('17', '15', TZ, YH, 'DOWNWARD', 'COMPLETED', '2026-09-26T12:00:00.400Z')],
      result('15', 'UNABLE_TO_VERIFY', '人已出海', '2026-09-26T11:11:55.162013Z'),
      { instruction: '总队任务，玉环处置！' },
    ),
  ],
  [
    action('DISPATCH', '12', HQ, T5_CREATED, { targetUnitName: NB, newDueAt: DUE }),
    action('DISPATCH', '13', HQ, T5_CREATED, { targetUnitName: ZS, newDueAt: DUE }),
    action('DISPATCH', '14', HQ, T5_CREATED, { targetUnitName: TZ, newDueAt: DUE }),
    action('CREATE', null, HQ, T5_CREATED, { note: '创建并下发给 3 个直属单位', newDueAt: DUE }),
    action('ACCEPT', '12', NB, '2026-09-26T08:07:29.689759Z'),
    action('RESULT', '12', NB, '2026-09-26T08:07:56.929755Z', { note: 'FULFILLED' }),
    action('RETURN', '13', ZS, '2026-09-26T08:10:42.782032Z', {
      targetUnitName: HQ,
      note: '舟山无相关警情',
      oldDueAt: DUE,
      newDueAt: DUE,
    }),
    action('ACCEPT', '14', TZ, '2026-09-26T11:07:58.641869Z'),
    action('DISPATCH', '15', TZ, '2026-09-26T11:08:55.132186Z', {
      targetUnitName: YH,
      newDueAt: '2026-09-26T12:00:00.400Z',
    }),
    action('ACCEPT', '15', YH, '2026-09-26T11:10:41.053780Z'),
    action('PROGRESS', '15', YH, '2026-09-26T11:10:59.078040Z', { note: '出发了，开始排查' }),
    action('RESULT', '15', YH, '2026-09-26T11:11:55.162013Z', { note: 'UNABLE_TO_VERIFY' }),
    action('RESULT', '14', TZ, '2026-09-26T11:13:04.252903Z', { note: 'UNABLE_TO_VERIFY' }),
    action('RESULT', '13', HQ, '2026-09-26T11:14:59.114190Z', { note: 'UNABLE_TO_VERIFY' }),
  ],
  { completedAt: '2026-09-26T11:14:59.114190Z' },
);

const T3_DUE = '2026-09-30T10:00:00Z';
const T3_EXTENDED = '2026-10-03T10:00:00Z';
const T3_CREATED = '2026-09-24T14:30:22.300858Z';
const T3_APPROVED = '2026-09-24T14:44:15.151525Z';
const T3_DECISION = '舟山支队确认至少需要 2880 分钟，批准交接并设置独立新期限。';

/** 任务 3（总队视角）：温州经总队批准交接给舟山，整单期限随之延长。 */
const task3 = task(
  [
    branch(
      '7',
      null,
      [segment('7', '7', HQ, NB, 'DOWNWARD', 'COMPLETED', T3_DUE)],
      result('7', 'FULFILLED', '宁波辖区已核明', '2026-09-24T14:41:04.483079Z'),
    ),
    branch(
      '8',
      null,
      [
        segment('8', '8', HQ, WZ, 'DOWNWARD', 'TRANSFERRED', T3_DUE),
        segment('12', '8', WZ, ZS, 'PEER_TRANSFER', 'COMPLETED', T3_EXTENDED),
      ],
      result('8', 'PARTIAL', '第三名人员材料仍需补充核验', '2026-09-24T14:45:10.945689Z'),
      {
        transfers: [
          {
            id: '1',
            branchId: '8',
            targetUnitName: ZS,
            status: 'APPROVED',
            requiredDurationMinutes: 2880,
            targetResponseReason: '',
            targetMine: false,
          },
        ],
      },
    ),
    branch(
      '9',
      '7',
      [segment('9', '9', NB, HS, 'DOWNWARD', 'COMPLETED', '2026-09-29T10:00:00Z')],
      result('9', 'OUT_OF_JURISDICTION', '线索已进入江北辖区', '2026-09-24T14:38:49.944602Z'),
    ),
    branch(
      '10',
      '7',
      [segment('10', '10', NB, JB, 'DOWNWARD', 'COMPLETED', '2026-09-29T10:00:00Z')],
      result('10', 'FULFILLED', '停靠位置已核实', '2026-09-24T14:39:17.928595Z'),
    ),
  ],
  [
    action('DISPATCH', '7', HQ, T3_CREATED, { targetUnitName: NB, newDueAt: T3_DUE }),
    action('DISPATCH', '8', HQ, T3_CREATED, { targetUnitName: WZ, newDueAt: T3_DUE }),
    action('CREATE', null, HQ, T3_CREATED, { note: '创建并下发给 2 个直属单位', newDueAt: T3_DUE }),
    action('ACCEPT', '7', NB, '2026-09-24T14:31:41.510408Z'),
    action('DISPATCH', '9', NB, '2026-09-24T14:32:12.065738Z', {
      targetUnitName: HS,
      newDueAt: '2026-09-29T10:00:00Z',
    }),
    action('DISPATCH', '10', NB, '2026-09-24T14:32:12.065738Z', {
      targetUnitName: JB,
      newDueAt: '2026-09-29T10:00:00Z',
    }),
    action('ACCEPT', '9', HS, '2026-09-24T14:38:17.198861Z'),
    action('PROGRESS', '9', HS, '2026-09-24T14:38:28.204782Z', { note: '未发现目标船舶' }),
    action('RESULT', '9', HS, '2026-09-24T14:38:49.944602Z', { note: 'OUT_OF_JURISDICTION' }),
    action('ACCEPT', '10', JB, '2026-09-24T14:39:03.079152Z'),
    action('RESULT', '10', JB, '2026-09-24T14:39:17.928595Z', { note: 'FULFILLED' }),
    action('RESULT', '7', NB, '2026-09-24T14:41:04.483079Z', { note: 'FULFILLED' }),
    action('ACCEPT', '8', WZ, '2026-09-24T14:41:42.044593Z'),
    action('PROGRESS', '8', WZ, '2026-09-24T14:41:50.925186Z', { note: '目标转向舟山海域' }),
    action('TRANSFER_REQUEST', '8', WZ, '2026-09-24T14:42:14.136322Z', {
      targetUnitName: ZS,
      note: '目标船舶已驶入舟山辖区',
      oldDueAt: T3_DUE,
    }),
    action('TRANSFER_ACCEPT', '8', ZS, '2026-09-24T14:43:05.206022Z', { note: '' }),
    action('EXTEND_DUE', null, HQ, T3_APPROVED, {
      note: T3_DECISION,
      oldDueAt: T3_DUE,
      newDueAt: T3_EXTENDED,
    }),
    action('TRANSFER_APPROVE', '8', HQ, T3_APPROVED, {
      targetUnitName: ZS,
      note: T3_DECISION,
      oldDueAt: T3_DUE,
      newDueAt: T3_EXTENDED,
    }),
    action('PROGRESS', '8', ZS, '2026-09-24T14:44:52.547247Z', {
      note: '目标出现在 Z-02 锚地附近',
    }),
    action('RESULT', '8', ZS, '2026-09-24T14:45:10.945689Z', { note: 'PARTIAL' }),
  ],
  { initialDueAt: T3_DUE, currentDueAt: T3_EXTENDED },
);

const NOW = Date.parse('2026-09-26T12:00:00Z');

describe('各单位办理情况', () => {
  it('树表以可见的最上层分支为根，没有下级时不带 children', () => {
    const rows = branchRows(task5, outcomeLabel, labels, NOW);
    assert.deepEqual(
      rows.map((row) => row.key),
      ['12', '13', '14'],
    );
    assert.equal('children' in rows[0], false);
    assert.deepEqual(
      rows[2].children?.map((row) => [row.unitName, row.tag.label]),
      [[YH, '无法核实']],
    );
    assert.deepEqual(parentRowKeys(rows), ['14']);
    assert.equal(findRow(rows, '15')?.unitName, YH);
  });

  it('退回后由发送单位提交结果时，仍按原承办单位列出并写明经过', () => {
    const row = branchRows(task5, outcomeLabel, labels, NOW)[1];
    assert.equal(row.unitName, ZS);
    assert.equal(row.route, `退回后由${HQ}提交结果`);
    assert.deepEqual(row.tag, { label: '无法核实', tone: 'warning' });
    assert.equal(row.summary, '人已经出海，已发通缉令');
    assert.equal(row.timing, 'on-time');
  });

  it('支队交接后按接手单位列出，写明从哪个单位交接而来', () => {
    const row = branchRows(task3, outcomeLabel, labels, NOW)[1];
    assert.equal(row.unitName, ZS);
    assert.equal(row.route, `自${WZ}交接`);
    assert.deepEqual(row.tag, { label: '部分完成', tone: 'warning' });
    assert.equal(row.dueAt, T3_EXTENDED);
  });

  it('汇总只数根分支，结果类型按固定顺序计数', () => {
    assert.deepEqual(taskSummary(branchRows(task5, outcomeLabel, labels, NOW)), {
      total: 3,
      answered: 3,
      recalled: 0,
      onTime: 3,
      outcomes: [
        { code: 'FULFILLED', label: '目标达成', tone: 'success', count: 1 },
        { code: 'UNABLE_TO_VERIFY', label: '无法核实', tone: 'warning', count: 2 },
      ],
      late: 0,
      overdue: 0,
    });
  });

  it('晚于期限的答复与过期未答复分别计数', () => {
    const late = branch(
      '41',
      null,
      [segment('51', '41', HQ, NB, 'DOWNWARD', 'COMPLETED', DUE)],
      result('41', 'FULFILLED', '已核明', '2026-09-27T02:00:00Z'),
    );
    const overdue = branch(
      '42',
      null,
      [segment('52', '42', HQ, TZ, 'DOWNWARD', 'IN_PROGRESS', DUE)],
      null,
    );
    const open = branch(
      '43',
      null,
      [segment('53', '43', HQ, ZS, 'DOWNWARD', 'PENDING_ACCEPT', '2026-09-30T10:00:00Z')],
      null,
    );
    const rows = branchRows(
      task([late, overdue, open], []),
      outcomeLabel,
      labels,
      Date.parse('2026-09-28T00:00:00Z'),
    );
    assert.deepEqual(
      rows.map((row) => row.timing),
      ['late', 'overdue', undefined],
    );
    const summary = taskSummary(rows);
    assert.equal(summary.answered, 1);
    assert.equal(summary.late, 1);
    assert.equal(summary.overdue, 1);
    assert.deepEqual(
      rows.slice(1).map((row) => [row.tag, row.summary, row.summaryMuted]),
      [
        [{ label: '办理中', tone: 'accent' }, '尚无进展记录', true],
        [{ label: '待承接', tone: 'accent' }, '尚无进展记录', true],
      ],
    );
  });

  it('退回未处理时标为已退回，并显示退回原因', () => {
    const returned = task(
      [
        branch(
          '21',
          null,
          [
            segment('31', '21', HQ, ZS, 'DOWNWARD', 'RETURNED', DUE),
            segment('32', '21', ZS, HQ, 'RETURN', 'IN_PROGRESS', DUE),
          ],
          null,
          { currentMine: true },
        ),
      ],
      [
        action('RETURN', '21', ZS, '2026-09-26T08:10:42Z', {
          targetUnitName: HQ,
          note: '舟山无相关警情',
        }),
      ],
    );
    const [row] = branchRows(returned, outcomeLabel, labels, NOW);
    assert.equal(row.unitName, ZS);
    assert.equal(row.route, `已退回${HQ}，待处理`);
    assert.deepEqual(row.tag, { label: '已退回', tone: 'warning' });
    assert.equal(row.summary, '退回原因：舟山无相关警情');
    assert.deepEqual(
      branchCommands(returned, {
        ...returned.branches![0],
        allowedActions: [allowed('progress'), allowed('reassign'), allowed('recall')],
      }).map((command) => [command.action, command.label]),
      [
        ['progress', '记录进展'],
        ['reassign', '重新派发'],
        ['recall', '撤回分支'],
      ],
    );
  });

  it('退回后重新派发时换成新承办单位，别人写的说明带上单位名', () => {
    const reassigned = task(
      [
        branch(
          '22',
          null,
          [
            segment('33', '22', HQ, ZS, 'DOWNWARD', 'RETURNED', DUE),
            segment('34', '22', ZS, HQ, 'RETURN', 'REASSIGNED', DUE),
            segment('35', '22', HQ, TZ, 'REASSIGN', 'PENDING_ACCEPT', DUE),
          ],
          null,
        ),
      ],
      [
        action('RETURN', '22', ZS, '2026-09-26T08:10:42Z', {
          targetUnitName: HQ,
          note: '舟山无相关警情',
        }),
      ],
    );
    const [row] = branchRows(reassigned, outcomeLabel, labels, NOW);
    assert.equal(row.unitName, TZ);
    assert.equal(row.route, `${ZS}退回后重新派发`);
    assert.deepEqual(row.tag, { label: '待承接', tone: 'accent' });
    assert.equal(row.summary, `${ZS}退回：舟山无相关警情`);
  });

  it('交接目标方只看到申请：按本单位列出，占位说明用次要文字', () => {
    const candidate = task(
      [
        branch('23', null, [], null, {
          currentAssignmentId: null,
          transfers: [
            {
              id: '5',
              branchId: '23',
              targetUnitName: ZS,
              status: 'AWAITING_TARGET',
              targetMine: true,
            },
          ],
        }),
      ],
      [],
      { issuerMine: false },
    );
    const [row] = branchRows(candidate, outcomeLabel, labels, NOW);
    assert.equal(row.unitName, ZS);
    assert.equal(row.route, undefined);
    assert.deepEqual(row.tag, { label: '待接收支队确认', tone: 'warning' });
    assert.equal(row.summaryMuted, true);
    assert.deepEqual(
      branchCommands(candidate, {
        ...candidate.branches![0],
        allowedActions: [allowed('respond', undefined, '5')],
      }).map((command) => command.key),
      ['respond'],
    );
  });
});

describe('办理记录', () => {
  it('发起任务排在同一时刻的下发之前，其余保持时间顺序', () => {
    const entries = historyEntries(task5.actions!, outcomeLabel, labels.returnReason);
    assert.equal(entries.length, 14);
    assert.deepEqual(
      entries.slice(0, 4).map((entry) => entry.code),
      ['CREATE', 'DISPATCH', 'DISPATCH', 'DISPATCH'],
    );
    assert.deepEqual(entries[0].sentence, [`${HQ}发起任务`]);
    assert.equal(entries[0].note, undefined);
    assert.deepEqual(entries[1].sentence, [`${HQ}下发给${NB}，期限 `, { time: DUE }]);
    assert.deepEqual(entries.at(-1)?.sentence, [`${HQ}提交结果：无法核实`]);
  });

  it('批准交接时顺带的整单延期并入批准记录，空说明不显示', () => {
    const entries = historyEntries(task3.actions!, outcomeLabel, labels.returnReason);
    assert.equal(entries.length, 19);
    assert.equal(
      entries.some((entry) => entry.code === 'EXTEND_DUE'),
      false,
    );
    const approve = entries.find((entry) => entry.code === 'TRANSFER_APPROVE');
    assert.deepEqual(approve?.sentence, [
      `${HQ}批准交接给${ZS}，期限 `,
      { time: T3_EXTENDED },
      '；任务期限由 ',
      { time: T3_DUE },
      ' 延至 ',
      { time: T3_EXTENDED },
    ]);
    assert.equal(approve?.note, T3_DECISION);
    assert.equal(entries.find((entry) => entry.code === 'TRANSFER_ACCEPT')?.note, undefined);
  });

  it('没有配对批准的期限调整单独成条', () => {
    const [entry] = historyEntries(
      [
        action('EXTEND_DUE', null, HQ, T3_APPROVED, {
          newDueAt: T3_EXTENDED,
          note: '延期',
        }) as never,
      ],
      outcomeLabel,
      labels.returnReason,
    );
    assert.deepEqual(entry.sentence, [`${HQ}将任务期限调整至 `, { time: T3_EXTENDED }]);
    assert.equal(entry.note, '延期');
  });

  it('分支经过包含本分支记录与从本分支往下的派发', () => {
    const entries = historyEntries(task3.actions!, outcomeLabel, labels.returnReason);
    const nb = branchHistory(entries, task3, task3.branches![0]);
    assert.deepEqual(
      nb.map((entry) => [entry.code, entry.branchId]),
      [
        ['DISPATCH', '7'],
        ['ACCEPT', '7'],
        ['DISPATCH', '9'],
        ['DISPATCH', '10'],
        ['RESULT', '7'],
      ],
    );
    assert.equal(branchHistory(entries, task3, task3.branches![1]).length, 8);
  });
});

describe('我单位待办与分支操作', () => {
  it('只显示服务端下发的操作，保留顺序、禁用原因和唯一主按钮', () => {
    const pending = task(
      [
        branch('24', null, [segment('36', '24', HQ, TZ, 'DOWNWARD', 'PENDING_ACCEPT', DUE)], null, {
          allowedActions: [allowed('accept'), allowed('return', '相关单位已经变化，请刷新后重试')],
        }),
      ],
      [],
      { issuerMine: false },
    );
    const commands = branchCommands(pending, pending.branches![0]);
    assert.deepEqual(
      commands.map((command) => [command.key, command.primary, command.disabledReason]),
      [
        ['accept', true, undefined],
        ['return', false, '相关单位已经变化，请刷新后重试'],
      ],
    );
    assert.deepEqual(branchCommands(pending, { ...pending.branches![0], allowedActions: [] }), []);
    assert.deepEqual(
      taskTodos(pending).map((todo) => [todo.title, todo.description]),
      [[`${TZ}：请确认是否承接`, ['截止 ', { time: DUE }]]],
    );
  });

  it('禁用结果仍可见，不能成为主按钮；前端不再根据下级状态推断', () => {
    const detail = task(
      [
        branch('25', null, [], null, {
          allowedActions: [
            allowed('progress'),
            allowed('results', '下级分支尚未全部办结'),
            allowed('respond', undefined, '6'),
          ],
          transfers: [{ id: '6', fromUnitName: WZ, targetUnitName: TZ, status: 'AWAITING_TARGET' }],
        }),
      ],
      [],
    );
    const commands = branchCommands(detail, detail.branches![0]);
    assert.deepEqual(
      commands.map((command) => [command.action, command.primary]),
      [
        ['progress', false],
        ['results', false],
        ['respond', true],
      ],
    );
    assert.equal(commands[2].transfer?.id, '6');
    assert.equal(
      taskTodos(detail).find((todo) => todo.key === 'respond-6')?.description[0],
      `${WZ}申请把此分支交接给本单位；同意后仍需总队批准。`,
    );
  });

  it('返回接回的分支标题说明原单位，按服务端提供的操作办理', () => {
    const returned = task(
      [
        branch(
          '28',
          null,
          [
            segment('40', '28', HQ, ZS, 'DOWNWARD', 'RETURNED', DUE),
            segment('41', '28', ZS, HQ, 'RETURN', 'IN_PROGRESS', DUE),
          ],
          null,
          { allowedActions: [allowed('progress'), allowed('reassign')] },
        ),
      ],
      [],
    );
    assert.deepEqual(
      taskTodos(returned).map((todo) => [
        todo.title,
        todo.commands.map((command) => command.action),
      ]),
      [[`${ZS}：已退回，待本单位处理`, ['progress', 'reassign']]],
    );
  });

  it('按 transferId 找对应申请，不误用同分支其他历史申请', () => {
    const detail = task(
      [
        branch('27', null, [segment('39', '27', HQ, TZ, 'DOWNWARD', 'IN_PROGRESS', DUE)], null, {
          allowedActions: [allowed('decide', undefined, '6')],
          transfers: [
            { id: '5', status: 'WITHDRAWN', targetUnitName: NB },
            { id: '6', status: 'AWAITING_ISSUER', targetUnitName: ZS },
          ],
        }),
      ],
      [],
    );
    const [todo] = taskTodos(detail);
    assert.equal(todo.commands[0].transfer?.targetUnitName, ZS);
    assert.deepEqual(todo.description, [`${TZ}申请交接给${ZS}；批准时须确定新期限。`]);
  });

  it('历史已办结任务没有操作列表时不出现待办', () => {
    assert.deepEqual(taskTodos(task5), []);
    assert.deepEqual(taskTodos(task3), []);
  });
});

describe('显示文案', () => {
  it('交接时长按天、小时或分钟显示', () => {
    assert.equal(formatDuration(2880), '2 天');
    assert.equal(formatDuration(120), '2 小时');
    assert.equal(formatDuration(90), '90 分钟');
    assert.equal(formatDuration(), '—');
  });

  it('未知状态保留原值，没有状态显示占位符', () => {
    assert.equal(labels.transfer('APPROVED'), '已批准');
    assert.equal(labels.transfer('NEW_STATE'), 'NEW_STATE');
    assert.equal(labels.transfer(), '—');
  });
});

describe('显式办结与撤回', () => {
  it('撤回计入根分支汇总，显示原因与结束时限，不混入结果类型', () => {
    const detail = task(
      [
        branch(
          '81',
          null,
          [
            segment('91', '81', HQ, ZS, 'DOWNWARD', 'RETURNED', DUE),
            {
              ...segment('92', '81', ZS, HQ, 'RETURN', 'RECALLED', DUE),
              endedAt: '2026-09-26T10:00:00Z',
              endReason: '取消错派要求',
            },
          ],
          null,
          { status: 'RECALLED' },
        ),
        branch(
          '82',
          null,
          [segment('93', '82', HQ, TZ, 'DOWNWARD', 'COMPLETED', DUE)],
          result('82', 'NOT_FOUND', '经核查未发现', '2026-09-26T10:00:00Z'),
        ),
      ],
      [],
      { status: 'AWAITING_CLOSE', allowedActions: [allowed('close')] },
    );
    const rows = branchRows(detail, outcomeLabel, labels, NOW);
    assert.deepEqual(rows[0].tag, { label: '已撤回', tone: 'neutral' });
    assert.equal(rows[0].route, `退回后由${HQ}撤回`);
    assert.equal(rows[0].summary, '撤回原因：取消错派要求');
    assert.equal(rows[0].timing, 'on-time');
    assert.equal(rows[1].tag.tone, 'neutral');
    const summary = taskSummary(rows);
    assert.equal(summary.answered + summary.recalled, summary.total);
    assert.equal(summary.recalled, 1);
    assert.deepEqual(
      summary.outcomes.map((outcome) => outcome.code),
      ['NOT_FOUND'],
    );
    assert.deepEqual(taskTodos(detail), []);
    assert.deepEqual(
      taskTodos({ ...detail, allowedActions: [allowed('close', '尚有分支未办结')] }),
      [],
    );
  });

  it('历史包含退回原因标签、办结和撤回，姓名随接口展示', () => {
    const actions = [
      action('RETURN', '81', ZS, T5_CREATED, {
        targetUnitName: HQ,
        reasonCode: 'WRONG_TARGET',
        note: '具体说明',
        actorUserName: '合成经办人',
      }),
      action('RECALL', '81', HQ, DUE, { note: '撤回说明' }),
      action('CLOSE', null, HQ, DUE),
    ];
    const entries = historyEntries(actions as never, outcomeLabel, labels.returnReason);
    assert.deepEqual(entries[0].sentence, [`${ZS}退回给${HQ}：派错单位`]);
    assert.equal(entries[0].actorUserName, '合成经办人');
    assert.deepEqual(entries[1].sentence, [`${HQ}撤回此分支`]);
    assert.deepEqual(entries[2].sentence, [`${HQ}办结任务`]);
  });

  it('字典改名只改标签，不改变状态色调与结果顺序', () => {
    assert.deepEqual(
      taskStatusTag('AWAITING_CLOSE', () => '待汇总结论'),
      { label: '待汇总结论', tone: 'warning' },
    );
    assert.deepEqual(taskStatusTag('OPEN', labels.order), { label: '办理中', tone: 'accent' });
    assert.deepEqual(taskStatusTag('COMPLETED', labels.order), {
      label: '已办结',
      tone: 'neutral',
    });
    const rows = branchRows(
      task(
        [
          branch('1', null, [], result('1', 'PARTIAL', '部分', DUE)),
          branch('2', null, [], result('2', 'NOT_FOUND', '未发现', DUE)),
          branch('3', null, [], result('3', 'FULFILLED', '达成', DUE)),
        ],
        [],
      ),
      outcomeLabel,
      labels,
      NOW,
    );
    assert.deepEqual(
      taskSummary(rows).outcomes.map((item) => item.code),
      ['FULFILLED', 'NOT_FOUND', 'PARTIAL'],
    );
  });
});
