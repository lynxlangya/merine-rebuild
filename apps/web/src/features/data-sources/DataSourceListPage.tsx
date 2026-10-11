import {
  DatabaseOutlined,
  EditOutlined,
  LockOutlined,
  PlusOutlined,
  PoweroffOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons';
import type { DataSourceView } from '@merine/api-contract';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, App, Button, Empty, Input, Pagination, Result, Skeleton, Tag, Tooltip } from 'antd';
import { useId, useState } from 'react';
import { useAuth } from '../auth/public';
import { DICTIONARY_CODES, DictTag, useDictionary } from '../dictionaries/public';
import { errorText, isForbiddenError } from '../../shared/api-error';
import { PERMISSIONS, hasPermission } from '../../shared/permissions';
import { PageHeader } from '../../shared/ui/PageHeader';
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
 * 数据源管理：连接列表是主工作区。
 * 「检查」只影响这里的展示与最近结论；真正的启用动作会在服务端重新检查，不复用页面上的旧结果。
 */
export function DataSourceListPage() {
  const { state } = useAuth();
  const { message, modal } = App.useApp();
  const client = useQueryClient();
  const id = useId();
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
  const changeSearch = (value: string) => {
    setSearch(value.trim());
    setPage(1);
  };
  const clearSearch = () => {
    setDraft('');
    changeSearch('');
  };
  const hasActions = canTest || canUpdate || canToggle;
  return (
    <div className={styles.page}>
      <PageHeader
        demo={false}
        title="数据源管理"
        description="维护 Agent 受控查询的数据库连接；密码加密保存、不回显，启用前必须重新检查只读授权。"
        actions={
          !forbidden && (
            <div className={styles.headActions}>
              <Tooltip title="刷新数据源列表">
                <Button
                  aria-label="刷新数据源列表"
                  icon={<ReloadOutlined spin={query.isFetching} />}
                  disabled={query.isFetching || query.fetchStatus === 'paused'}
                  onClick={() => void query.refetch()}
                />
              </Tooltip>
              {canCreate && (
                <Button
                  type="primary"
                  icon={<PlusOutlined />}
                  onClick={() => setDrawer({ target: null })}
                >
                  新增数据源
                </Button>
              )}
            </div>
          )
        }
      />
      {forbidden ? (
        <Result status="403" title="没有查看数据源的权限" />
      ) : (
        <>
          <section className={styles.configured} aria-labelledby={`${id}-configured`}>
            <div className={styles.sectionHead}>
              <div>
                <h2 id={`${id}-configured`}>
                  {search ? '搜索结果' : '已配置数据源'}
                  {query.data && <span className={styles.count}>{query.data.total}</span>}
                </h2>
                <p>检查只核对只读授权与视图白名单；配置通过不代表已授权 Agent 使用。</p>
              </div>
              <Input.Search
                className={styles.search}
                placeholder="搜索数据源名称"
                aria-label="搜索数据源名称"
                allowClear
                maxLength={80}
                value={draft}
                onChange={(event) => setDraft(event.target.value)}
                onSearch={changeSearch}
              />
            </div>
            {query.fetchStatus === 'paused' && (
              <Alert
                className={styles.alert}
                type="warning"
                showIcon
                title="网络连接已断开，恢复后会自动加载"
              />
            )}
            {query.isError && (
              <Alert
                className={styles.alert}
                type={query.data ? 'warning' : 'error'}
                showIcon
                title={query.data ? '刷新失败，当前显示上次加载的数据源' : '数据源加载失败'}
                description={errorText(query.error)}
                action={
                  <Button size="small" onClick={() => void query.refetch()}>
                    重试
                  </Button>
                }
              />
            )}
            {query.isPending ? (
              <div className={styles.loading}>
                <Skeleton active paragraph={{ rows: 3 }} />
              </div>
            ) : !query.data ? null : items.length === 0 ? (
              <div className={styles.empty}>
                <Empty
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                  description={search ? '没有找到匹配的数据源' : '还没有数据源配置'}
                />
                {search ? (
                  <Button onClick={clearSearch}>清除搜索</Button>
                ) : (
                  <p>
                    {canCreate
                      ? '点击右上角新增；保存后先测试连接、勾选视图，再启用。'
                      : '数据源配置添加后会显示在这里。'}
                  </p>
                )}
              </div>
            ) : (
              <div>
                {items.map((item) => {
                  const views = item.views ?? [];
                  const outcome = outcomeMeta(item.lastCheckStatus);
                  const checking = check.isPending && check.variables === item.id;
                  return (
                    <article key={item.id} className={styles.card} aria-label={item.name}>
                      <div className={styles.cardHead}>
                        <div className={styles.mark} aria-hidden="true">
                          <DatabaseOutlined />
                        </div>
                        <div className={styles.identity}>
                          <div className={styles.nameLine}>
                            <h3>{item.name}</h3>
                            <DictTag dictionary={statusDictionary} value={item.status ?? ''} />
                            {outcome && item.lastCheckAt && (
                              <Tag color={outcome.color} bordered={false}>
                                {outcome.label}
                              </Tag>
                            )}
                          </div>
                          <p className={styles.description}>
                            MySQL
                            {item.remark && (
                              <>
                                <span className={styles.separator}>·</span>
                                {item.remark}
                              </>
                            )}
                          </p>
                        </div>
                        {hasActions && (
                          <div className={styles.cardActions}>
                            {canTest && (
                              <Button
                                size="small"
                                icon={<SafetyCertificateOutlined />}
                                loading={checking}
                                disabled={toggle.isPending}
                                aria-label={`检查${item.name ?? ''}的连接与授权`}
                                onClick={() => check.mutate(item.id ?? '')}
                              >
                                检查
                              </Button>
                            )}
                            {canUpdate && (
                              <Button
                                size="small"
                                icon={<EditOutlined />}
                                aria-label={`编辑${item.name ?? ''}`}
                                disabled={toggle.isPending}
                                onClick={() => setDrawer({ target: item })}
                              >
                                编辑
                              </Button>
                            )}
                            {canToggle &&
                              (item.status === 'ENABLED' ? (
                                <Button
                                  size="small"
                                  icon={<PoweroffOutlined />}
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
                                    views.length === 0 ? '先检查并勾选至少一个允许查询的视图' : ''
                                  }
                                >
                                  <span>
                                    <Button
                                      size="small"
                                      type="primary"
                                      ghost
                                      icon={<PoweroffOutlined />}
                                      aria-label={`启用${item.name ?? ''}`}
                                      loading={toggle.isPending && toggle.variables?.id === item.id}
                                      disabled={views.length === 0 || check.isPending}
                                      onClick={() => confirmEnable(item)}
                                    >
                                      启用
                                    </Button>
                                  </span>
                                </Tooltip>
                              ))}
                          </div>
                        )}
                      </div>
                      <div className={styles.cardBody}>
                        <div className={styles.field}>
                          <span className={styles.fieldLabel}>连接</span>
                          <code className={styles.endpoint}>
                            {item.username}@{item.host}:{item.port}/{item.databaseName}
                          </code>
                          <span className={styles.keyHint}>
                            <LockOutlined aria-hidden="true" />
                            密码已加密保存，不回显
                          </span>
                        </div>
                        <div className={styles.field}>
                          <span className={styles.fieldLabel}>
                            允许查询的视图
                            <span className={styles.count}>{views.length}</span>
                          </span>
                          {views.length > 0 ? (
                            <div className={styles.views} aria-label={`${item.name ?? ''} 的视图`}>
                              {views.map((view) => (
                                <Tag key={view} bordered={false} className={styles.viewTag}>
                                  {view}
                                </Tag>
                              ))}
                            </div>
                          ) : (
                            <p className={styles.muted}>尚未勾选视图，当前不允许启用</p>
                          )}
                        </div>
                        <div className={styles.field}>
                          <span className={styles.fieldLabel}>最近检查</span>
                          {item.lastCheckAt && item.lastCheckStatus ? (
                            <>
                              <span className={styles.checkedAt}>
                                {formatCheckedAt(item.lastCheckAt)}
                                <span className={styles.separator}>·</span>
                                {outcome?.label}
                              </span>
                              <p className={styles.checkMessage}>
                                {item.lastCheckMessage || outcome?.hint}
                              </p>
                            </>
                          ) : (
                            <p className={styles.muted}>
                              尚未检查；编辑连接参数或视图后，旧结论会立即失效
                            </p>
                          )}
                        </div>
                      </div>
                    </article>
                  );
                })}
              </div>
            )}
            {query.data && query.data.total > 12 && (
              <div className={styles.pagination}>
                <span>共 {query.data.total} 个数据源</span>
                <Pagination
                  current={page}
                  pageSize={12}
                  total={query.data.total}
                  showSizeChanger={false}
                  onChange={setPage}
                />
              </div>
            )}
          </section>
          <p className={styles.note}>
            <SafetyCertificateOutlined aria-hidden="true" />
            连接测试只读取授权元数据，不执行任何写操作；勾选视图不会修改数据库授权。
          </p>
        </>
      )}
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
