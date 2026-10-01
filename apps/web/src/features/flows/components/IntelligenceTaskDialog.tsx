import { useRestoreFocus } from '../../../shared/useRestoreFocus';
import { useEffect, useRef, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Alert, App, Button, DatePicker, Form, Input, Modal, Select, Space, Spin } from 'antd';
import type {
  CreateIntelligenceTask,
  IntelligenceDetail,
  RecordAssessment,
} from '@merine/api-contract';
import { ApiError } from '../../../shared/http';
import { useIdempotentSubmit } from '../../../shared/useIdempotentSubmit';
import { useAuth } from '../../auth/public';
import { DICTIONARY_CODES, useDictionary, toDictOptions } from '../../dictionaries/public';
import {
  taskIntelKeys,
  fetchIntelligenceTaskContext,
  createIntelligenceTask,
  taskWallTimeToUtc,
  type TaskWallTime,
} from '../../tasks/public';
import { AssessmentPicker } from './AssessmentPicker';
import { formatFlowTime } from '../model';
import { fetchTopic } from '../api';
import { normalizeIntelligenceTask } from '../../tasks/public';
import {
  canConfirmTaskSource,
  canSubmitIntelligenceTask,
  confirmTaskSourceReview,
  createTaskSourceReview,
} from '../taskSourceReview';
type Values = Omit<CreateIntelligenceTask, 'dueAt' | 'newAssessment'> & {
  dueAt: TaskWallTime;
  mode: 'existing' | 'new';
  newAssessment?: RecordAssessment;
};

