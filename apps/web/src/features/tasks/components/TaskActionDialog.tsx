import { useRestoreFocus } from '../../../shared/useRestoreFocus';
import { useRef, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  Alert,
  App,
  Button,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
} from 'antd';
import type { TaskBranch, TaskDetail, TaskTransfer } from '@merine/api-contract';
import { DICTIONARY_CODES, toDictOptions, useDictionary } from '../../dictionaries/public';
import { useIdempotentSubmit } from '../../../shared/useIdempotentSubmit';
import { closeTask, createTask, fetchTaskTargets, taskAction } from '../api';
import { normalizeTaskCommand, type TaskCommand } from '../taskCommand';
import { formatDuration } from '../model';
import { TaskTime } from './TaskTime';

const durationUnits = [
  { value: 60, label: '小时' },
  { value: 1440, label: '天' },
];
const labels: Record<string, string> = {
  create: '新建任务',
  accept: '承接任务',
  progress: '记录进展',
  return: '承接前退回',
  dispatch: '下发下级',
  reassign: '退回后重新派发',
  results: '提交处置结果',
  'transfer-requests': '申请支队交接',
  respond: '确认或拒绝承接',
  decide: '审批交接',
  withdraw: '撤回交接申请',
  close: '办结任务',
  recall: '撤回分支',
};
export interface TaskDialog {
  action: string;
  branch?: TaskBranch;
  transfer?: TaskTransfer;
  key: string;
}

