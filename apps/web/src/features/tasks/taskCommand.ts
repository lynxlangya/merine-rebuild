import type { CloseTask, CreateTask } from '@merine/api-contract';
import { taskWallTimeToUtc, type TaskWallTime } from './taskTime.ts';

export interface TaskActionContext {
  action: string;
  taskId?: string;
  branchId?: string;
  transferId?: string;
}
export type TaskCommand =
  | { kind: 'create'; input: CreateTask }
  | { kind: 'close'; taskId: string; input: CloseTask }
  | {
      kind: 'branch';
      taskId: string;
      branchId: string;
      action: string;
      transferId?: string;
      input?: Record<string, unknown>;
    };

/** 将业务表单和操作对象固定为一次提交；未知结果重放时不再读取变化中的表单。 */
export function normalizeTaskCommand(
  context: TaskActionContext,
  values: Record<string, unknown>,
): TaskCommand {
  const text = (key: string) => String(values[key] ?? '').trim();
  const dueAt = values.dueAt ? taskWallTimeToUtc(values.dueAt as TaskWallTime) : undefined;
  const targetUnitCodes = [
    ...new Set((values.targetUnitCodes as string[] | undefined) ?? []),
  ].sort();
  if (context.action === 'create') {
    if (!dueAt) throw new Error('请选择截止时间');
    return {
      kind: 'create',
      input: {
        title: text('title'),
        instruction: text('instruction'),
        expectedResult: text('expectedResult'),
        dueAt,
        targetUnitCodes,
      },
    };
  }
  if (!context.taskId) throw new Error('任务信息不完整，请重新打开详情');
  if (context.action === 'close')
    return { kind: 'close', taskId: context.taskId, input: { conclusion: text('conclusion') } };
  if (!context.branchId) throw new Error('责任分支信息不完整，请重新打开详情');
  let input: Record<string, unknown> | undefined;
  switch (context.action) {
    case 'dispatch':
    case 'reassign':
      input = {
        instruction: text('instruction'),
        expectedResult: text('expectedResult'),
        dueAt,
        targetUnitCodes,
      };
      break;
    case 'progress':
      input = { note: text('note') };
      break;
    case 'return':
      input = { reasonCode: text('reasonCode'), reason: text('reason') };
      break;
    case 'recall':
      input = { reason: text('reason') };
      break;
    case 'results':
      input = {
        outcomeCode: text('outcomeCode'),
        handlingDetail: text('handlingDetail'),
        conclusion: text('conclusion'),
      };
      break;
    case 'transfer-requests':
      input = {
        targetUnitCode: text('targetUnitCode'),
        reason: text('reason'),
        workDone: text('workDone'),
        evidenceSummary: text('evidenceSummary'),
        remainingWork: text('remainingWork'),
      };
      break;
    case 'respond':
      input = {
        accept: values.accept === true,
        reason: text('reason'),
        requiredDurationMinutes:
          values.accept === true
            ? Number(values.requiredDurationValue) * Number(values.requiredDurationUnit)
            : undefined,
      };
      break;
    case 'decide':
      input = {
        approve: values.approve === true,
        reason: text('reason'),
        dueAt: values.approve === true ? dueAt : undefined,
      };
      break;
    case 'accept':
    case 'withdraw':
      break;
    default:
      throw new Error('不支持的任务操作');
  }
  return {
    kind: 'branch',
    taskId: context.taskId,
    branchId: context.branchId,
    action: context.action,
    transferId: context.transferId,
    input,
  };
}
