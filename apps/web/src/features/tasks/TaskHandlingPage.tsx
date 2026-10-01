import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useParams, useSearchParams } from 'react-router';
import { Alert, App, Button, Empty, Spin, Table, Tabs, Typography } from 'antd';
import type { TableColumnsType } from 'antd';
import type { TaskBranch, TaskListItem, TaskTransfer } from '@merine/api-contract';
import { PageHeader } from '../../shared/ui/PageHeader';
import { TablePager } from '../../shared/ui/TablePager';
import { errorText, isForbiddenError } from '../../shared/api-error';
import { PERMISSIONS, hasPermission } from '../../shared/permissions';
import { useAuth } from '../auth/public';
import { DICTIONARY_CODES, dictLabel, useDictionary } from '../dictionaries/public';
import { fetchTask, fetchTasks, fetchTaskTargets } from './api';
import { TaskStatusTag } from './components/TaskStatusTag';
import { RecordTitleLink } from '../../shared/ui/RecordTitleLink';
import { DetailBackLink } from '../../shared/ui/DetailBackLink';
import tableStyles from '../../shared/ui/RecordTable.module.css';
import { TaskTime } from './components/TaskTime';
import { type TaskLabels } from './model';
import { TaskDetailView } from './TaskDetailView';
import { TaskActionDialog, type TaskDialog } from './components/TaskActionDialog';
import { fetchTaskIntelligenceSource, taskIntelKeys } from './intelligenceApi';
import styles from './TaskHandlingPage.module.css';

