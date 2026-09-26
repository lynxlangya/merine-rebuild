import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useParams, useSearchParams } from 'react-router';
import {
  Alert,
  App,
  Button,
  DatePicker,
  Empty,
  Form,
  Input,
  Modal,
  Select,
  Spin,
  Table,
  Tabs,
  Tag,
  Typography,
} from 'antd';
import type { TableColumnsType } from 'antd';
import type { TaskBranch, TaskListItem, TaskTransfer } from '@merine/api-contract';
import { PageHeader } from '../../shared/ui/PageHeader';
import { TablePager } from '../../shared/ui/TablePager';
import { ApiError } from '../../shared/http';
import { PERMISSIONS, hasPermission } from '../../shared/permissions';
import { useAuth } from '../auth/public';
import { DICTIONARY_CODES, dictLabel, toDictOptions, useDictionary } from '../dictionaries/public';
import { createTask, fetchTask, fetchTasks, fetchTaskTargets, taskAction } from './api';
import { TaskDetailView } from './TaskDetailView';
import { formatTaskTime, taskWallTimeToUtc, type TaskWallTime } from './taskTime';
import styles from './TaskHandlingPage.module.css';

const tabs = [
  ['all', '全部相关'],
  ['inbox', '待承接'],
  ['doing', '办理中'],
  ['issued', '我单位发起'],
  ['transfers', '待回应交接'],
  ['decisions', '待审批交接'],
  ['completed', '已办结'],
];
const statuses: Record<string, string> = {
  OPEN: '办理中',
  COMPLETED: '已办结',
  PENDING_ACCEPT: '待承接',
  IN_PROGRESS: '办理中',
  RETURNED: '已退回',
  REASSIGNED: '已重派',
  TRANSFERRED: '已交接',
  AWAITING_TARGET: '待目标确认',
  AWAITING_ISSUER: '待总队审批',
  APPROVED: '已批准',
  TARGET_DECLINED: '目标拒绝',
  ISSUER_REJECTED: '总队拒绝',
  WITHDRAWN: '已撤回',
};
const labels: Record<string, string> = {
  create: '新建任务',
  accept: '承接任务',
  progress: '记录进展',
  return: '承接前退回',
  dispatch: '继续下发',
  reassign: '退回后重新派发',
  results: '提交正式结果',
  'transfer-requests': '申请支队交接',
  respond: '回应交接',
  decide: '审批交接',
  withdraw: '撤回交接',
};
const at = formatTaskTime;
type Dialog = {
  action: string;
  branch?: TaskBranch;
  transfer?: TaskTransfer;
  sourceResultId?: string;
  key: string;
};
type Values = Record<string, unknown>;

