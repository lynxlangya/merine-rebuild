import { useQuery } from '@tanstack/react-query';
import { useNavigate, useParams, useSearchParams } from 'react-router';
import { Alert, Button, Input, Select, Space, Spin, Table, Tabs, Tag } from 'antd';
import type { TableColumnsType } from 'antd';
import type { IntelligenceListItem } from '@merine/api-contract';
import { PageHeader } from '../../shared/ui/PageHeader';
import { TablePager } from '../../shared/ui/TablePager';
import { ApiError } from '../../shared/http';
import { PERMISSIONS, hasPermission } from '../../shared/permissions';
import { useAuth } from '../auth/public';
import { fetchTopic, fetchTopics, fetchUnits } from './api';
import { needsUnits } from './actions';
import { flowKeys } from './queries';
import { useFlowActions } from './useFlowActions';
import { formatFlowTime } from './model';
import { FlowFormDialog } from './components/FlowFormDialog';
import { TopicDetail } from './components/TopicDetail';
import styles from './InformationFlowPage.module.css';

export function InformationFlowPage() {
  const { state } = useAuth();
  const user = state.status === 'authenticated' ? state.user : undefined;
  const navigate = useNavigate();
  const { topicId } = useParams();
  const [params, setParams] = useSearchParams();
  const view = params.get('view') === 'sent' ? 'sent' : 'received';
  const statuses = view === 'sent' ? ['all', 'DRAFT', 'PUBLISHED'] : ['all', 'pending', 'signed'];
  const status = statuses.includes(params.get('status') ?? '') ? params.get('status')! : 'all';
  const page = Math.max(1, Number(params.get('page')) || 1);
  const keyword = params.get('keyword') ?? '';
  const canRead = hasPermission(user?.permissionCodes, PERMISSIONS.intelRead);
  const list = useQuery({
    queryKey: flowKeys.list(user?.id, view, status, keyword, page),
    queryFn: ({ signal }) => fetchTopics(view, status, keyword, page, signal),
    enabled: canRead && !topicId,
  });
  const detail = useQuery({
    queryKey: flowKeys.detail(user?.id, topicId),
    queryFn: ({ signal }) => fetchTopic(topicId!, signal),
    enabled: canRead && !!topicId,
  });
  const { dialog, busy, failure, uncertain, act, submit, cancel, retry } = useFlowActions(
    user?.id,
    topicId,
    detail.data,
  );
  const unitAction = dialog && needsUnits(dialog.code) ? dialog.code : undefined;
  const units = useQuery({
    queryKey: [...flowKeys.units(user?.id), unitAction, topicId, dialog?.receipt?.id],
    queryFn: ({ signal }) =>
      fetchUnits(
        unitAction!,
        unitAction === 'create' ? undefined : topicId,
        dialog?.receipt?.id,
        signal,
      ),
    enabled: !!unitAction,
  });
  const setFilter = (key: string, value: string) => {
    const next = new URLSearchParams(params);
    next.set(key, value);
    if (key !== 'page') next.set('page', '1');
    setParams(next);
  };
  if (!canRead) return <PageHeader title="信息流转" description="当前账号没有信息流转查看权限。" />;
  const queryError = topicId ? detail.error : list.error;
  const columns: TableColumnsType<IntelligenceListItem> = [
    {
      title: '情报',
      key: 'topic',
      render: (_, row) => (
        <div>
          <Button
            type="link"
            className={styles.titleLink}
            onClick={() => navigate(`/collaboration/flows/${row.id}?${params}`)}
          >
            {row.title}
          </Button>
          <div className={styles.meta}>{row.topicNo}</div>
        </div>
      ),
    },
    { title: '原发起单位', dataIndex: 'sourceUnitName', width: 235 },
    {
      title: view === 'sent' ? '发送状态' : '本单位签收',
      key: 'status',
      width: 180,
      render: (_, row) =>
        view === 'sent' ? (
          <Tag color={row.status === 'DRAFT' ? 'default' : 'blue'}>
            {row.status === 'DRAFT' ? '草稿' : '已发出'}
          </Tag>
        ) : (
          <span>
            <Tag color={row.pendingReceiptCount ? 'orange' : 'green'}>
              {row.pendingReceiptCount ? `${row.pendingReceiptCount} 次待签收` : '已签收'}
            </Tag>
            <span className={styles.hint}>共 {row.myReceiptCount} 次送达</span>
          </span>
        ),
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      width: 165,
      render: (value) => <span className={styles.time}>{formatFlowTime(value)}</span>,
    },
  ];
  return (
    <div>
      <PageHeader
        title={topicId ? (detail.data?.title ?? '情报详情') : '信息流转'}
        description={topicId ? undefined : '共享情报，汇集线索。'}
        actions={
          topicId ? (
            <Space>
              <Button
                disabled={busy || uncertain}
                onClick={() => navigate(`/collaboration/flows?${params}`)}
              >
                返回列表
              </Button>
              <Button onClick={() => void detail.refetch()}>刷新</Button>
            </Space>
          ) : (
            hasPermission(user?.permissionCodes, PERMISSIONS.intelCreate) && (
              <Button
                type="primary"
                disabled={busy || uncertain}
                onClick={() => void act({ code: 'create' })}
              >
                新建情报
              </Button>
            )
          )
        }
      />
      <div className={styles.page}>
        {queryError && (
          <Alert
            type="error"
            showIcon
            title={queryError instanceof ApiError ? queryError.message : '加载失败'}
            action={
              <Button
                size="small"
                onClick={() => void (topicId ? detail.refetch() : list.refetch())}
              >
                重试
              </Button>
            }
          />
        )}
        {failure && !dialog && (
          <Alert
            type="error"
            title={failure}
            showIcon
            action={
              uncertain && (
                <Button loading={busy} onClick={() => void retry()}>
                  重试提交
                </Button>
              )
            }
          />
        )}
        {topicId ? (
          detail.isPending ? (
            <Spin />
          ) : (
            detail.data && (
              <TopicDetail
                detail={detail.data}
                onAction={(operation) => void act(operation)}
                busy={busy || uncertain}
              />
            )
          )
        ) : (
          <section className={styles.panel}>
            <Tabs
              activeKey={view}
              onChange={(value) => {
                const next = new URLSearchParams();
                next.set('view', value);
                setParams(next);
              }}
              items={[
                { key: 'received', label: '我收到的' },
                { key: 'sent', label: '我发出的' },
              ]}
            />
            <div className={styles.filters}>
              <Select
                aria-label="筛选状态"
                value={status}
                onChange={(value) => setFilter('status', value)}
                options={
                  view === 'sent'
                    ? [
                        { value: 'all', label: '全部状态' },
                        { value: 'DRAFT', label: '草稿' },
                        { value: 'PUBLISHED', label: '已发出' },
                      ]
                    : [
                        { value: 'all', label: '全部状态' },
                        { value: 'pending', label: '待签收' },
                        { value: 'signed', label: '已签收' },
                      ]
                }
              />
              <Input.Search
                placeholder="搜索标题或编号"
                defaultValue={keyword}
                key={view}
                allowClear
                onSearch={(value) => setFilter('keyword', value)}
              />
            </div>
            <Table
              rowKey="id"
              columns={columns}
              dataSource={list.data?.items ?? []}
              loading={list.isFetching}
              pagination={false}
              scroll={{ x: 900 }}
              locale={{ emptyText: '暂无本单位情报' }}
            />
            <TablePager
              total={list.data?.total ?? 0}
              page={page}
              pageSize={20}
              itemCount={list.data?.items.length ?? 0}
              onPageChange={(value) => setFilter('page', String(value))}
            />
          </section>
        )}
      </div>
      {dialog && (
        <FlowFormDialog
          dialog={dialog}
          options={units.data ?? []}
          unitLoading={units.isFetching}
          unitError={units.error}
          busy={busy}
          locked={uncertain}
          failure={failure}
          onRetry={() => void units.refetch()}
          onCancel={cancel}
          onRetrySubmission={() => void retry()}
          onSubmit={(values) => void submit(values)}
        />
      )}
    </div>
  );
}
