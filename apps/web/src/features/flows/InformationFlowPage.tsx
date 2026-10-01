import { useQuery } from '@tanstack/react-query';
import { useParams, useSearchParams } from 'react-router';
import { Alert, Button, Empty, Spin, Table, Tabs } from 'antd';
import type { TableColumnsType } from 'antd';
import type { IntelligenceListItem } from '@merine/api-contract';
import { PageHeader } from '../../shared/ui/PageHeader';
import { TablePager } from '../../shared/ui/TablePager';
import { errorText, isForbiddenError } from '../../shared/api-error';
import { DetailBackLink } from '../../shared/ui/DetailBackLink';
import { StatusTag } from '../../shared/ui/StatusTag';
import { RecordTitleLink } from '../../shared/ui/RecordTitleLink';
import tableStyles from '../../shared/ui/RecordTable.module.css';
import { FlowFilters } from './components/FlowFilters';
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
  const { topicId } = useParams();
  const [params, setParams] = useSearchParams();
  const view = params.get('view') === 'sent' ? 'sent' : 'received';
  const statuses = view === 'sent' ? ['all', 'DRAFT', 'PUBLISHED'] : ['all', 'pending', 'signed'];
  const status = statuses.includes(params.get('status') ?? '') ? params.get('status')! : 'all';
  const requestedPage = Number(params.get('page'));
  const page = Number.isInteger(requestedPage) && requestedPage > 0 ? requestedPage : 1;
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
  if (!canRead)
    return (
      <>
        <PageHeader demo={false} title="信息流转" />
        <div className={styles.page}>
          <Alert type="warning" title="当前账号没有信息流转查看权限" />
        </div>
      </>
    );

  const columns: TableColumnsType<IntelligenceListItem> = [
    {
      title: '情报',
      key: 'topic',
      width: 430,
      render: (_, row) => (
        <RecordTitleLink to={`/collaboration/flows/${row.id}?${params}`} number={row.topicNo}>
          {row.title}
        </RecordTitleLink>
      ),
    },
    {
      title: view === 'sent' ? '发送状态' : '本单位签收',
      key: 'status',
      width: 180,
      render: (_, row) =>
        view === 'sent' ? (
          <StatusTag
            tone={row.status === 'DRAFT' ? 'neutral' : 'accent'}
            label={row.status === 'DRAFT' ? '草稿' : '已发出'}
          />
        ) : (
          <div className={styles.statusCell}>
            <StatusTag
              tone={row.pendingReceiptCount ? 'warning' : 'success'}
              label={row.pendingReceiptCount ? `${row.pendingReceiptCount} 次待签收` : '已签收'}
            />
            <span className={styles.hint}>共 {row.myReceiptCount} 次送达</span>
          </div>
        ),
    },
    { title: '原发起单位', dataIndex: 'sourceUnitName', width: 235 },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      width: 180,
      render: (value) => <span className={styles.time}>{formatFlowTime(value)}</span>,
    },
  ];
  return (
    <div>
      {topicId && (
        <DetailBackLink to={`/collaboration/flows?${params}`}>返回情报列表</DetailBackLink>
      )}
      <PageHeader
        demo={false}
        title={topicId ? (detail.data?.title ?? '情报详情') : '信息流转'}
        description={topicId ? undefined : '共享情报，汇集线索。'}
        actions={
          topicId ? (
            <Button
              loading={detail.isFetching}
              disabled={busy || uncertain}
              onClick={() => void detail.refetch()}
            >
              刷新
            </Button>
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
          ) : detail.isError ? (
            <Alert
              type="error"
              showIcon
              title={errorText(detail.error)}
              action={
                <Button loading={detail.isFetching} onClick={() => void detail.refetch()}>
                  重试
                </Button>
              }
            />
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
          <>
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
            <FlowFilters
              key={`${view}:${status}:${keyword}`}
              view={view}
              status={status}
              keyword={keyword}
              onSearch={(nextStatus, nextKeyword) => {
                const next = new URLSearchParams(params);
                next.set('status', nextStatus);
                next.set('keyword', nextKeyword);
                next.set('page', '1');
                setParams(next);
              }}
            />
            <section className={tableStyles.frame}>
              <div className={tableStyles.toolbar}>
                <h2>{view === 'sent' ? '我发出的情报' : '我收到的情报'}</h2>
                <Button loading={list.isFetching} onClick={() => void list.refetch()}>
                  刷新
                </Button>
              </div>
              {list.isError && (
                <Alert
                  type="error"
                  showIcon
                  className={tableStyles.error}
                  title={
                    list.data && !isForbiddenError(list.error)
                      ? '刷新失败，当前显示上次加载的数据'
                      : '情报加载失败'
                  }
                  description={errorText(list.error)}
                  action={
                    <Button loading={list.isFetching} onClick={() => void list.refetch()}>
                      重试
                    </Button>
                  }
                />
              )}
              {list.isPending ? (
                <div className={styles.loading}>
                  <Spin />
                </div>
              ) : (
                list.data &&
                !isForbiddenError(list.error) && (
                  <>
                    <Table
                      rowKey="id"
                      columns={columns}
                      dataSource={list.data?.items ?? []}
                      loading={list.isFetching}
                      pagination={false}
                      size="small"
                      tableLayout="fixed"
                      scroll={{ x: 1025 }}
                      locale={{
                        emptyText: (
                          <Empty
                            description={
                              keyword || status !== 'all'
                                ? '没有符合查询条件的情报'
                                : view === 'sent'
                                  ? '暂无我发出的情报'
                                  : '暂无我收到的情报'
                            }
                          />
                        ),
                      }}
                    />
                    <TablePager
                      total={list.data?.total ?? 0}
                      page={page}
                      pageSize={20}
                      itemCount={list.data?.items.length ?? 0}
                      onPageChange={(value) => setFilter('page', String(value))}
                    />
                  </>
                )
              )}
            </section>
          </>
        )}
      </div>
      {dialog && (
        <FlowFormDialog
          dialog={dialog}
          topicId={topicId}
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