export function TaskHandlingPage() {
  const { message } = App.useApp();
  const client = useQueryClient();
  const navigate = useNavigate();
  const { taskId: selectedId } = useParams<{ taskId: string }>();
  const [searchParams, setSearchParams] = useSearchParams();
  const { state } = useAuth();
  const outcomeDictionary = useDictionary(DICTIONARY_CODES.taskResultOutcome);
  const me = state.status === 'authenticated' ? state.user : null;
  const can = (code: string) => me?.permissionCodes.includes(code) ?? false;
  const requestedTab = searchParams.get('tab');
  const tab = tabs.some(([key]) => key === requestedTab) ? requestedTab! : 'all';
  const requestedPage = Number(searchParams.get('page'));
  const page = Number.isInteger(requestedPage) && requestedPage > 0 ? requestedPage : 1;
  const listUrl = `/collaboration/tasks?tab=${tab}&page=${page}`;
  const detailUrl = (id: string) => `/collaboration/tasks/${id}?tab=${tab}&page=${page}`;
  const updateList = (nextTab: string, nextPage: number) =>
    setSearchParams({ tab: nextTab, page: String(nextPage) }, { replace: true });
  const [dialog, setDialog] = useState<Dialog>();
  const [busy, setBusy] = useState(false);
  const [form] = Form.useForm();
  const list = useQuery({
    queryKey: ['tasks', tab, page],
    queryFn: ({ signal }) => fetchTasks(tab, page, signal),
    enabled: !selectedId && can(PERMISSIONS.taskRead),
  });
  const detail = useQuery({
    queryKey: ['task', selectedId],
    queryFn: ({ signal }) => fetchTask(selectedId!, signal),
    enabled: !!selectedId && can(PERMISSIONS.taskRead),
  });
  const current = detail.data;
  const targetAction =
    dialog?.action === 'create'
      ? 'create'
      : dialog?.action === 'dispatch'
        ? 'dispatch'
        : dialog?.action === 'reassign'
          ? 'reassign'
          : dialog?.action === 'transfer-requests'
            ? 'transfer'
            : undefined;
  const targets = useQuery({
    queryKey: ['task-targets', targetAction, selectedId, dialog?.branch?.id],
    queryFn: () => fetchTaskTargets(targetAction!, selectedId, dialog?.branch?.id),
    enabled: !!targetAction,
  });
  const createTargets = useQuery({
    queryKey: ['task-targets', 'create'],
    queryFn: () => fetchTaskTargets('create'),
    enabled: can(PERMISSIONS.taskCreate),
  });
  const open = (
    action: string,
    branch?: TaskBranch,
    transfer?: TaskTransfer,
    sourceResultId?: string,
  ) => {
    form.resetFields();
    setDialog({ action, branch, transfer, sourceResultId, key: crypto.randomUUID() });
  };
  const close = () => {
    setDialog(undefined);
    form.resetFields();
  };

  async function submit(values: Values = {}) {
    if (!dialog) return;
    setBusy(true);
    try {
      const a = dialog.action,
        text = (key: string) => String(values[key] ?? '').trim();
      const dueAt = values.dueAt ? taskWallTimeToUtc(values.dueAt as TaskWallTime) : undefined;
      const targetUnitCodes = (values.targetUnitCodes as string[] | undefined) ?? [];
      let saved;
      if (a === 'create')
        saved = await createTask(
          {
            title: text('title'),
            instruction: text('instruction'),
            expectedResult: text('expectedResult'),
            dueAt: dueAt!,
            targetUnitCodes,
            ...(text('sourceResultId') ? { sourceResultId: text('sourceResultId') } : {}),
          },
          dialog.key,
        );
      else {
        if (!selectedId || !dialog.branch?.id) return;
        let body: Record<string, unknown> | undefined;
        if (a === 'dispatch' || a === 'reassign')
          body = {
            instruction: text('instruction'),
            expectedResult: text('expectedResult'),
            dueAt,
            targetUnitCodes,
          };
        if (a === 'progress') body = { note: text('note') };
        if (a === 'return') body = { reason: text('reason') };
        if (a === 'results')
          body = {
            outcomeCode: text('outcomeCode'),
            handlingDetail: text('handlingDetail'),
            conclusion: text('conclusion'),
            ...(text('suggestedUnitCode') ? { suggestedUnitCode: text('suggestedUnitCode') } : {}),
          };
        if (a === 'transfer-requests')
          body = {
            targetUnitCode: text('targetUnitCode'),
            reason: text('reason'),
            workDone: text('workDone'),
            evidenceSummary: text('evidenceSummary'),
            remainingWork: text('remainingWork'),
          };
        if (a === 'respond')
          body = {
            accept: values.accept === true,
            reason: text('reason'),
            requiredDurationMinutes:
              values.accept === true ? Number(values.requiredDurationMinutes) : undefined,
          };
        if (a === 'decide')
          body = {
            approve: values.approve === true,
            reason: text('reason'),
            dueAt: values.approve === true ? dueAt : undefined,
          };
        saved = await taskAction(
          selectedId,
          dialog.branch.id,
          a,
          dialog.key,
          body,
          dialog.transfer?.id,
        );
      }
      await Promise.all([
        client.invalidateQueries({ queryKey: ['tasks'] }),
        client.invalidateQueries({ queryKey: ['task'] }),
      ]);
      close();
      if (a === 'create' && saved?.id) void navigate(detailUrl(saved.id));
      message.success('操作已保存');
    } catch (error) {
      message.error(error instanceof ApiError ? error.message : '操作失败，请重试');
    } finally {
      setBusy(false);
    }
  }

  const taskColumns: TableColumnsType<TaskListItem> = [
    {
      title: '任务',
      dataIndex: 'title',
      width: 290,
      render: (_value, item) => (
        <div className={styles.taskCell}>
          <button
            type="button"
            className={styles.titleLink}
            onClick={() => item.id && navigate(detailUrl(item.id))}
          >
            {item.title}
          </button>
          <Typography.Text type="secondary" className={styles.taskNo}>
            {item.taskNo}
          </Typography.Text>
        </div>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (value: string) => (
        <Tag color={value === 'COMPLETED' ? 'green' : 'blue'}>{statuses[value] ?? value}</Tag>
      ),
    },
    { title: '发起单位', dataIndex: 'issuerUnitName', width: 170 },
    {
      title: '当前责任',
      dataIndex: 'currentResponsibleUnits',
      width: 175,
      render: (_value, item) => (
        <div className={styles.taskCell}>
          <span>{item.currentResponsibleUnits ?? '已交付'}</span>
          {item.myStatus && (
            <Typography.Text type="secondary">
              我单位：
              {item.myStatus
                .split(',')
                .map((status) => statuses[status] ?? status)
                .join('、')}
            </Typography.Text>
          )}
        </div>
      ),
    },
    {
      title: '办理期限',
      dataIndex: 'currentDueAt',
      width: 185,
      render: (_value, item) => (
        <div className={styles.taskCell}>
          <span>当前 {at(item.currentDueAt)}</span>
          <Typography.Text type="secondary">原定 {at(item.initialDueAt)}</Typography.Text>
        </div>
      ),
    },
    {
      title: '分支情况',
      dataIndex: 'openBranchCount',
      width: 145,
      render: (_value, item) => (
        <div className={styles.taskCell}>
          <span>待结果 {item.openBranchCount ?? 0}</span>
          {!!item.pendingTransferCount && (
            <Typography.Text type="secondary">
              待处理交接 {item.pendingTransferCount}
            </Typography.Text>
          )}
          {!!item.overdueBranchCount && (
            <Typography.Text type="danger">逾期分支 {item.overdueBranchCount}</Typography.Text>
          )}
        </div>
      ),
    },
    {
      title: '最近动作',
      dataIndex: 'lastActionAt',
      width: 170,
      render: (value: string | undefined) => <span className={styles.taskNo}>{at(value)}</span>,
    },
    {
      title: '操作',
      key: 'action',
      width: 96,
      align: 'right',
      render: (_value, item) => (
        <Button type="link" onClick={() => item.id && navigate(detailUrl(item.id))}>
          查看详情
        </Button>
      ),
    },
  ];

  return (
    <div>
      {selectedId ? (
        !hasPermission(me?.permissionCodes, PERMISSIONS.taskRead) ? (
          <Alert type="warning" message="当前账号没有任务查看权限" />
        ) : detail.isLoading ? (
          <div className={styles.page}>
            <Spin />
          </div>
        ) : detail.isError ? (
          <div className={styles.page}>
            <Button type="link" onClick={() => navigate(listUrl)}>
              返回任务列表
            </Button>
            <Alert
              type="error"
              message={detail.error instanceof ApiError ? detail.error.message : '详情加载失败'}
              action={<Button onClick={() => detail.refetch()}>重试</Button>}
            />
          </div>
        ) : current ? (
          <TaskDetailView
            task={current}
            can={can}
            canCreateFollowup={can(PERMISSIONS.taskCreate) && (createTargets.data?.length ?? 0) > 0}
            outcomeLabel={(code) => dictLabel(outcomeDictionary.data, code ?? '')}
            onBack={() => navigate(listUrl)}
            onAction={open}
          />
        ) : null
      ) : (
        <>
          <PageHeader demo={false} title="任务处置" description="明确责任、办理过程与正式结果。" />
          <div className={styles.page}>
            <div className={styles.toolbar}>
              <Tabs
                activeKey={tab}
                items={tabs.map(([key, label]) => ({ key, label }))}
                onChange={(key) => updateList(key, 1)}
              />
              {can(PERMISSIONS.taskCreate) && (createTargets.data?.length ?? 0) > 0 && (
                <Button type="primary" onClick={() => open('create')}>
                  新建任务
                </Button>
              )}
            </div>
            {!hasPermission(me?.permissionCodes, PERMISSIONS.taskRead) ? (
              <Alert type="warning" message="当前账号没有任务查看权限" />
            ) : list.isLoading ? (
              <Spin />
            ) : list.isError ? (
              <Alert
                type="error"
                message="任务加载失败"
                action={<Button onClick={() => list.refetch()}>重试</Button>}
              />
            ) : (
              <div className={styles.table}>
                <Table<TaskListItem>
                  rowKey={(item) => item.id ?? item.taskNo ?? ''}
                  size="small"
                  tableLayout="fixed"
                  scroll={{ x: 1331 }}
                  dataSource={list.data?.items ?? []}
                  columns={taskColumns}
                  pagination={false}
                  locale={{ emptyText: <Empty description="当前分类暂无任务" /> }}
                />
                <TablePager
                  total={list.data?.total ?? 0}
                  page={page}
                  pageSize={20}
                  itemCount={list.data?.items.length ?? 0}
                  onPageChange={(nextPage) => updateList(tab, nextPage)}
                />
              </div>
            )}
          </div>
        </>
      )}
      <Modal
        title={dialog ? labels[dialog.action] : ''}
        open={!!dialog}
        destroyOnHidden
        onCancel={close}
        onOk={() => form.submit()}
        confirmLoading={busy}
        okText="确认提交"
      >
        <Form form={form} layout="vertical" onFinish={submit}>
          {dialog?.action === 'create' && (
            <>
              <Form.Item name="title" label="任务标题" rules={[{ required: true, max: 160 }]}>
                <Input maxLength={160} placeholder="如：核查某船停靠位置及船员情况" />
              </Form.Item>
              <Form.Item
                name="sourceResultId"
                label="来源结果 ID（可选）"
                initialValue={dialog.sourceResultId}
              >
                <Input
                  readOnly={!!dialog.sourceResultId}
                  placeholder="选填；可从结果详情发起后续任务自动带入"
                />
              </Form.Item>
            </>
          )}
          {['create', 'dispatch', 'reassign'].includes(dialog?.action ?? '') && (
            <>
              <Form.Item
                name="instruction"
                label="任务要求"
                rules={[{ required: true, max: 4000 }]}
              >
                <Input.TextArea rows={3} placeholder="说明需要办理的事项、范围和具体要求" />
              </Form.Item>
              <Form.Item
                name="expectedResult"
                label="交付目标"
                rules={[{ required: true, max: 1000 }]}
              >
                <Input.TextArea
                  rows={2}
                  placeholder="说明需提交的结果和依据，如核查结论与现场材料"
                />
              </Form.Item>
              <Form.Item name="targetUnitCodes" label="直属下级" rules={[{ required: true }]}>
                <Select
                  mode="multiple"
                  placeholder="选择接收任务的直属下级，可多选"
                  loading={targets.isLoading}
                  options={(targets.data ?? []).map((u) => ({ value: u.code, label: u.name }))}
                />
              </Form.Item>
              <Form.Item name="dueAt" label="明确截止时间" rules={[{ required: true }]}>
                <DatePicker showTime placeholder="选择截止日期和时间" style={{ width: '100%' }} />
              </Form.Item>
            </>
          )}
          {dialog?.action === 'progress' && (
            <Form.Item name="note" label="办理进展" rules={[{ required: true, max: 4000 }]}>
              <Input.TextArea rows={4} />
            </Form.Item>
          )}
          {dialog?.action === 'return' && (
            <Form.Item name="reason" label="退回原因" rules={[{ required: true, max: 1000 }]}>
              <Input.TextArea rows={3} />
            </Form.Item>
          )}
          {dialog?.action === 'results' && (
            <>
              <Form.Item name="outcomeCode" label="结果类型" rules={[{ required: true }]}>
                <Select
                  options={toDictOptions(outcomeDictionary.data)}
                  loading={outcomeDictionary.isLoading}
                />
              </Form.Item>
              <Form.Item
                name="handlingDetail"
                label="已做工作与依据"
                rules={[{ required: true, max: 4000 }]}
              >
                <Input.TextArea rows={4} />
              </Form.Item>
              <Form.Item
                name="conclusion"
                label="结论与待办"
                rules={[{ required: true, max: 4000 }]}
              >
                <Input.TextArea rows={3} />
              </Form.Item>
              <Form.Item name="suggestedUnitCode" label="建议后续单位编码（可选）">
                <Input />
              </Form.Item>
              <Alert
                type="info"
                message="跨辖区事项由发出单位决定后续任务；结果不会把剩余时限转给其他大队。"
              />
            </>
          )}
          {dialog?.action === 'transfer-requests' && (
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
              <Alert type="info" message="批准前本单位继续负责，申请不会暂停原期限。" />
            </>
          )}
          {dialog?.action === 'respond' && (
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
                    <Form.Item
                      name="requiredDurationMinutes"
                      label="批准后至少需要的办理分钟数"
                      rules={[{ required: true }]}
                    >
                      <Input type="number" min={1} />
                    </Form.Item>
                  ) : (
                    <Form.Item name="reason" label="拒绝理由" rules={[{ required: true }]}>
                      <Input.TextArea />
                    </Form.Item>
                  )
                }
              </Form.Item>
              <Alert type="info" message="同意后责任仍在原支队；总队批准时才转移。" />
            </>
          )}
          {dialog?.action === 'decide' && (
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
                      <DatePicker showTime style={{ width: '100%' }} />
                    </Form.Item>
                  ) : null
                }
              </Form.Item>
              <Form.Item name="reason" label="审批理由（延期或拒绝时必填）">
                <Input.TextArea />
              </Form.Item>
              <Alert
                type="info"
                message={`目标要求批准后至少 ${dialog.transfer?.requiredDurationMinutes ?? '—'} 分钟；整单当前期限 ${at(current?.currentDueAt)}。`}
              />
            </>
          )}
          {['accept', 'withdraw'].includes(dialog?.action ?? '') && (
            <Alert type="info" message="此操作会写入任务历史。" />
          )}
        </Form>
      </Modal>
    </div>
  );
}
