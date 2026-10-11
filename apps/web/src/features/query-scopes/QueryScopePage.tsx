import { ReloadOutlined, SearchOutlined, TableOutlined } from '@ant-design/icons';
import type { DataSourceView } from '@merine/api-contract';
import { useQuery } from '@tanstack/react-query';
import type { TableColumnsType } from 'antd';
import { Alert, Button, Input, Result, Skeleton, Table, Tag, Typography } from 'antd';
import { useState } from 'react';
import { useNavigate } from 'react-router';
import { errorText, isForbiddenError } from '../../shared/api-error';
import { PERMISSIONS, hasPermission } from '../../shared/permissions';
import { PageHeader } from '../../shared/ui/PageHeader';
import { TablePager } from '../../shared/ui/TablePager';
import { useAuth } from '../auth/public';
import { fetchQueryScopes, queryScopeKeys } from './api';
import { formatCheckedAt, outcomeMeta } from './model';
import styles from './QueryScopePage.module.css';

/**
 * 查询范围：维护 Agent 允许查询的视图白名单与视图/字段说明，与数据源管理保持同一套列表版式。
 *
 * 保存范围会清除检查结果并停用数据源（需回数据源管理复检启用），说明变化只递增语义版本。
 * 列表只读本项目配置库；探测候选视图与字段、新增字段说明需要实时连接目标库。
 */
