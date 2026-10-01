import { useRestoreFocus } from '../../../shared/useRestoreFocus';
import { App, Alert, Form, Input, Modal, Select } from 'antd';
import type { AssessmentContext, RecordAssessment } from '@merine/api-contract';
import { useIdempotentSubmit } from '../../../shared/useIdempotentSubmit';
import { DICTIONARY_CODES, useDictionary, toDictOptions } from '../../dictionaries/public';
import { recordAssessment } from '../assessmentApi';
import { formatFlowTime } from '../model';

export function AssessmentDialog({
  topicId,
  context,
  onClose,
  onSaved,
}: {
  topicId: string;
  context: AssessmentContext;
  onClose: () => void;
  onSaved: () => void;
}) {
  useRestoreFocus();
  const [form] = Form.useForm<RecordAssessment>();
  const { modal } = App.useApp();
  const dictionary = useDictionary(DICTIONARY_CODES.assessmentRecommendation);
  const submission = useIdempotentSubmit(
    (input: RecordAssessment, key: string) => recordAssessment(topicId, input, key),
    () => onSaved(),
  );
  const close = () => {
    if (submission.busy || submission.uncertain) return;
    if (form.isFieldsTouched())
      modal.confirm({
        title: '放弃未保存的研判？',
        okText: '放弃',
        cancelText: '继续填写',
        onOk: onClose,
      });
    else onClose();
  };
  return (
    <Modal
      open
      title="记录研判"
      width={600}
      style={{ top: 24 }}
      styles={{ body: { maxHeight: 'calc(100vh - 200px)', overflowY: 'auto' } }}
      onCancel={close}
      mask={{ closable: !submission.busy && !submission.uncertain }}
      closable={!submission.busy && !submission.uncertain}
      cancelButtonProps={{ disabled: submission.busy || submission.uncertain }}
      confirmLoading={submission.busy}
      okText={submission.uncertain ? '重试提交' : '保存研判'}
      onOk={() => (submission.uncertain ? void submission.retry() : form.submit())}
    >
      {submission.failure && <Alert type="error" title={submission.failure} />}
      <Form
        form={form}
        layout="vertical"
        disabled={submission.busy || submission.uncertain}
        initialValues={{ receiptId: context.signedReceipts[0]?.id }}
        onFinish={(v) => void submission.submit({ ...v, analysis: v.analysis.trim() })}
      >
        {!context.sourceMine && (
          <Form.Item name="receiptId" label="依据的接收记录" rules={[{ required: true }]}>
            <Select
              options={context.signedReceipts.map((r) => ({
                value: r.id,
                label: `${r.fromUnitName} · ${formatFlowTime(r.signedAt)}`,
              }))}
            />
          </Form.Item>
        )}
        <Form.Item name="analysis" label="研判说明" rules={[{ required: true, whitespace: true }]}>
          <Input.TextArea
            autoFocus
            rows={5}
            maxLength={4000}
            showCount
            placeholder="说明事实、依据与仍需核实的疑点"
          />
        </Form.Item>
        <Form.Item name="recommendationCode" label="建议行动" rules={[{ required: true }]}>
          <Select loading={dictionary.isFetching} options={toDictOptions(dictionary.data)} />
        </Form.Item>
      </Form>
    </Modal>
  );
}
