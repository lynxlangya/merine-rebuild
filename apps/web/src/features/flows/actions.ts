import type {
  IntelligenceDraftRequest,
  IntelligenceFeedbackRequest,
  IntelligenceReceipt,
  IntelligenceSendRequest,
  IntelligenceSupplementRequest,
} from '@merine/api-contract';
import { ApiError } from '../../shared/http.ts';

const labels = {
  create: '新建情报',
  update: '修改草稿',
  send: '发送情报',
  sign: '单位签收',
  feedbacks: '反馈线索',
  forward: '继续共享',
  supplement: '补充或更正',
} as const;
export type FlowCode = keyof typeof labels;
export type UnitAction = 'create' | 'update' | 'send' | 'forward';
export type FlowOperation =
  | { code: 'create' | 'update' | 'send' | 'supplement'; receipt?: never }
  | { code: 'sign'; receipt: IntelligenceReceipt }
  | { code: 'feedbacks' | 'forward'; receipt: IntelligenceReceipt };
export type FlowForm = Partial<Omit<IntelligenceDraftRequest, 'version'>> & {
  kind?: 'SUPPLEMENT' | 'CORRECTION';
  attachAssessment?: boolean;
  assessmentId?: string;
  assessmentSummary?: string;
};
export type FlowDialogState = Exclude<FlowOperation, { code: 'sign' }> & {
  published: boolean;
  draftVersion: number;
  initialValues: FlowForm;
};
export function flowLabel(code: FlowCode, published = false) {
  return code === 'send' && published ? '共享给其他单位' : labels[code];
}
export function needsUnits(code: FlowCode): code is UnitAction {
  return code === 'create' || code === 'update' || code === 'send' || code === 'forward';
}
export function flowOperation(code: string, receipt?: IntelligenceReceipt): FlowOperation | null {
  switch (code) {
    case 'create':
    case 'update':
    case 'send':
    case 'supplement':
      return { code };
    case 'sign':
      return receipt ? { code, receipt } : null;
    case 'feedbacks':
    case 'forward':
      return receipt ? { code, receipt } : null;
    default:
      return null;
  }
}
export type FlowCommand =
  | { code: 'create'; body: IntelligenceDraftRequest }
  | { code: 'update'; topicId: string; body: IntelligenceDraftRequest }
  | { code: 'send'; topicId: string; body: IntelligenceSendRequest }
  | { code: 'forward'; topicId: string; receiptId: string; body: IntelligenceSendRequest }
  | { code: 'sign'; topicId: string; receiptId: string }
  | { code: 'feedbacks'; topicId: string; receiptId: string; body: IntelligenceFeedbackRequest }
  | { code: 'supplement'; topicId: string; body: IntelligenceSupplementRequest };
const text = (value?: string | null) => (value ?? '').trim();
const codes = (value?: string[]) => [...new Set((value ?? []).map(text))].sort();

/** 比较的是实际提交的规范化命令，不包含未提交的表单字段。 */
export function flowCommand(
  operation: FlowOperation,
  values: FlowForm = {},
  topicId?: string,
  version = 0,
): FlowCommand {
  if (operation.code !== 'create' && !topicId) throw new Error('请重新打开情报详情');
  const id = topicId ?? '';
  switch (operation.code) {
    case 'create':
    case 'update': {
      const body: IntelligenceDraftRequest = {
        title: text(values.title),
        body: text(values.body),
        scopeUnitCodes: codes(values.scopeUnitCodes),
        targetUnitCodes: codes(values.targetUnitCodes),
        note: text(values.note),
        version,
      };
      return operation.code === 'create'
        ? { code: 'create', body }
        : { code: 'update', topicId: id, body };
    }
    case 'send':
      return {
        code: 'send',
        topicId: id,
        body: {
          targetUnitCodes: codes(values.targetUnitCodes),
          note: text(values.note),
          ...(values.attachAssessment
            ? {
                assessmentId: values.assessmentId,
                assessmentSummary: text(values.assessmentSummary),
              }
            : {}),
        },
      };
    case 'forward':
      return {
        code: 'forward',
        topicId: id,
        receiptId: operation.receipt.id,
        body: {
          targetUnitCodes: codes(values.targetUnitCodes),
          note: text(values.note),
          ...(values.attachAssessment
            ? {
                assessmentId: values.assessmentId,
                assessmentSummary: text(values.assessmentSummary),
              }
            : {}),
        },
      };
    case 'sign':
      return { code: 'sign', topicId: id, receiptId: operation.receipt.id };
    case 'feedbacks':
      return {
        code: 'feedbacks',
        topicId: id,
        receiptId: operation.receipt.id,
        body: { body: text(values.body) },
      };
    case 'supplement':
      return {
        code: 'supplement',
        topicId: id,
        body: { kind: values.kind ?? 'SUPPLEMENT', body: text(values.body) },
      };
  }
}
export type FlowIntent = {
  key: string;
  command: FlowCommand;
  fingerprint: string;
  uncertain: boolean;
};
export function flowIntent(previous: FlowIntent | null, command: FlowCommand): FlowIntent {
  const fingerprint = JSON.stringify(command);
  if (previous?.fingerprint === fingerprint) return previous;
  if (previous?.uncertain) throw new Error('请先重试并确认上次提交结果');
  return { key: crypto.randomUUID(), command, fingerprint, uncertain: false };
}
export function resultUncertain(error: unknown) {
  return (
    !(error instanceof ApiError) ||
    error.status === 0 ||
    error.status >= 500 ||
    (error.status >= 200 && error.status < 300) ||
    error.code === 'IDEMPOTENCY_PENDING'
  );
}
