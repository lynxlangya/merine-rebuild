import { DatabaseOutlined, PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import type { DataSourceView } from '@merine/api-contract';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { TableColumnsType } from 'antd';
import { Alert, App, Button, Input, Result, Skeleton, Table, Tag, Tooltip, Typography } from 'antd';
import { useState } from 'react';
import { errorText, isForbiddenError } from '../../shared/api-error';
import { PERMISSIONS, hasPermission } from '../../shared/permissions';
import { PageHeader } from '../../shared/ui/PageHeader';
import { TablePager } from '../../shared/ui/TablePager';
import { useAuth } from '../auth/public';
import { DICTIONARY_CODES, DictTag, useDictionary } from '../dictionaries/public';
import {
  changeDataSourceStatus,
  checkDataSourceSaved,
  dataSourceKeys,
  fetchDataSources,
} from './api';
import { formatCheckedAt, outcomeMeta } from './model';
import { DataSourceFormDrawer } from './DataSourceFormDrawer';
import styles from './DataSourceListPage.module.css';

/**
 * 数据源管理：连接列表是主工作区，与用户管理的列表页保持同一套版式。
 * 「检查」只影响这里的展示与最近结论；真正的启用动作会在服务端重新检查，不复用页面上的旧结果。
 */
export function DataSourceListPage() {
  const { state } = useAuth();
  const { message, modal } = App.useApp();
  const client = useQueryClient();
  const codes = state.status === 'authenticated' ? state.user.permissionCodes : [];
  const canRead = hasPermission(codes, PERMISSIONS.datasourceRead),
    canCreate = hasPermission(codes, PERMISSIONS.datasourceCreate),
    canUpdate = hasPermission(codes, PERMISSIONS.datasourceUpdate),
    canToggle = hasPermission(codes, PERMISSIONS.datasourceToggleStatus),
    canTest = hasPermission(codes, PERMISSIONS.datasourceTest);
  const [search, setSearch] = useState('');
  const [draft, setDraft] = useState('');
  const [page, setPage] = useState(1);
  const [drawer, setDrawer] = useState<{ target: DataSourceView | null } | null>(null);
  const statusDictionary = useDictionary(DICTIONARY_CODES.status).data;
  const query = useQuery({
    queryKey: [...dataSourceKeys, search, page],
    queryFn: ({ signal }) => fetchDataSources(search, page, signal),
    enabled: canRead,
  });
  const check = useMutation({
    mutationFn: checkDataSourceSaved,
    onSuccess: (result) => {
      const meta = outcomeMeta(result.outcome);
      if (result.outcome === 'PASSED') message.success(`检查通过：${result.message ?? ''}`);
      else message.warning(`检查未通过（${meta?.label ?? '未知结论'}）：${result.message ?? ''}`);
      void client.invalidateQueries({ queryKey: dataSourceKeys });
    },
    onError: (error) => message.error(errorText(error)),
  });
  const toggle = useMutation({
    mutationFn: (input: { id: string; status: 'ENABLED' | 'DISABLED'; version: number }) =>
      changeDataSourceStatus(input.id, { status: input.status, version: input.version }),
    onSuccess: (view) => {
      message.success(view.status === 'ENABLED' ? '数据源已启用' : '数据源已停用');
      void client.invalidateQueries({ queryKey: dataSourceKeys });
    },
  });
  const confirmEnable = (item: DataSourceView) =>
    modal.confirm({
      title: `检查并启用「${item.name ?? ''}」？`,
      content:
        '服务端会重新测试连接并检查只读授权；只有检查通过且已勾选视图才会启用。检查通过只代表当时状态。',
      okText: '检查并启用',
      cancelText: '取消',
      onOk: async () => {
        try {
          await toggle.mutateAsync({
            id: item.id ?? '',
            status: 'ENABLED',
            version: item.version ?? 0,
          });
        } catch (error) {
          message.error(errorText(error));
          throw error;
        }
      },
    });
  const disable = (item: DataSourceView) => {
    void toggle
      .mutateAsync({ id: item.id ?? '', status: 'DISABLED', version: item.version ?? 0 })
      .catch((error) => message.error(errorText(error)));
  };
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
      title: '名称',
      dataIndex: 'name',
      key: 'name',
      width: 280,
      render: (value: string, item) => (
        <div className={styles.ident}>
          <span className={styles.nameLine}>
            <b>{value}</b>
            {item.remark && <span className={styles.muted}>· {item.remark}</span>}
          </span>
          <Typography.Text
            className={styles.idText}
            copyable={{ text: item.id ?? '', tooltips: ['复制数据源 ID', '已复制'] }}
          >
            {item.id}
          </Typography.Text>
        </div>
      ),
    },
    {
      title: '连接',
      key: 'endpoint',
      width: 250,
      render: (_value, item) => (
        <span className={styles.mono}>
          {item.host}:{item.port}/{item.databaseName}
        </span>
      ),
    },
    {
      title: '账号',
      dataIndex: 'username',
      key: 'username',
      width: 160,
      render: (value: string) => <span className={styles.mono}>{value}</span>,
    },
    {
      title: '允许视图',
      key: 'views',
      width: 100,
      align: 'center',
      render: (_value, item) => (
        <span className={styles.countCell}>
          <DatabaseOutlined aria-hidden="true" />
          {item.views?.length ?? 0}
        </span>
      ),
    },
    {
      title: '状态',
      key: 'status',
      width: 120,
      render: (_value, item) => <DictTag dictionary={statusDictionary} value={item.status ?? ''} />,
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
    ...(canTest || canUpdate || canToggle
      ? [
          {
            title: '操作',
            key: 'actions',
            align: 'right' as const,
            width: 210,
            render: (_value: unknown, item: DataSourceView) => {
              const views = item.views ?? [];
              return (
                <span className={styles.actions}>
                  {canUpdate && (
                    <Button
                      type="text"
                      size="small"
                      aria-label={`编辑${item.name ?? ''}`}
                      disabled={toggle.isPending}
                      onClick={() => setDrawer({ target: item })}
                    >
                      编辑
                    </Button>
                  )}
                  {canTest && (
                    <Button
                      type="text"
                      size="small"
                      aria-label={`检查${item.name ?? ''}的连接与授权`}
                      loading={check.isPending && check.variables === item.id}
                      disabled={toggle.isPending}
                      onClick={() => check.mutate(item.id ?? '')}
                    >
                      检查
                    </Button>
                  )}
                  {canToggle &&
                    (item.status === 'ENABLED' ? (
                      <Button
                        type="text"
                        size="small"
                        danger
                        aria-label={`停用${item.name ?? ''}`}
                        loading={toggle.isPending && toggle.variables?.id === item.id}
                        disabled={check.isPending}
                        onClick={() => disable(item)}
                      >
                        停用
                      </Button>
                    ) : (
                      <Tooltip
                        title={
                          views.length === 0 ? '先在「查询范围」勾选至少一个允许查询的视图' : ''
                        }
                      >
                        <Button
                          type="text"
                          size="small"
                          aria-label={`启用${item.name ?? ''}`}
                          loading={toggle.isPending && toggle.variables?.id === item.id}
                          disabled={views.length === 0 || check.isPending}
                          onClick={() => confirmEnable(item)}
                        >
                          启用
                        </Button>
                      </Tooltip>
                    ))}
                </span>
              );
            },
          },
        ]
      : []),
  ];

  return (
    <div>
      <PageHeader
        demo={false}
        title="数据源管理"
        description="维护 Agent 受控查询的数据库连接；密码加密保存、不回显，启用前必须重新检查只读授权。"
        actions={
          !forbidden &&
          canCreate && (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => setDrawer({ target: null })}
            >
              新增数据源
            </Button>
          )
        }
      />
      <div className={styles.page}>
        {forbidden ? (
          <div className={styles.stateCard}>
            <Result
              status="403"
              title="没有查看数据源的权限"
              subTitle="数据源管理需要具备智能体管理权限的账号。这里不显示任何连接信息，也不会因此退出登录。"
            />
          </div>
        ) : (
          <>
            <div className={styles.filters}>
              <label className={styles.field}>
                <span className={styles.label}>名称</span>
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
                <b className={styles.toolbarTitle}>{hasFilters ? '搜索结果' : '数据源列表'}</b>
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
                    title={query.data ? '刷新失败，以下为上一次的结果' : '数据源加载失败'}
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
                    <span className={styles.skeletonText}>正在加载数据源列表…</span>
                  </div>
                ) : (
                  <Table<DataSourceView>
                    rowKey="id"
                    size="small"
                    tableLayout="fixed"
                    scroll={{ x: 1320 }}
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
                            {canCreate
                              ? '可以用右上角「新增数据源」创建第一个配置；保存后到「查询范围」勾选视图，再复检启用。'
                              : '数据源配置添加后会显示在这里。'}
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
              检查只读取授权元数据，不执行任何写操作；勾选视图不会修改数据库授权。配置通过不代表已授权
              Agent 使用。
            </p>
          </>
        )}
      </div>
      {drawer && (
        <DataSourceFormDrawer
          key={drawer.target?.id ?? 'create'}
          target={drawer.target}
          onClose={() => setDrawer(null)}
        />
      )}
    </div>
  );
}
