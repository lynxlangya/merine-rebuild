import { ReloadOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Empty, Input, Result, Skeleton } from 'antd';
import { useState } from 'react';
import { Link } from 'react-router';
import { errorText, isForbiddenError } from '../../shared/api-error';
import { PERMISSIONS, hasPermission } from '../../shared/permissions';
import { PageHeader } from '../../shared/ui/PageHeader';
import { TablePager } from '../../shared/ui/TablePager';
import collection from '../../shared/ui/ResourceCollection.module.css';
import { useAuth } from '../auth/public';
import { fetchQueryScopes, queryScopeKeys } from './api';
import { QueryScopeCard } from './components/QueryScopeCard';

/** 范围目录只展示保存的白名单，不因进入列表而连接目标库。 */
export function QueryScopePage() {
  const { state } = useAuth();
  const codes = state.status === 'authenticated' ? state.user.permissionCodes : [];
  const canRead = hasPermission(codes, PERMISSIONS.scopeRead);
  const canUpdate = hasPermission(codes, PERMISSIONS.scopeUpdate);
  const canReadSource = hasPermission(codes, PERMISSIONS.datasourceRead);
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

  function changeSearch(value: string) {
    setSearch(value.trim());
    setPage(1);
  }
  function clearSearch() {
    setDraft('');
    changeSearch('');
  }

  return (
    <div>
      <PageHeader
        demo={false}
        title="查询范围"
        description="选择允许查询的视图，定义视图与字段说明。"
      />
      <div className={collection.page}>
        {forbidden ? (
          <div className={collection.state}>
            <Result status="403" title="没有查看查询范围的权限" />
          </div>
        ) : (
          <>
            <div className={collection.toolbar}>
              <div className={collection.heading}>
                <h2>{hasFilters ? '搜索结果' : '视图目录'}</h2>
                <span className={collection.total}>
                  {query.data ? `${query.data.total} 个数据源` : '—'}
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
                title={query.data ? '刷新失败，当前显示上次结果' : '查询范围加载失败'}
                description={errorText(query.error)}
                action={
                  <Button size="small" onClick={() => void query.refetch()}>
                    重试
                  </Button>
                }
              />
            )}
            <section aria-label="数据源视图目录" aria-busy={query.isFetching}>
              {query.isPending ? (
                <div className={collection.grid} role="status" aria-label="正在加载查询范围">
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
                      <QueryScopeCard item={item} canUpdate={canUpdate} />
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
                            ? '本页暂无范围'
                            : '尚未配置数据源'
                      }
                    >
                      {hasFilters ? (
                        <Button onClick={clearSearch}>清空搜索</Button>
                      ) : query.data?.total ? (
                        <Button onClick={() => setPage(1)}>返回第一页</Button>
                      ) : (
                        canReadSource && <Link to="/agent/datasources">配置数据库连接 →</Link>
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
    </div>
  );
}
