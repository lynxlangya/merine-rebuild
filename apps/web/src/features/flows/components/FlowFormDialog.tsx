import { useRestoreFocus } from '../../../shared/useRestoreFocus';
import { useEffect } from 'react';
import { Alert, App, Button, Checkbox, Form, Input, Modal, Select, Spin } from 'antd';
import type { IntelligenceUnitOption } from '@merine/api-contract';
import { useAuth } from '../../auth/public';
import { AssessmentPicker } from './AssessmentPicker';
import { ApiError } from '../../../shared/http';
import { ScopeSelector } from './ScopeSelector';
import { flowLabel, needsUnits as actionNeedsUnits } from '../actions';
import type { FlowDialogState, FlowForm } from '../actions';
import { validDraftTargets } from '../model';
import styles from '../InformationFlowPage.module.css';

/** 每次打开都有自己的表单实例；预填、取消和失败保留输入不会与上一次弹窗交叉。 */
export function FlowFormDialog({
  dialog,
  topicId,
  options,
  unitLoading,
  unitError,
  busy,
  locked,
  failure,
  onRetry,
  onRetrySubmission,
  onCancel,
  onSubmit,
}: {
  dialog: FlowDialogState;
  topicId?: string;
  options: IntelligenceUnitOption[];
  unitLoading: boolean;
  unitError: Error | null;
  busy: boolean;
  locked: boolean;
  failure?: string;
  onRetry: () => void;
  onRetrySubmission: () => void;
  onCancel: () => void;
  onSubmit: (values: FlowForm) => void;
}) {
  useRestoreFocus();
  const [form] = Form.useForm<FlowForm>();
  const { state } = useAuth();
  const user = state.status === 'authenticated' ? state.user : undefined;
  const { modal } = App.useApp();
  const attach = Form.useWatch('attachAssessment', form);
  const close = () => {
    if (busy || locked) return;
    if (form.isFieldsTouched())
      modal.confirm({
        title: '放弃未保存的内容？',
        okText: '放弃',
        cancelText: '继续填写',
        onOk: onCancel,
      });
    else onCancel();
  };
  const scope = Form.useWatch('scopeUnitCodes', form) ?? dialog.initialValues.scopeUnitCodes ?? [];
  const needsUnits = actionNeedsUnits(dialog.code);
  const isDraft = dialog.code === 'create' || dialog.code === 'update';
  const isSend = dialog.code === 'send' || dialog.code === 'forward';
  useEffect(() => {
    if (!isDraft || unitLoading || unitError || busy || locked) return;
    const selected: string[] = form.getFieldValue('targetUnitCodes') ?? [];
    const valid = validDraftTargets(selected, scope, options);
    if (valid.length !== selected.length) form.setFieldValue('targetUnitCodes', valid);
  }, [isDraft, scope, options, unitLoading, unitError, busy, locked, form]);
  return (
    <Modal
      open
      title={flowLabel(dialog.code, dialog.published)}
      width={isDraft ? 800 : 600}
      style={{ top: 24 }}
      styles={{ body: { maxHeight: 'calc(100vh - 200px)', overflowY: 'auto' } }}
      destroyOnHidden
      mask={{ closable: !busy && !locked }}
      closable={!busy && !locked}
      onCancel={close}
      onOk={() => (locked ? onRetrySubmission() : form.submit())}
      confirmLoading={busy}
      okText={
        locked
          ? '重试原提交'
          : isDraft
            ? '保存草稿'
            : isSend
              ? dialog.published || dialog.code === 'forward'
                ? '共享情报'
                : '发送情报'
              : dialog.code === 'feedbacks'
                ? '提交反馈'
                : '保存说明'
      }
      cancelButtonProps={{ disabled: busy || locked }}
      okButtonProps={{ disabled: !locked && needsUnits && (unitLoading || !!unitError) }}
    >
      {failure && <Alert type="error" title={failure} showIcon className={styles.formAlert} />}
      {unitError && needsUnits && (
        <Alert
          type="error"
          title={unitError instanceof ApiError ? unitError.message : '单位选项加载失败'}
          action={<Button onClick={() => onRetry()}>重试</Button>}
        />
      )}
      {needsUnits && unitLoading && <Spin />}
      <Form
        form={form}
        layout="vertical"
        initialValues={dialog.initialValues}
        onFinish={onSubmit}
        disabled={busy || locked}
      >
        {isDraft && (
          <>
            <Form.Item
              name="title"
              label="情报标题"
              rules={[{ required: true, whitespace: true, message: '请输入标题' }]}
            >
              <Input maxLength={160} showCount />
            </Form.Item>
            <Form.Item
              name="body"
              label="情报正文"
              rules={[{ required: true, whitespace: true, message: '请输入情报正文' }]}
            >
              <Input.TextArea rows={4} maxLength={4000} showCount />
            </Form.Item>
            <Form.Item
              name="scopeUnitCodes"
              label="允许传播范围"
              rules={[{ required: true, type: 'array', min: 1, message: '请选择允许知悉的单位' }]}
            >
              <ScopeSelector options={options} />
            </Form.Item>
          </>
        )}
        {(isDraft || isSend) && (
          <>
            <Form.Item
              name="targetUnitCodes"
              label={isDraft ? '首次接收单位' : '本次接收单位'}
              dependencies={isDraft ? ['scopeUnitCodes'] : []}
              rules={[
                { required: true, type: 'array', min: 1, message: '请选择接收单位' },
                {
                  validator: (_, targets: string[] = []) =>
                    !isDraft || targets.every((code) => scope.includes(code))
                      ? Promise.resolve()
                      : Promise.reject(new Error('接收单位须在传播范围内')),
                },
              ]}
            >
              <Select
                mode="multiple"
                showSearch={{ optionFilterProp: 'label' }}
                placeholder="选择接收单位"
                options={options
                  .filter((u) => u.targetEligible && (!isDraft || scope.includes(u.code)))
                  .map((u) => ({ value: u.code, label: u.name }))}
              />
            </Form.Item>
            {isSend && dialog.published && (
              <>
                <Form.Item name="attachAssessment" valuePropName="checked">
                  <Checkbox>附本单位研判</Checkbox>
                </Form.Item>
                {attach && (
                  <>
                    <Form.Item name="assessmentId" label="引用的研判" rules={[{ required: true }]}>
                      <AssessmentPicker
                        topicId={topicId!}
                        onPick={(a) => form.setFieldValue('assessmentSummary', a.analysis)}
                        userId={user?.id}
                      />
                    </Form.Item>
                    <Form.Item
                      name="assessmentSummary"
                      label="本次共享研判摘要"
                      rules={[{ required: true, whitespace: true }]}
                    >
                      <Input.TextArea rows={3} maxLength={4000} showCount />
                    </Form.Item>
                  </>
                )}
              </>
            )}
            <Form.Item name="note" label="发送说明（可选）">
              <Input.TextArea rows={3} maxLength={1000} showCount />
            </Form.Item>
          </>
        )}
        {dialog?.code === 'supplement' && (
          <Form.Item name="kind" label="说明类型" rules={[{ required: true }]}>
            <Select
              options={[
                { value: 'SUPPLEMENT', label: '补充' },
                { value: 'CORRECTION', label: '更正' },
              ]}
            />
          </Form.Item>
        )}
        {(dialog?.code === 'feedbacks' || dialog?.code === 'supplement') && (
          <Form.Item
            name="body"
            label={dialog.code === 'feedbacks' ? '线索反馈' : '追加说明'}
            rules={[{ required: true, whitespace: true, message: '请输入说明' }]}
          >
            <Input.TextArea rows={6} maxLength={4000} showCount />
          </Form.Item>
        )}
      </Form>
    </Modal>
  );
}