export function QueryScopePage() {
  const { state } = useAuth();
  const codes = state.status === 'authenticated' ? state.user.permissionCodes : [];
  const canRead = hasPermission(codes, PERMISSIONS.scopeRead);
  const canUpdate = hasPermission(codes, PERMISSIONS.scopeUpdate);
  const navigate = useNavigate();
  const [search, setSearch] = useState('');
  const [draft, setDraft] = useState('');
  const [page, setPage] = useState(1);
  const query = useQuery({
    queryKey: [...queryScopeKeys, 'list', search, page],
    queryFn: ({ signal }) => fetchQueryScopes(search, page, signal),
    enabled: canRead,
  });
  const forbidden = !canRead || isForbiddenError(query.error);
  const items = query.data?.items ?? [];
  const hasFilters = search !== '';

  const changeSearch = (value: string) => {
    setSearch(value.trim());
    setPage(1);
  };
  const clearSearch = () => {
    setDraft('');
    changeSearch('');
  };
  const columns: TableColumnsType<DataSourceView> = [
    {
      title: '数据源',
      dataIndex: 'name',
      key: 'name',
      width: 300,
      render: (value: string, item) => (
        <div className={styles.ident}>
          <span className={styles.nameLine}>
            <b>{value}</b>
            {item.remark && <span className={styles.muted}>· {item.remark}</span>}
          </span>
          <Typography.Text className={styles.idText}>{item.id}</Typography.Text>
        </div>
      ),
    },
    {
      title: '状态',
      key: 'status',
      width: 110,
      render: (_value, item) => (
        <Tag color={item.status === 'ENABLED' ? 'success' : 'default'} bordered={false}>
          {item.status === 'ENABLED' ? '启用' : '停用'}
        </Tag>
      ),
    },
    {
      title: '允许视图',
      key: 'views',
      width: 110,
      align: 'center',
      render: (_value, item) => (
        <span className={styles.countCell}>
          <TableOutlined aria-hidden="true" />
          {item.views?.length ?? 0}
        </span>
      ),
    },
    {
      title: '版本',
      key: 'version',
      width: 150,
      render: (_value, item) => (
        <span className={styles.mono}>
          v{item.version ?? 0} · 语义 v{item.semanticVersion ?? 0}
        </span>
      ),
    },
    {
      title: '最近检查',
      key: 'check',
      width: 200,
      render: (_value, item) => {
        const outcome = outcomeMeta(item.lastCheckStatus);
        if (!item.lastCheckAt || !outcome) {
          return <span className={styles.muted}>尚未检查</span>;
        }
        return (
          <span className={styles.checkCell}>
            <Tag color={outcome.color} bordered={false}>
              {outcome.label}
            </Tag>
            <span className={styles.muted}>{formatCheckedAt(item.lastCheckAt)}</span>
          </span>
        );
      },
    },
    {
      title: '操作',
      key: 'actions',
      align: 'right',
      width: 140,
      render: (_value, item) => (
        <Button
          type="text"
          size="small"
          onClick={() => void navigate(`/agent/query-scopes/${item.id ?? ''}`)}
        >
          {canUpdate ? '维护范围' : '查看范围'}
        </Button>
      ),
    },
  ];

  return (
    <div>
      <PageHeader
        demo={false}
        title="查询范围"
        description="维护 Agent 允许查询的视图与视图/字段说明；保存范围会停用数据源，说明变化只递增语义版本。"
      />
      <div className={styles.page}>
        {forbidden ? (
          <div className={styles.stateCard}>
            <Result
              status="403"
              title="没有查看查询范围的权限"
              subTitle="查询范围需要具备查询范围查看权限的账号。这里不显示任何数据源配置，也不会因此退出登录。"
            />
          </div>
        ) : (
          <>
            <div className={styles.filters}>
              <label className={styles.field}>
                <span className={styles.label}>数据源名称</span>
                <Input
                  allowClear
                  maxLength={80}
                  placeholder="搜索数据源名称"
                  aria-label="搜索数据源名称"
                  value={draft}
                  onChange={(event) => setDraft(event.target.value)}
                  onPressEnter={() => changeSearch(draft)}
                />
              </label>
              <div className={styles.filterActions}>
                <Button
                  type="primary"
                  icon={<SearchOutlined />}
                  onClick={() => changeSearch(draft)}
                >
                  查询
                </Button>
                <Button onClick={clearSearch}>重置</Button>
              </div>
            </div>

            <div className={styles.panel}>
              <div className={styles.toolbar}>
                <b className={styles.toolbarTitle}>{hasFilters ? '搜索结果' : '数据源范围'}</b>
                <Tag className={styles.countTag}>
                  {query.isPending ? '—' : (query.data?.total ?? 0)}
                </Tag>
                <span className={styles.spacer} />
                <Button
                  size="small"
                  icon={<ReloadOutlined />}
                  loading={query.isFetching}
                  disabled={query.fetchStatus === 'paused'}
                  onClick={() => void query.refetch()}
                >
                  刷新
                </Button>
              </div>
              <div className={styles.tableWrap}>
                {query.fetchStatus === 'paused' && (
                  <Alert
                    className={styles.banner}
                    type="warning"
                    showIcon
                    title="网络连接已断开，恢复后会自动加载"
                  />
                )}
                {query.isError && (
                  <Alert
                    className={styles.banner}
                    type={query.data ? 'warning' : 'error'}
                    showIcon
                    title={query.data ? '刷新失败，以下为上一次的结果' : '查询范围加载失败'}
                    description={errorText(query.error)}
                    action={
                      <Button size="small" onClick={() => void query.refetch()}>
                        重试
                      </Button>
                    }
                  />
                )}
                {query.isPending ? (
                  <div className={styles.skeleton} role="status" aria-live="polite">
                    <Skeleton active title={false} paragraph={{ rows: 5 }} />
                    <span className={styles.skeletonText}>正在加载查询范围…</span>
                  </div>
                ) : (
                  <Table<DataSourceView>
                    rowKey="id"
                    size="small"
                    tableLayout="fixed"
                    scroll={{ x: 1100 }}
                    loading={query.isFetching && !query.isError}
                    dataSource={items}
                    columns={columns}
                    pagination={false}
                    locale={{
                      emptyText: hasFilters ? (
                        <span className={styles.emptyText}>
                          <span>没有找到匹配的数据源</span>
                          <span className={styles.emptyHint}>
                            可以更换关键字，或清空筛选查看全部配置。
                          </span>
                          <Button type="link" size="small" onClick={clearSearch}>
                            清空筛选条件
                          </Button>
                        </span>
                      ) : (
                        <span className={styles.emptyText}>
                          <span>还没有数据源配置</span>
                          <span className={styles.emptyHint}>
                            先在「数据源管理」新增连接并检查授权，再回到这里勾选允许查询的视图。
                          </span>
                        </span>
                      ),
                    }}
                  />
                )}
              </div>
              {query.data && (
                <TablePager
                  total={query.data.total}
                  page={page}
                  pageSize={query.data.pageSize}
                  itemCount={items.length}
                  onPageChange={setPage}
                />
              )}
            </div>

            <p className={styles.note}>
              <span aria-hidden="true">·</span>
              列表只读本项目配置；「维护范围」里的探测与保存说明会按需连接目标库并复用数据源测试连接权限。
            </p>
          </>
        )}
      </div>
    </div>
  );
}
