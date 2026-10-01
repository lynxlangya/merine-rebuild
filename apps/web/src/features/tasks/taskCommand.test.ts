import assert from 'node:assert/strict';
import { test } from 'node:test';
import { ApiError } from '../../shared/http.ts';
import { submissionIntent, submissionUncertain } from '../../shared/idempotentIntent.ts';
import { normalizeTaskCommand } from './taskCommand.ts';

const deadline = {
  year: () => 2026,
  month: () => 9,
  date: () => 3,
  hour: () => 16,
  minute: () => 30,
};
test('新建任务不提交已移除的来源结果字段', () => {
  const values = {
    title: ' 核查 ',
    instruction: ' 要求 ',
    expectedResult: ' 结论 ',
    dueAt: deadline,
    targetUnitCodes: ['B', 'A', 'A'],
    sourceResultId: '伪造',
  };
  const normal = normalizeTaskCommand({ action: 'create' }, values);
  assert.deepEqual(normal, {
    kind: 'create',
    input: {
      title: '核查',
      instruction: '要求',
      expectedResult: '结论',
      dueAt: '2026-10-03T08:30:00.000Z',
      targetUnitCodes: ['A', 'B'],
    },
  });
});
test('任务失败重试保留对象、正文和请求键；明确修正才创建新请求', () => {
  const context = { action: 'progress', taskId: '8', branchId: '9' };
  const values = { note: ' 已核对 ' };
  const original = submissionIntent(null, normalizeTaskCommand(context, values));
  assert.equal(
    submissionIntent(original, normalizeTaskCommand(context, { note: '已核对' })),
    original,
  );
  original.uncertain = submissionUncertain(new ApiError('连接中断', 0));
  context.branchId = '10';
  values.note = '后续工作';
  assert.deepEqual(original.input, {
    kind: 'branch',
    taskId: '8',
    branchId: '9',
    action: 'progress',
    transferId: undefined,
    input: { note: '已核对' },
  });
  assert.throws(() => submissionIntent(original, normalizeTaskCommand(context, values)), /重试/);
  const rejected = submissionIntent(null, normalizeTaskCommand(context, values));
  rejected.uncertain = submissionUncertain(new ApiError('填写不完整', 400));
  assert.notEqual(
    submissionIntent(rejected, normalizeTaskCommand(context, { note: '补充说明' })).key,
    rejected.key,
  );
});
test('拒绝交接不提交隐藏的时长和期限；同意时换算业务时长', () => {
  const base = { taskId: '8', branchId: '9', transferId: '12' };
  const refused = normalizeTaskCommand(
    { ...base, action: 'respond' },
    { accept: false, reason: ' 拒绝 ', requiredDurationValue: 2, requiredDurationUnit: 1440 },
  );
  assert.equal(refused.kind === 'branch' && refused.input?.requiredDurationMinutes, undefined);
  const accepted = normalizeTaskCommand(
    { ...base, action: 'respond' },
    { accept: true, requiredDurationValue: 2, requiredDurationUnit: 1440 },
  );
  assert.equal(accepted.kind === 'branch' && accepted.input?.requiredDurationMinutes, 2880);
  const denied = normalizeTaskCommand(
    { ...base, action: 'decide' },
    { approve: false, dueAt: deadline },
  );
  assert.equal(denied.kind === 'branch' && denied.input?.dueAt, undefined);
});

test('正式结果只提交类型、办理经过和结论，不采纳旧表单的建议单位', () => {
  const command = normalizeTaskCommand(
    { action: 'results', taskId: '8', branchId: '9' },
    {
      outcomeCode: 'OUT_OF_JURISDICTION',
      handlingDetail: ' 已核对 ',
      conclusion: ' 去向写入结论 ',
      suggestedUnitCode: '旧单位',
    },
  );
  assert.deepEqual(command.kind === 'branch' && command.input, {
    outcomeCode: 'OUT_OF_JURISDICTION',
    handlingDetail: '已核对',
    conclusion: '去向写入结论',
  });
});