const tabs = [
  ['all', '全部相关'],
  ['inbox', '待承接'],
  ['doing', '办理中'],
  ['issued', '我单位发起'],
  ['transfers', '待回应交接'],
  ['decisions', '待审批交接'],
  ['closing', '待办结'],
  ['completed', '已办结'],
];
export function TaskHandlingPage() {
  const { message } = App.useApp();
  const client = useQueryClient();
  const navigate = useNavigate();
  const { taskId: selectedId } = useParams<{ taskId: string }>();
  const [searchParams, setSearchParams] = useSearchParams();
  const { state } = useAuth();
  const outcomeDictionary = useDictionary(DICTIONARY_CODES.taskResultOutcome);
  const orderDictionary = useDictionary(DICTIONARY_CODES.taskOrderStatus);
  const branchDictionary = useDictionary(DICTIONARY_CODES.taskBranchStatus);
  const assignmentDictionary = useDictionary(DICTIONARY_CODES.taskAssignmentStatus);
  const transferDictionary = useDictionary(DICTIONARY_CODES.taskTransferStatus);
  const returnDictionary = useDictionary(DICTIONARY_CODES.taskReturnReason);
  const taskLabels: TaskLabels = {
    order: (code) => (code ? dictLabel(orderDictionary.data, code) : '—'),
    branch: (code) => (code ? dictLabel(branchDictionary.data, code) : '—'),
    assignment: (code) => (code ? dictLabel(assignmentDictionary.data, code) : '—'),
    transfer: (code) => (code ? dictLabel(transferDictionary.data, code) : '—'),
    returnReason: (code) => (code ? dictLabel(returnDictionary.data, code) : '—'),
  };
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
  const [dialog, setDialog] = useState<TaskDialog>();
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
  const intelligenceSource = useQuery({
    queryKey: [...taskIntelKeys.root(me?.id), selectedId, 'source'],
    queryFn: ({ signal }) => fetchTaskIntelligenceSource(selectedId!, signal),
    enabled: !!selectedId && can(PERMISSIONS.taskRead),
  });
  const createTargets = useQuery({
    queryKey: ['task-targets', 'create'],
    queryFn: () => fetchTaskTargets('create'),
    enabled: can(PERMISSIONS.taskCreate),
  });
  const open = (action: string, branch?: TaskBranch, transfer?: TaskTransfer) => {
    setDialog({ action, branch, transfer, key: crypto.randomUUID() });
  };

  const taskColumns: TableColumnsType<TaskListItem> = [
    {
      title: '任务',
      dataIndex: 'title',
      width: 300,
      render: (_value, item) => (
        <RecordTitleLink to={detailUrl(item.id!)} number={item.taskNo}>
          {item.title}
        </RecordTitleLink>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 85,
      render: (value: string | undefined) => <TaskStatusTag status={value} />,
    },
    { title: '发起单位', dataIndex: 'issuerUnitName', width: 155 },
    {
      title: '当前责任',
      dataIndex: 'currentResponsibleUnits',
      width: 165,
      render: (_value, item) => (
        <div className={styles.taskCell}>
          <span>{item.currentResponsibleUnits ?? '—'}</span>
          {item.myStatus && (
            <Typography.Text type="secondary">
              我单位：
              {item.myStatus
                .split(',')
                .map((status) => taskLabels.assignment(status))
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
          <span>
            当前 <TaskTime value={item.currentDueAt} />
          </span>
          <Typography.Text type="secondary">
            原定 <TaskTime value={item.initialDueAt} />
          </Typography.Text>
        </div>
      ),
    },
    {
      title: '分支情况',
      dataIndex: 'openBranchCount',
      width: 110,
      // 只列还需要关注的数量；都为 0 时显示 `—`，不再出现“待结果 0”。
      render: (_value, item) =>
        item.openBranchCount || item.pendingTransferCount || item.overdueBranchCount ? (
          <div className={styles.taskCell}>
            {!!item.openBranchCount && <span>待答复 {item.openBranchCount}</span>}
            {!!item.pendingTransferCount && (
              <Typography.Text type="secondary">
                待处理交接 {item.pendingTransferCount}
              </Typography.Text>
            )}
            {!!item.overdueBranchCount && (
              <Typography.Text type="danger">逾期分支 {item.overdueBranchCount}</Typography.Text>
            )}
          </div>
        ) : (
          <Typography.Text type="secondary">—</Typography.Text>
        ),
    },
    {
      title: '最近动作',
      dataIndex: 'lastActionAt',
      width: 170,
      render: (value: string | undefined) => <TaskTime value={value} />,
    },
  ];

  return (
    <div>
      {selectedId ? (
        !hasPermission(me?.permissionCodes, PERMISSIONS.taskRead) ? (
          <Alert type="warning" title="当前账号没有任务查看权限" />
        ) : detail.isLoading ? (
          <div className={styles.page}>
            <Spin />
          </div>
        ) : detail.isError ? (
          <div className={styles.page}>
            <DetailBackLink to={listUrl}>返回任务列表</DetailBackLink>
            <Alert
              type="error"
              title={errorText(detail.error)}
              action={<Button onClick={() => detail.refetch()}>重试</Button>}
            />
          </div>
        ) : current ? (
          <TaskDetailView
            key={current.id}
            task={current}
            outcomeLabel={(code) => (code ? dictLabel(outcomeDictionary.data, code) : '—')}
            labels={taskLabels}
            backTo={listUrl}
            refreshing={detail.isFetching}
            onRefresh={() => {
              void detail.refetch();
              void client.invalidateQueries({ queryKey: taskIntelKeys.root(me?.id) });
            }}
            onAction={open}
          />
        ) : null
      ) : (
        <>
          <PageHeader
            demo={false}
            title="任务处置"
            description="明确责任、办理过程与正式结果。"
            actions={
              can(PERMISSIONS.taskCreate) &&
              (createTargets.data?.length ?? 0) > 0 && (
                <Button type="primary" onClick={() => open('create')}>
                  新建任务
                </Button>
              )
            }
          />
          <div className={styles.page}>
            <div className={styles.toolbar}>
              <Tabs
                activeKey={tab}
                items={tabs.map(([key, label]) => ({ key, label }))}
                onChange={(key) => updateList(key, 1)}
              />
            </div>
            {!hasPermission(me?.permissionCodes, PERMISSIONS.taskRead) ? (
              <Alert type="warning" title="当前账号没有任务查看权限" />
            ) : list.isLoading ? (
              <Spin />
            ) : list.isError && (!list.data || isForbiddenError(list.error)) ? (
              <Alert
                type="error"
                title="任务加载失败"
                description={errorText(list.error)}
                action={<Button onClick={() => list.refetch()}>重试</Button>}
              />
            ) : (
              <section className={tableStyles.frame}>
                <div className={tableStyles.toolbar}>
                  <h2>{tabs.find(([key]) => key === tab)?.[1]}</h2>
                  <Button loading={list.isFetching} onClick={() => void list.refetch()}>
                    刷新
                  </Button>
                </div>
                {list.isError && (
                  <Alert
                    className={tableStyles.error}
                    type="error"
                    title="刷新失败，当前显示上次加载的数据"
                    description={errorText(list.error)}
                    action={<Button onClick={() => void list.refetch()}>重试</Button>}
                  />
                )}
                <Table<TaskListItem>
                  rowKey={(item) => item.id ?? item.taskNo ?? ''}
                  size="small"
                  tableLayout="fixed"
                  scroll={{ x: 1170 }}
                  loading={list.isFetching}
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
              </section>
            )}
          </div>
        </>
      )}
      {dialog && (
        <TaskActionDialog
          key={dialog.key}
          dialog={dialog}
          task={current}
          sourceLinked={intelligenceSource.data?.linked ?? false}
          onClose={() => setDialog(undefined)}
          onSaved={(saved) => {
            setDialog(undefined);
            void client.invalidateQueries({ queryKey: ['tasks'] });
            void client.invalidateQueries({ queryKey: ['task'] });
            if (dialog.action === 'create' && saved.id) void navigate(detailUrl(saved.id));
            message.success('操作已保存');
          }}
        />
      )}
    </div>
  );
}
