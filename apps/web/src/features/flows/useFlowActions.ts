import { useRef, useState } from 'react';
import { App } from 'antd';
import { useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router';
import type { IntelligenceDetail } from '@merine/api-contract';
import { ApiError } from '../../shared/http';
import { executeFlowCommand } from './api';
import { flowCommand, flowIntent, resultUncertain } from './actions';
import type { FlowDialogState, FlowForm, FlowIntent, FlowOperation } from './actions';
import { flowKeys } from './queries';
import { assessmentKeys } from './assessmentApi';
import { taskIntelKeys } from '../tasks/public';

/** 同一次提交的内容和请求键一起保留；未知结果只允许重试原请求。 */
export function useFlowActions(userId?: string, topicId?: string, detail?: IntelligenceDetail) {
  const cache = useQueryClient();
  const navigate = useNavigate();
  const { message } = App.useApp();
  const [dialog, setDialog] = useState<FlowDialogState | null>(null);
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState<string>();
  const [uncertain, setUncertain] = useState(false);
  const intent = useRef<FlowIntent | null>(null);
  const running = useRef(false);

  const run = async (pending: FlowIntent) => {
    if (running.current) return;
    running.current = true;
    intent.current = pending;
    setBusy(true);
    setFailure(undefined);
    let data: IntelligenceDetail;
    try {
      data = await executeFlowCommand(pending.command, pending.key);
    } catch (error) {
      pending.uncertain ||= resultUncertain(error);
      setUncertain(pending.uncertain);
      setFailure(
        pending.uncertain
          ? '提交结果待确认，请重试原提交。'
          : error instanceof ApiError
            ? error.message
            : '操作失败，请稍后重试',
      );
      return;
    } finally {
      running.current = false;
      setBusy(false);
    }
    intent.current = null;
    setUncertain(false);
    setDialog(null);
    cache.setQueryData(flowKeys.detail(userId, data.id), data);
    void cache.invalidateQueries({ queryKey: flowKeys.lists(userId) });
    void cache.invalidateQueries({ queryKey: flowKeys.units(userId), refetchType: 'none' });
    void cache.invalidateQueries({ queryKey: assessmentKeys.root(userId) });
    void cache.invalidateQueries({ queryKey: taskIntelKeys.root(userId) });
    message.success('操作成功');
    if (pending.command.code === 'create') navigate(`/collaboration/flows/${data.id}?view=sent`);
  };
  const submit = async (values: FlowForm) => {
    if (!dialog || running.current) return;
    let pending: FlowIntent;
    try {
      pending = flowIntent(
        intent.current,
        flowCommand(dialog, values, topicId, dialog.draftVersion),
      );
    } catch (error) {
      setFailure(error instanceof Error ? error.message : '请核对提交内容');
      return;
    }
    await run(pending);
  };
  const act = async (operation: FlowOperation) => {
    if (running.current || uncertain) return;
    setFailure(undefined);
    if (operation.code === 'sign') {
      await run(flowIntent(intent.current, flowCommand(operation, {}, topicId)));
      return;
    }
    intent.current = null;
    const isDraftSend = operation.code === 'send' && detail?.status === 'DRAFT';
    const initialValues: FlowForm =
      operation.code === 'update' && detail
        ? {
            title: detail.title,
            body: detail.body,
            scopeUnitCodes: detail.scopeUnitCodes,
            targetUnitCodes: detail.draftTargetUnitCodes,
            note: detail.draftNote ?? '',
          }
        : {
            scopeUnitCodes: [],
            targetUnitCodes: isDraftSend ? detail.draftTargetUnitCodes : [],
            note: isDraftSend ? (detail.draftNote ?? '') : '',
            kind: 'SUPPLEMENT',
          };
    setDialog({
      ...operation,
      published: detail?.status === 'PUBLISHED',
      initialValues,
      draftVersion: operation.code === 'update' ? (detail?.version ?? 0) : 0,
    });
  };
  const cancel = () => {
    if (running.current || uncertain) return;
    intent.current = null;
    setDialog(null);
    setFailure(undefined);
  };
  const retry = async () => {
    if (intent.current) await run(intent.current);
  };
  return { dialog, busy, failure, uncertain, act, submit, cancel, retry };
}