export function IntelligenceTaskDialog({
  topicId,
  source,
  onClose,
  onCreated,
}: {
  topicId: string;
  source: Pick<IntelligenceDetail, 'title' | 'topicNo' | 'supplements'>;
  onClose: () => void;
  onCreated: (id: string) => void;
}) {
  const { state } = useAuth();
  const user = state.status === 'authenticated' ? state.user : undefined;
  useRestoreFocus();
  const [form] = Form.useForm<Values>();
  const { modal } = App.useApp();
  // 检查点只随已展示并确认的来源说明更新，不跟随资格查询自动刷新。
  const [sourceReview, setSourceReview] = useState(() =>
    createTaskSourceReview(source.supplements),
  );
  const feedback = useRef<HTMLDivElement>(null);
  const mode = Form.useWatch('mode', form);
  const context = useQuery({
    queryKey: [...taskIntelKeys.root(user?.id), topicId, 'context'],
    queryFn: ({ signal }) => fetchIntelligenceTaskContext(topicId, signal),
  });
  const dictionary = useDictionary(DICTIONARY_CODES.assessmentRecommendation);
  const updatedSource = useQuery({
    queryKey: ['intelligence-task-review', user?.id, topicId],
    queryFn: ({ signal }) => fetchTopic(topicId, signal),
    enabled: sourceReview.required,
  });
  const submission = useIdempotentSubmit(
    (input: CreateIntelligenceTask, key: string) => createIntelligenceTask(topicId, input, key),
    (r) => onCreated(r.id!),
    (error) => {
      if (error instanceof ApiError && error.code === 'SOURCE_CHANGED') {
        setSourceReview((current) => ({ ...current, required: true }));
        void updatedSource.refetch();
        void context.refetch();
      }
    },
  );
  useEffect(() => {
    if (context.data && !form.isFieldsTouched())
      form.setFieldsValue({
        mode: context.data.assessments.length ? 'existing' : 'new',
        assessmentId: context.data.assessments[0]?.id,
        newAssessment: { receiptId: context.data.assessmentContext.signedReceipts[0]?.id },
      });
  }, [context.data, form]);
  useEffect(() => {
    if (submission.failure) feedback.current?.scrollIntoView({ block: 'start' });
  }, [submission.failure]);
  const close = () => {
    if (submission.busy || submission.uncertain) return;
    if (form.isFieldsTouched())
      modal.confirm({
        title: '放弃未保存的任务？',
        okText: '放弃',
        cancelText: '继续填写',
        onOk: onClose,
      });
    else onClose();
  };
  const ctx = context.data;
  return (
    <Modal
      open
      title="基于情报发起任务"
      width={800}
      style={{ top: 24 }}
      styles={{ body: { maxHeight: 'calc(100vh - 200px)', overflowY: 'auto' } }}
      onCancel={close}
      closable={!submission.busy && !submission.uncertain}
      mask={{ closable: !submission.busy && !submission.uncertain }}
      cancelButtonProps={{ disabled: submission.busy || submission.uncertain }}
      confirmLoading={submission.busy}
      okText={submission.uncertain ? '重试提交' : '创建并下发'}
      okButtonProps={{
        disabled: !submission.uncertain && !canSubmitIntelligenceTask(sourceReview, context),
      }}
      onOk={() => (submission.uncertain ? void submission.retry() : form.submit())}
    >
      <p>
        来源情报：{source.title} · {source.topicNo}
      </p>
      {context.isPending && <Spin />}
      {context.error && (
        <Alert
          type="error"
          title={context.error.message}
          action={<Button onClick={() => void context.refetch()}>重试</Button>}
        />
      )}
      <div ref={feedback}>
        {submission.failure && <Alert type="error" title={submission.failure} />}
        {sourceReview.required && (
          <Alert
            type="warning"
            title="请核对来源新增说明，已填写内容保留。"
            description={
              <>
                {updatedSource.isFetching && <Spin size="small" />}
                {!updatedSource.isError &&
                  updatedSource.data?.supplements.map((s) => (
                    <div key={s.id}>
                      <strong>{formatFlowTime(s.createdAt)}</strong>
                      <p style={{ whiteSpace: 'pre-wrap' }}>{s.body}</p>
                    </div>
                  ))}
                {updatedSource.error && (
                  <Alert
                    type="error"
                    title="来源说明加载失败，请重试后核对。"
                    action={
                      <Button
                        loading={updatedSource.isFetching}
                        onClick={() => void updatedSource.refetch()}
                      >
                        重新加载说明
                      </Button>
                    }
                  />
                )}
                <Button
                  disabled={!canConfirmTaskSource(context, updatedSource)}
                  onClick={() =>
                    setSourceReview((current) =>
                      confirmTaskSourceReview(current, context, updatedSource),
                    )
                  }
                >
                  已核对，继续填写
                </Button>
              </>
            }
          />
        )}
      </div>
      {ctx && (
        <Form
          form={form}
          layout="vertical"
          scrollToFirstError
          disabled={submission.busy || submission.uncertain}
          onFinish={(v) => {
            if (
              submission.busy ||
              submission.uncertain ||
              !canSubmitIntelligenceTask(sourceReview, context)
            )
              return;
            void submission.submit(
              normalizeIntelligenceTask({
                title: v.title.trim(),
                instruction: v.instruction.trim(),
                expectedResult: v.expectedResult.trim(),
                dueAt: taskWallTimeToUtc(v.dueAt),
                targetUnitCodes: [...v.targetUnitCodes].sort(),
                backgroundSummary: v.backgroundSummary.trim(),
                sourceSupplementCheckpoint: sourceReview.checkpoint,
                ...(v.mode === 'existing'
                  ? { assessmentId: v.assessmentId }
                  : {
                      newAssessment: {
                        ...v.newAssessment!,
                        analysis: v.newAssessment!.analysis.trim(),
                      },
                    }),
              }),
            );
          }}
        >
          <Form.Item name="mode" label="发起依据">
            <Select
              options={[
                { value: 'existing', label: '采用本单位已有研判' },
                {
                  value: 'new',
                  label: '同时记录新研判',
                  disabled: !ctx.assessmentContext.canAssess,
                },
              ]}
            />
          </Form.Item>
          {mode === 'existing' ? (
            <Form.Item name="assessmentId" label="采用的研判" rules={[{ required: true }]}>
              <AssessmentPicker topicId={topicId} userId={user?.id} />
            </Form.Item>
          ) : (
            <>
              {!ctx.assessmentContext.sourceMine && (
                <Form.Item
                  name={['newAssessment', 'receiptId']}
                  label="依据的接收记录"
                  rules={[{ required: true }]}
                >
                  <Select
                    options={ctx.assessmentContext.signedReceipts.map((r) => ({
                      value: r.id,
                      label: `${r.fromUnitName} · ${formatFlowTime(r.signedAt)}`,
                    }))}
                  />
                </Form.Item>
              )}
              <Form.Item
                name={['newAssessment', 'analysis']}
                label="研判说明"
                rules={[{ required: true, whitespace: true }]}
              >
                <Input.TextArea rows={3} maxLength={4000} showCount />
              </Form.Item>
              <Form.Item
                name={['newAssessment', 'recommendationCode']}
                label="建议行动"
                rules={[{ required: true }]}
              >
                <Select options={toDictOptions(dictionary.data)} loading={dictionary.isFetching} />
              </Form.Item>
            </>
          )}
          <Form.Item
            name="backgroundSummary"
            label="提供给办理单位的背景摘要"
            extra="此内容将提供给任务参与单位，并随后续下发、重派或交接展示。"
            rules={[{ required: true, whitespace: true }]}
          >
            <Input.TextArea rows={3} maxLength={4000} showCount />
          </Form.Item>
          <Form.Item name="title" label="任务标题" rules={[{ required: true, whitespace: true }]}>
            <Input maxLength={160} />
          </Form.Item>
          <Form.Item
            name="instruction"
            label="办理要求"
            rules={[{ required: true, whitespace: true }]}
          >
            <Input.TextArea rows={3} maxLength={4000} showCount />
          </Form.Item>
          <Form.Item
            name="expectedResult"
            label="预期结果"
            rules={[{ required: true, whitespace: true }]}
          >
            <Input.TextArea rows={2} maxLength={1000} />
          </Form.Item>
          <Space align="start" style={{ width: '100%' }}>
            <Form.Item
              name="targetUnitCodes"
              label="直属责任单位"
              rules={[{ required: true, type: 'array', min: 1 }]}
            >
              <Select
                mode="multiple"
                style={{ minWidth: 260 }}
                options={ctx.targets.map((u) => ({ value: u.code, label: u.name }))}
              />
            </Form.Item>
            <Form.Item name="dueAt" label="截止时间" rules={[{ required: true }]}>
              <DatePicker showTime={{ format: 'HH:mm' }} format="YYYY-MM-DD HH:mm" />
            </Form.Item>
          </Space>
          {ctx.reason && <Alert type="warning" title={ctx.reason} />}
        </Form>
      )}
    </Modal>
  );
}
