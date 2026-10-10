import {
  DeleteOutlined,
  EditOutlined,
  KeyOutlined,
  LinkOutlined,
  PlusOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import type { ModelProvider } from '@merine/api-contract';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, App, Button, Empty, Input, Pagination, Result, Skeleton, Tag, Tooltip } from 'antd';
import { useId, useState } from 'react';
import { useAuth } from '../auth/public';
import { DICTIONARY_CODES, DictTag, dictLabel, useDictionary } from '../dictionaries/public';
import { PERMISSIONS, hasPermission } from '../../shared/permissions';
import { errorText, isForbiddenError } from '../../shared/api-error';
import { PageHeader } from '../../shared/ui/PageHeader';
import { fetchProviders, deleteProvider, providerKeys } from './api';
import { modelOptionKeys } from './queries';
import { providerTemplates, templateFor } from './templates';
import { ProviderIcon } from './ProviderIcon';
import { ProviderFormDrawer } from './ProviderFormDrawer';
import styles from './ModelProvidersPage.module.css';

/** 设置和后台共用：连接列表是主工作区，模板只负责创建表单的起点。 */
export function ModelProvidersPage() {
  const { state } = useAuth();
  const { modal, message } = App.useApp();
  const client = useQueryClient();
  const id = useId();
  const codes = state.status === 'authenticated' ? state.user.permissionCodes : [];
  const canRead = hasPermission(codes, PERMISSIONS.providerRead),
    canCreate = hasPermission(codes, PERMISSIONS.providerCreate),
    canUpdate = hasPermission(codes, PERMISSIONS.providerUpdate),
    canDelete = hasPermission(codes, PERMISSIONS.providerDelete);
  const [search, setSearch] = useState('');
  const [draft, setDraft] = useState('');
  const [page, setPage] = useState(1);
  const [drawer, setDrawer] = useState<{
    target: ModelProvider | null;
    vendor: ModelProvider['vendor'];
  } | null>(null);
  const statusDictionary = useDictionary(DICTIONARY_CODES.status).data;
  const query = useQuery({
    queryKey: [...providerKeys, search, page],
    queryFn: ({ signal }) => fetchProviders(search, page, signal),
    enabled: canRead,
  });
  const remove = useMutation({
    mutationFn: deleteProvider,
    onSuccess: () => {
      message.success('模型连接已删除');
      if ((query.data?.items.length ?? 0) === 1 && page > 1) setPage(page - 1);
      void client.invalidateQueries({ queryKey: providerKeys });
      void client.invalidateQueries({ queryKey: modelOptionKeys.all });
    },
  });
  const confirmDelete = (item: ModelProvider) =>
    modal.confirm({
      title: `删除“${item.name}”？`,
      content: '这会删除该连接及其模型配置和密钥，聊天选择器中也不再出现。重新添加时需要再次填写。',
      okText: '删除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await remove.mutateAsync(item);
        } catch (error) {
          message.error(errorText(error));
          throw error;
        }
      },
    });
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
  return (
    <div className={styles.page}>
      <PageHeader
        demo={false}
        title="模型连接"
        description="管理模型服务的连接、可用模型与访问凭据。"
        actions={
          !forbidden && (
            <div className={styles.headActions}>
              <Tooltip title="刷新连接列表">
                <Button
                  aria-label="刷新连接列表"
                  icon={<ReloadOutlined spin={query.isFetching} />}
                  disabled={query.isFetching || query.fetchStatus === 'paused'}
                  onClick={() => void query.refetch()}
                />
              </Tooltip>
              {canCreate && (
                <Button
                  type="primary"
                  icon={<PlusOutlined />}
                  onClick={() => setDrawer({ target: null, vendor: 'CUSTOM' })}
                >
                  添加连接
                </Button>
              )}
            </div>
          )
        }
      />
      {forbidden ? (
        <Result status="403" title="没有查看模型连接的权限" />
      ) : (
        <>
          <section className={styles.configured} aria-labelledby={`${id}-configured`}>
            <div className={styles.sectionHead}>
              <div>
                <h2 id={`${id}-configured`}>
                  {search ? '搜索结果' : '已配置连接'}
                  {query.data && <span className={styles.count}>{query.data.total}</span>}
                </h2>
                <p>启用连接中的启用模型会出现在聊天选择器。</p>
              </div>
              <Input.Search
                className={styles.search}
                placeholder="搜索连接名称"
                aria-label="搜索连接名称"
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
                title={query.data ? '刷新失败，当前显示上次加载的连接' : '模型连接加载失败'}
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
                  description={search ? '没有找到匹配的连接' : '还没有模型连接'}
                />
                {search ? (
                  <Button onClick={clearSearch}>清除搜索</Button>
                ) : (
                  <p>
                    {canCreate
                      ? '从下方选择供应商模板，或点击右上角添加连接。'
                      : '连接配置添加后会显示在这里。'}
                  </p>
                )}
              </div>
            ) : (
              <div>
                {items.map((item) => (
                  <article key={item.id} className={styles.connection} aria-label={item.name}>
                    <div className={styles.connectionHead}>
                      <ProviderIcon vendor={item.vendor} name={item.name} />
                      <div className={styles.identity}>
                        <div className={styles.nameLine}>
                          <h3>{item.name}</h3>
                          <DictTag dictionary={statusDictionary} value={item.status} />
                        </div>
                        <p className={styles.description} title={item.remark}>
                          {templateFor(item.vendor).name}
                          {item.remark && (
                            <>
                              <span className={styles.separator}>·</span>
                              {item.remark}
                            </>
                          )}
                        </p>
                      </div>
                      <div className={styles.connectionActions}>
                        {item.website && (
                          <Tooltip title="打开供应商官网">
                            <Button
                              type="text"
                              size="small"
                              icon={<LinkOutlined />}
                              aria-label={`打开${item.name}官网`}
                              href={item.website}
                              target="_blank"
                              rel="noopener noreferrer"
                            >
                              官网
                            </Button>
                          </Tooltip>
                        )}
                        {canUpdate && (
                          <Button
                            size="small"
                            icon={<EditOutlined />}
                            aria-label={`编辑${item.name}`}
                            onClick={() => setDrawer({ target: item, vendor: item.vendor })}
                          >
                            编辑
                          </Button>
                        )}
                        {canDelete && (
                          <Tooltip title="删除连接">
                            <Button
                              type="text"
                              size="small"
                              danger
                              icon={<DeleteOutlined />}
                              aria-label={`删除${item.name}`}
                              disabled={remove.isPending}
                              onClick={() => confirmDelete(item)}
                            />
                          </Tooltip>
                        )}
                      </div>
                    </div>
                    <div className={styles.connectionBody}>
                      <div className={styles.endpoint}>
                        <span className={styles.fieldLabel}>请求地址</span>
                        <Tooltip title={item.baseUrl}>
                          <code>{item.baseUrl}</code>
                        </Tooltip>
                        <span className={styles.key}>
                          <KeyOutlined aria-hidden="true" />
                          密钥已保存
                        </span>
                      </div>
                      <div className={styles.modelsField}>
                        <span className={styles.fieldLabel}>
                          模型<span className={styles.modelCount}>{item.models.length}</span>
                        </span>
                        {item.models.length > 0 ? (
                          <div className={styles.models} aria-label={`${item.name} 的模型`}>
                            {item.models.map((model) => (
                              <Tooltip
                                key={model.id}
                                title={`${model.modelId} · ${dictLabel(statusDictionary, model.status)}`}
                              >
                                <Tag
                                  bordered={false}
                                  className={`${styles.modelTag} ${model.status === 'DISABLED' ? styles.modelDisabled : ''}`}
                                >
                                  {model.displayName || model.modelId}
                                  {model.status === 'DISABLED' && (
                                    <span className={styles.modelStatus}>
                                      {dictLabel(statusDictionary, model.status)}
                                    </span>
                                  )}
                                </Tag>
                              </Tooltip>
                            ))}
                          </div>
                        ) : (
                          <p className={styles.modelsEmpty}>
                            尚未添加模型{canUpdate ? '，可通过编辑配置添加。' : '。'}
                          </p>
                        )}
                      </div>
                    </div>
                  </article>
                ))}
              </div>
            )}
            {query.data && query.data.total > 12 && (
              <div className={styles.pagination}>
                <span>共 {query.data.total} 个连接</span>
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
          {canCreate && (
            <section className={styles.templatesPanel} aria-labelledby={`${id}-templates`}>
              <div className={styles.sectionHead}>
                <div>
                  <h2 id={`${id}-templates`}>快速添加</h2>
                  <p>选择供应商预填地址；同一供应商可配置多个连接。</p>
                </div>
                <span className={styles.compatible}>OpenAI 兼容接口</span>
              </div>
              <div className={styles.templates}>
                {providerTemplates
                  .filter((template) => template.vendor !== 'CUSTOM')
                  .map((template) => (
                    <button
                      type="button"
                      className={styles.template}
                      key={template.vendor}
                      aria-label={`添加${template.name}连接`}
                      onClick={() => setDrawer({ target: null, vendor: template.vendor })}
                    >
                      <ProviderIcon vendor={template.vendor} name={template.name} />
                      <span className={styles.templateText}>
                        <strong>{template.name}</strong>
                        <small>{new URL(template.website).hostname}</small>
                      </span>
                      <PlusOutlined className={styles.add} aria-hidden="true" />
                    </button>
                  ))}
              </div>
            </section>
          )}
          <p className={styles.note}>
            <KeyOutlined aria-hidden="true" />
            密钥不回显；启用是配置状态，不代表已验证连通。
          </p>
        </>
      )}
      {drawer && (
        <ProviderFormDrawer
          key={drawer.target?.id ?? drawer.vendor}
          {...drawer}
          onClose={() => setDrawer(null)}
        />
      )}
    </div>
  );
}