/** 每次操作拥有独立表单与提交意图，关闭保护和失败重试不会串用下一次操作。 */
export function TaskActionDialog({
  dialog,
  task,
  sourceLinked,
  onClose,
  onSaved,
}: {
  dialog: TaskDialog;
  task?: TaskDetail;
  sourceLinked: boolean;
  onClose: () => void;
  onSaved: (saved: TaskDetail) => void;
}) {
  useRestoreFocus();
  const [form] = Form.useForm();
  const accept = Form.useWatch('accept', form);
  const approve = Form.useWatch('approve', form);
  const submitLabel =
    dialog.action === 'respond'
      ? accept === true
        ? '同意承接'
        : accept === false
          ? '拒绝承接'
          : '提交交接回应'
      : dialog.action === 'decide'
        ? approve === true
          ? '批准交接'
          : approve === false
            ? '拒绝交接'
            : '提交审批决定'
        : dialog.action === 'create'
          ? '创建并下发'
          : labels[dialog.action];
  const { modal } = App.useApp();
  const confirming = useRef(false);
  const [inputError, setInputError] = useState<string>();
  const outcomeDictionary = useDictionary(DICTIONARY_CODES.taskResultOutcome);
  const returnDictionary = useDictionary(DICTIONARY_CODES.taskReturnReason);
  const targetAction = (
    {
      create: 'create',
      dispatch: 'dispatch',
      reassign: 'reassign',
      'transfer-requests': 'transfer',
    } as Record<string, string>
  )[dialog.action];
  const targets = useQuery({
    queryKey: ['task-targets', targetAction, task?.id, dialog.branch?.id],
    queryFn: () => fetchTaskTargets(targetAction!, task?.id, dialog.branch?.id),
    enabled: !!targetAction,
  });
  const request = useIdempotentSubmit<TaskCommand, TaskDetail>((command, key) => {
    if (command.kind === 'create') return createTask(command.input, key);
    if (command.kind === 'close') return closeTask(command.taskId, command.input, key);
    return taskAction(
      command.taskId,
      command.branchId,
      command.action,
      key,
      command.input,
      command.transferId,
    );
  }, onSaved);
  const { busy, uncertain, failure } = request;
  const close = () => {
    if (busy || uncertain || confirming.current) return;
    if (!form.isFieldsTouched()) {
      onClose();
      return;
    }
    confirming.current = true;
    modal.confirm({
      title: '放弃未保存的内容？',
      okText: '放弃',
      cancelText: '继续填写',
      onOk: onClose,
      afterClose: () => {
        confirming.current = false;
      },
    });
  };
  const submit = (values: Record<string, unknown>) => {
    setInputError(undefined);
    try {
      const command = normalizeTaskCommand(
        {
          action: dialog.action,
          taskId: task?.id,
          branchId: dialog.branch?.id,
          transferId: dialog.transfer?.id,
        },
        values,
      );
      void request.submit(command);
    } catch (error) {
      setInputError(error instanceof Error ? error.message : '请核对输入');
    }
  };
  return (
    <Modal
      open
      title={labels[dialog.action]}
      width={['create', 'dispatch', 'reassign'].includes(dialog.action) ? 800 : 600}
      style={{ top: 24 }}
      styles={{ body: { maxHeight: 'calc(100vh - 200px)', overflowY: 'auto' } }}
      destroyOnHidden
      mask={{ closable: !busy && !uncertain }}
      closable={!busy && !uncertain}
      keyboard={!busy && !uncertain}
      onCancel={close}
      onOk={() => (uncertain ? void request.retry() : form.submit())}
      confirmLoading={busy}
      okText={uncertain ? '重试原提交' : submitLabel}
      cancelButtonProps={{ disabled: busy || uncertain }}
      okButtonProps={{
        disabled: !uncertain && !!targetAction && (targets.isLoading || targets.isError),
      }}
    >
      {(failure || inputError) && (
        <Alert type="error" title={failure || inputError} showIcon style={{ marginBottom: 16 }} />
      )}
      <Form form={form} layout="vertical" disabled={busy || uncertain} onFinish={submit}>
        {sourceLinked &&
          ['dispatch', 'reassign', 'transfer-requests'].includes(dialog.action ?? '') && (
            <Alert
              type="info"
              title="任务背景摘要将同时提供给新的办理单位，原情报仍按独立权限访问。"
              style={{ marginBottom: 16 }}
            />
          )}
        {targetAction && targets.isError ? (
          <Alert
            type="error"
            title="单位选项加载失败"
            action={<Button onClick={() => targets.refetch()}>重试</Button>}
          />
        ) : null}
        {dialog.action === 'create' && (
          <>
            <Form.Item name="title" label="任务标题" rules={[{ required: true, max: 160 }]}>
              <Input autoFocus maxLength={160} placeholder="如：核查某船停靠位置及船员情况" />
            </Form.Item>
          </>
        )}
        {['create', 'dispatch', 'reassign'].includes(dialog.action ?? '') && (
          <>
            <Form.Item name="instruction" label="任务要求" rules={[{ required: true, max: 4000 }]}>
              <Input.TextArea rows={3} placeholder="说明需要办理的事项、范围和具体要求" />
            </Form.Item>
            <Form.Item
              name="expectedResult"
              label="预期结果"
              rules={[{ required: true, max: 1000 }]}
            >
              <Input.TextArea rows={2} placeholder="说明需提交的结果和依据，如核查结论与现场材料" />
            </Form.Item>
            <Form.Item name="targetUnitCodes" label="直属责任单位" rules={[{ required: true }]}>
              <Select
                mode="multiple"
                placeholder="选择接收任务的直属下级，可多选"
                loading={targets.isLoading}
                options={(targets.data ?? []).map((u) => ({ value: u.code, label: u.name }))}
              />
            </Form.Item>
            <Form.Item name="dueAt" label="截止时间" rules={[{ required: true }]}>
              <DatePicker
                showTime={{ format: 'HH:mm' }}
                format="YYYY-MM-DD HH:mm"
                placeholder="选择截止日期和时间"
                style={{ width: '100%' }}
              />
            </Form.Item>
          </>
        )}
        {dialog.action === 'progress' && (
          <Form.Item name="note" label="办理进展" rules={[{ required: true, max: 4000 }]}>
            <Input.TextArea rows={4} />
          </Form.Item>
        )}
        {dialog.action === 'close' && (
          <Form.Item
            name="conclusion"
            label="总体结论"
            rules={[{ required: true, whitespace: true, max: 4000 }]}
          >
            <Input.TextArea rows={5} maxLength={4000} showCount />
          </Form.Item>
        )}
        {dialog.action === 'recall' && (
          <Form.Item
            name="reason"
            label="撤回原因"
            rules={[{ required: true, whitespace: true, max: 1000 }]}
          >
            <Input.TextArea rows={3} maxLength={1000} showCount />
          </Form.Item>
        )}
        {dialog.action === 'return' && (
          <>
            <Form.Item name="reasonCode" label="退回原因" rules={[{ required: true }]}>
              <Select
                options={toDictOptions(returnDictionary.data)}
                loading={returnDictionary.isLoading}
              />
            </Form.Item>
            <Form.Item
              name="reason"
              label="具体说明"
              rules={[{ required: true, whitespace: true, max: 1000 }]}
            >
              <Input.TextArea rows={3} maxLength={1000} showCount />
            </Form.Item>
          </>
        )}
        {dialog.action === 'results' && (
          <>
            <Form.Item name="outcomeCode" label="结果类型" rules={[{ required: true }]}>
              <Select
                options={toDictOptions(outcomeDictionary.data)}
                loading={outcomeDictionary.isLoading}
              />
            </Form.Item>
            <Form.Item
              name="handlingDetail"
              label="办理经过"
              rules={[{ required: true, max: 4000 }]}
            >
              <Input.TextArea rows={4} />
            </Form.Item>
            <Form.Item name="conclusion" label="结论" rules={[{ required: true, max: 4000 }]}>
              <Input.TextArea rows={3} />
            </Form.Item>
          </>
        )}
        {dialog.action === 'transfer-requests' && (
          <>
            <Form.Item name="targetUnitCode" label="目标支队" rules={[{ required: true }]}>
              <Select
                loading={targets.isLoading}
                options={(targets.data ?? []).map((u) => ({ value: u.code, label: u.name }))}
              />
            </Form.Item>
            {(['reason', 'workDone', 'evidenceSummary', 'remainingWork'] as const).map(
              (name, i) => (
                <Form.Item
                  key={name}
                  name={name}
                  label={['交接原因', '已做工作', '依据', '剩余事项'][i]}
                  rules={[{ required: true }]}
                >
                  <Input.TextArea rows={2} />
                </Form.Item>
              ),
            )}
            <Alert type="info" title="批准前本单位继续负责，申请不会暂停原期限。" />
          </>
        )}
        {dialog.action === 'respond' && (
          <>
            <Form.Item name="accept" label="是否同意承接" rules={[{ required: true }]}>
              <Select
                options={[
                  { value: true, label: '同意' },
                  { value: false, label: '拒绝' },
                ]}
              />
            </Form.Item>
            <Form.Item noStyle shouldUpdate>
              {({ getFieldValue }) =>
                getFieldValue('accept') === true ? (
                  <Form.Item label="批准后至少需要的办理时长" required>
                    <Space.Compact>
                      <Form.Item
                        name="requiredDurationValue"
                        noStyle
                        rules={[{ required: true, message: '请填写所需办理时长' }]}
                      >
                        <InputNumber
                          min={1}
                          max={9999}
                          precision={0}
                          placeholder="时长"
                          aria-label="所需办理时长"
                          style={{ width: 160 }}
                        />
                      </Form.Item>
                      <Form.Item name="requiredDurationUnit" noStyle initialValue={60}>
                        <Select
                          aria-label="时长单位"
                          style={{ width: 88 }}
                          options={durationUnits}
                        />
                      </Form.Item>
                    </Space.Compact>
                  </Form.Item>
                ) : getFieldValue('accept') === false ? (
                  <Form.Item name="reason" label="拒绝理由" rules={[{ required: true }]}>
                    <Input.TextArea />
                  </Form.Item>
                ) : null
              }
            </Form.Item>
            <Alert type="info" title="同意后责任仍在原支队；总队批准时才转移。" />
          </>
        )}
        {dialog.action === 'decide' && (
          <>
            <Form.Item name="approve" label="审批决定" rules={[{ required: true }]}>
              <Select
                options={[
                  { value: true, label: '批准' },
                  { value: false, label: '拒绝' },
                ]}
              />
            </Form.Item>
            <Form.Item noStyle shouldUpdate>
              {({ getFieldValue }) =>
                getFieldValue('approve') === true ? (
                  <Form.Item name="dueAt" label="目标支队新截止时间" rules={[{ required: true }]}>
                    <DatePicker
                      showTime={{ format: 'HH:mm' }}
                      format="YYYY-MM-DD HH:mm"
                      placeholder="选择新的截止日期和时间"
                      style={{ width: '100%' }}
                    />
                  </Form.Item>
                ) : null
              }
            </Form.Item>
            <Form.Item name="reason" label="审批理由（延期或拒绝时必填）">
              <Input.TextArea />
            </Form.Item>
            <Alert
              type="info"
              title={
                <>
                  接收支队要求批准后至少 {formatDuration(dialog.transfer?.requiredDurationMinutes)}
                  ；整单当前期限 <TaskTime value={task?.currentDueAt} />。
                </>
              }
            />
          </>
        )}
        {['accept', 'withdraw'].includes(dialog.action ?? '') && (
          <Alert type="info" title="此操作会写入任务历史。" />
        )}
      </Form>
    </Modal>
  );
}
