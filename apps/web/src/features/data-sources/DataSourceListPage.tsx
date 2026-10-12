import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import type { DataSourceView } from '@merine/api-contract';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, App, Button, Empty, Input, Result, Skeleton, Tooltip } from 'antd';
import { useState } from 'react';
import { errorText, isForbiddenError } from '../../shared/api-error';
import { PERMISSIONS, hasPermission } from '../../shared/permissions';
import { PageHeader } from '../../shared/ui/PageHeader';
import { TablePager } from '../../shared/ui/TablePager';
import collection from '../../shared/ui/ResourceCollection.module.css';
import { useAuth } from '../auth/public';
import {
  changeDataSourceStatus,
  checkDataSourceSaved,
  dataSourceKeys,
  fetchDataSources,
} from './api';
import { outcomeMeta } from './model';
import { DataSourceFormDrawer } from './DataSourceFormDrawer';
import { DataSourceCard } from './components/DataSourceCard';

/** 连接管理：保存配置、检查只读授权与启停；查询范围使用独立入口。 */
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
  const canReadScope = hasPermission(codes, PERMISSIONS.scopeRead);
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
  return (
    <div>
      <PageHeader
        demo={false}
        title="数据源管理"
        description="连接数据库，检查只读授权。"
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
      <div className={collection.page}>
        {forbidden ? (
          <div className={collection.state}>
            <Result status="403" title="没有查看数据源的权限" />
          </div>
        ) : (
          <>
            <div className={collection.toolbar}>
              <div className={collection.heading}>
                <h2>{hasFilters ? '搜索结果' : '数据库连接'}</h2>
                <span className={collection.total}>
                  {query.data ? `${query.data.total} 个` : '—'}
                </span>
              </div>
              <Input.Search
                className={collection.search}
                allowClear
                maxLength={80}
                placeholder="搜索数据源"
                aria-label="搜索数据源名称"
                value={draft}
                onChange={(event) => {
                  setDraft(event.target.value);
                  if (!event.target.value) changeSearch('');
                }}
                onSearch={changeSearch}
              />
              {hasFilters && (
                <Button type="text" onClick={clearSearch}>
                  清空
                </Button>
              )}
              <Button
                icon={<ReloadOutlined />}
                loading={query.isFetching}
                disabled={query.fetchStatus === 'paused'}
                onClick={() => void query.refetch()}
              >
                刷新
              </Button>
            </div>
            {query.fetchStatus === 'paused' && (
              <Alert type="warning" showIcon title="网络已断开，恢复后自动加载" />
            )}
            {query.isError && (
              <Alert
                type={query.data ? 'warning' : 'error'}
                showIcon
                title={query.data ? '刷新失败，当前显示上次结果' : '数据源加载失败'}
                description={errorText(query.error)}
                action={
                  <Button size="small" onClick={() => void query.refetch()}>
                    重试
                  </Button>
                }
              />
            )}
            <section aria-label="数据库连接" aria-busy={query.isFetching}>
              {query.isPending ? (
                <div className={collection.grid} role="status" aria-label="正在加载数据源">
                  {[0, 1].map((key) => (
                    <div key={key} className={`${collection.card} ${collection.skeleton}`}>
                      <Skeleton active paragraph={{ rows: 6 }} />
                    </div>
                  ))}
                </div>
              ) : items.length > 0 ? (
                <ul className={collection.grid}>
                  {items.map((item) => (
                    <li key={item.id}>
                      <DataSourceCard
                        item={item}
                        scopeHref={
                          canReadScope
                            ? `/agent/query-scopes/${encodeURIComponent(item.id ?? '')}`
                            : undefined
                        }
                        actions={
                          (canUpdate || canTest || canToggle) && (
                            <>
                              {canUpdate && (
                                <Button
                                  size="small"
                                  disabled={toggle.isPending || check.isPending}
                                  aria-label={`编辑${item.name}`}
                                  onClick={() => setDrawer({ target: item })}
                                >
                                  编辑连接
                                </Button>
                              )}
                              {canTest && (
                                <Button
                                  size="small"
                                  disabled={toggle.isPending || check.isPending}
                                  loading={check.isPending && check.variables === item.id}
                                  aria-label={`检查${item.name}的连接与授权`}
                                  onClick={() => check.mutate(item.id ?? '')}
                                >
                                  检查连接
                                </Button>
                              )}
                              {canToggle &&
                                (item.status === 'ENABLED' ? (
                                  <Button
                                    type="text"
                                    size="small"
                                    danger
                                    aria-label={`停用${item.name}`}
                                    loading={toggle.isPending && toggle.variables?.id === item.id}
                                    disabled={check.isPending || toggle.isPending}
                                    onClick={() => disable(item)}
                                  >
                                    停用
                                  </Button>
                                ) : (
                                  <Tooltip
                                    title={
                                      !item.views?.length
                                        ? '先在查询范围中选择至少一个视图'
                                        : '启用时会重新检查只读授权'
                                    }
                                  >
                                    <Button
                                      type="primary"
                                      size="small"
                                      aria-label={`检查并启用${item.name}`}
                                      loading={toggle.isPending && toggle.variables?.id === item.id}
                                      disabled={
                                        !item.views?.length || check.isPending || toggle.isPending
                                      }
                                      onClick={() => confirmEnable(item)}
                                    >
                                      启用
                                    </Button>
                                  </Tooltip>
                                ))}
                            </>
                          )
                        }
                      />
                    </li>
                  ))}
                </ul>
              ) : (
                !query.isError && (
                  <div className={collection.state}>
                    <Empty
                      description={
                        hasFilters
                          ? '没有匹配的数据源'
                          : query.data?.total
                            ? '本页暂无连接'
                            : '添加第一个数据库连接'
                      }
                    >
                      {hasFilters ? (
                        <Button onClick={clearSearch}>清空搜索</Button>
                      ) : query.data?.total ? (
                        <Button onClick={() => setPage(1)}>返回第一页</Button>
                      ) : (
                        canCreate && (
                          <Button
                            type="primary"
                            icon={<PlusOutlined />}
                            onClick={() => setDrawer({ target: null })}
                          >
                            新增数据源
                          </Button>
                        )
                      )}
                    </Empty>
                  </div>
                )
              )}
            </section>
            {query.data && (query.data.total > query.data.pageSize || page > 1) && (
              <div className={collection.pagination}>
                <TablePager
                  total={query.data.total}
                  page={page}
                  pageSize={query.data.pageSize}
                  itemCount={items.length}
                  onPageChange={setPage}
                />
              </div>
            )}
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
