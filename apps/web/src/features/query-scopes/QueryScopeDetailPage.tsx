import type { DataSourceCheckResult, SaveQueryScopeMetadata } from '@merine/api-contract';
import { ReloadOutlined, SettingOutlined, TableOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, App, Button, Empty, Input, Result, Skeleton, Tag, Tooltip } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { Link, useBlocker, useParams } from 'react-router';
import { errorText, isForbiddenError } from '../../shared/api-error';
import { PERMISSIONS, hasPermission } from '../../shared/permissions';
import { DetailBackLink } from '../../shared/ui/DetailBackLink';
import { PageHeader } from '../../shared/ui/PageHeader';
import { useAuth } from '../auth/public';
import { dataSourceKeys } from '../data-sources/public';
import { DICTIONARY_CODES, DictTag, useDictionary } from '../dictionaries/public';
import {
  fetchQueryScopeDetail,
  inspectQueryScope,
  queryScopeKeys,
  replaceQueryScopeViews,
  saveQueryScopeMetadata,
} from './api';
import {
  type MetadataDraft,
  columnRows,
  formatCheckedAt,
  metadataChanged,
  metadataForm,
  outcomeMeta,
  scopeCandidates,
  viewMetaOf,
} from './model';
import { ScopeMetadataEditor } from './components/ScopeMetadataEditor';
import { ScopeRangeModal } from './components/ScopeRangeModal';
import styles from './QueryScopeDetailPage.module.css';

const LIST_PATH = '/agent/query-scopes';

export function QueryScopeDetailPage() {
  const { sourceId = '' } = useParams<{ sourceId: string }>();
  return <QueryScopeWorkspace key={sourceId} sourceId={sourceId} />;
}

/** 单个数据源的范围与说明工作区；各类草稿及字段探测按对象隔离。 */
function QueryScopeWorkspace({ sourceId }: { sourceId: string }) {
  const { state } = useAuth();
  const { message, modal } = App.useApp();
  const client = useQueryClient();
  const codes = state.status === 'authenticated' ? state.user.permissionCodes : [];
  const canRead = hasPermission(codes, PERMISSIONS.scopeRead);
  const canUpdate = hasPermission(codes, PERMISSIONS.scopeUpdate);
  const canTest = hasPermission(codes, PERMISSIONS.datasourceTest);
  const canReadSource = hasPermission(codes, PERMISSIONS.datasourceRead);
  const dictionary = useDictionary(DICTIONARY_CODES.status).data;
  const detailKey = [...queryScopeKeys, 'detail', sourceId];
  const detail = useQuery({
    queryKey: detailKey,
    queryFn: ({ signal }) => fetchQueryScopeDetail(sourceId, signal),
    enabled: !!sourceId && canRead,
  });
  const source = detail.data?.source;
  const [catalogInspection, setCatalogInspection] = useState<DataSourceCheckResult | null>(null);
  const [viewInspections, setViewInspections] = useState<Record<string, DataSourceCheckResult>>({});
  const [selectedView, setSelectedView] = useState<string | null>(null);
  const [metadataDraft, setMetadataDraft] = useState<MetadataDraft | null>(null);
  const [draftVersion, setDraftVersion] = useState<{
    version: number;
    semanticVersion: number;
  } | null>(null);
  const [rangeDraft, setRangeDraft] = useState<string[] | null>(null);
  const [rangeVersion, setRangeVersion] = useState<number | null>(null);
  const [rangeOpen, setRangeOpen] = useState(false);
  const [search, setSearch] = useState('');
  const savedViews = source?.views ?? [];
  const metaByView = new Map(
    (detail.data?.meta ?? []).map((item) => [item.view ?? '', item] as const),
  );
  const editableViews = [...new Set([...savedViews, ...metaByView.keys()])]
    .filter(Boolean)
    .sort((left, right) => left.localeCompare(right, 'en'));
  const activeView = selectedView ?? savedViews[0] ?? editableViews[0] ?? null;
  const meta = activeView ? viewMetaOf(detail.data, activeView) : undefined;
  const form = metadataDraft ?? metadataForm(meta);
  const rows = columnRows(
    activeView ? (viewInspections[activeView] ?? null) : null,
    detail.data,
    activeView ?? '',
  );
  const chosen = rangeDraft ?? savedViews;
  const candidates = scopeCandidates(detail.data, catalogInspection);
  const rangeDirty =
    chosen.length !== savedViews.length || chosen.some((view) => !savedViews.includes(view));
  const metaDirty = !!activeView && metadataChanged(form, meta);
  const filteredViews = editableViews.filter((view) =>
    `${view} ${metaByView.get(view)?.displayName ?? ''}`
      .toLowerCase()
      .includes(search.trim().toLowerCase()),
  );
  const inspect = useMutation({
    mutationFn: (view?: string) => inspectQueryScope(sourceId, view),
    onSuccess: (result, view) => {
      // 迟到响应：数据源配置版本已变化（如刚刚保存范围），旧字段与旧注释不再属于当前连接。
      if (
        result.sourceVersion !== undefined &&
        result.sourceVersion !== (source?.version ?? result.sourceVersion)
      ) {
        return;
      }
      setCatalogInspection(result);
      if (view) setViewInspections((previous) => ({ ...previous, [view]: result }));
      if (result.outcome !== 'PASSED') message.warning(result.message ?? '探测未通过');
    },
    onError: (error) => message.error(errorText(error)),
  });

  // 字段探测结果绑定数据源配置版本：连接配置或范围变化后，旧字段、类型与注释全部作废。
  const inspectedVersion = useRef<number | null>(null);
  useEffect(() => {
    const next = source?.version ?? null;
    if (inspectedVersion.current === next) return;
    inspectedVersion.current = next;
    setViewInspections({});
    setCatalogInspection(null);
  }, [source?.version]);
  const saveViews = useMutation({
    mutationFn: (views: string[]) =>
      replaceQueryScopeViews(sourceId, { version: rangeVersion ?? source?.version ?? 0, views }),
    onSuccess: (updated) => {
      client.setQueryData(detailKey, { ...detail.data, source: updated });
      setRangeDraft(null);
      setRangeOpen(false);
      setCatalogInspection(null);
      void client.invalidateQueries({ queryKey: queryScopeKeys });
      void client.invalidateQueries({ queryKey: dataSourceKeys });
      message.success('范围已保存，数据源已停用，请重新检查并启用');
    },
    onError: (error) => message.error(errorText(error)),
  });
  const saveMeta = useMutation({
    mutationFn: (input: SaveQueryScopeMetadata) => saveQueryScopeMetadata(sourceId, input),
    onSuccess: (data) => {
      client.setQueryData(detailKey, data);
      setMetadataDraft(null);
      void client.invalidateQueries({ queryKey: [...queryScopeKeys, 'list'] });
      void client.invalidateQueries({ queryKey: dataSourceKeys });
      message.success('说明已保存');
    },
    onError: (error) => message.error(errorText(error)),
  });
  const saving = saveViews.isPending || saveMeta.isPending;
  const dirty = metaDirty || rangeDirty;
  const blocker = useBlocker(dirty || saving);
  useEffect(() => {
    if (blocker.state !== 'blocked') return;
    if (saving) {
      message.info('正在保存，请稍候');
      blocker.reset();
      return;
    }
    modal.confirm({
      title: '放弃未保存的修改？',
      okText: '放弃修改',
      cancelText: '继续编辑',
      onOk: () => blocker.proceed(),
      onCancel: () => blocker.reset(),
    });
    // 只在进入阻塞状态时确认，不随输入变化重开弹窗。
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [blocker.state]);
  useEffect(() => {
    if (!dirty && !saving) return;
    const protect = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = '';
    };
    window.addEventListener('beforeunload', protect);
    return () => window.removeEventListener('beforeunload', protect);
  }, [dirty, saving]);

  function selectView(view: string) {
    if (saving) return;
    const go = () => {
      setMetadataDraft(null);
      setSelectedView(view);
      if (canTest && !viewInspections[view] && !inspect.isPending) inspect.mutate(view);
    };
    if (view === activeView) {
      if (canTest && !viewInspections[view] && !inspect.isPending) inspect.mutate(view);
      return;
    }
    if (metaDirty)
      modal.confirm({
        title: '放弃当前视图的修改？',
        okText: '放弃并切换',
        cancelText: '继续编辑',
        onOk: go,
      });
    else go();
  }
  function closeRange() {
    if (saveViews.isPending) return;
    const close = () => {
      setRangeDraft(null);
      setRangeOpen(false);
    };
    if (rangeDirty)
      modal.confirm({
        title: '放弃范围修改？',
        okText: '放弃修改',
        cancelText: '继续选择',
        onOk: close,
      });
    else close();
  }
  function openRange() {
    const open = () => {
      setMetadataDraft(null);
      setRangeDraft(null);
      setRangeVersion(source?.version ?? 0);
      setRangeOpen(true);
    };
    if (metaDirty)
      modal.confirm({
        title: '放弃当前说明并调整范围？',
        okText: '放弃并继续',
        cancelText: '继续编辑',
        onOk: open,
      });
    else open();
  }
  function changeMetadata(value: MetadataDraft) {
    // 第一次输入时固定版本；后台刷新不能替用户接受别人的配置变化。
    if (metadataDraft === null)
      setDraftVersion({
        version: source?.version ?? 0,
        semanticVersion: source?.semanticVersion ?? 0,
      });
    setMetadataDraft(value);
  }
  function submitMetadata() {
    if (!activeView || !canUpdate || saving) return;
    saveMeta.mutate({
      version: draftVersion?.version ?? source?.version ?? 0,
      semanticVersion: draftVersion?.semanticVersion ?? source?.semanticVersion ?? 0,
      view: activeView,
      displayName: form.displayName,
      description: form.description,
      columns: Object.entries(form.overrides)
        .filter(([, value]) => value.trim() !== '')
        .map(([name, value]) => ({ name, description: value.trim() })),
    });
  }
  const forbidden = !canRead || isForbiddenError(detail.error);
  const outcome = catalogInspection ? outcomeMeta(catalogInspection.outcome) : undefined;

  return (
    <div>
      <PageHeader
        demo={false}
        title={forbidden ? '查询范围' : (source?.name ?? '查询范围')}
        actions={
          !forbidden && (
            <Button
              icon={<ReloadOutlined />}
              loading={detail.isFetching}
              disabled={saving || dirty}
              title={dirty ? '请先保存或放弃修改' : undefined}
              onClick={() => void detail.refetch()}
            >
              刷新配置
            </Button>
          )
        }
      />
      <div className={styles.page}>
        <div className={styles.context}>
          <DetailBackLink to={LIST_PATH}>返回查询范围</DetailBackLink>
          {!forbidden && source && (
            <div className={styles.contextMeta}>
              <DictTag dictionary={dictionary} value={source.status ?? ''} />
              <Tooltip
                title={`配置 v${source.version ?? 0} · 语义 v${source.semanticVersion ?? 0}`}
              >
                <span>说明 v{source.semanticVersion ?? 0}</span>
              </Tooltip>
              {outcome && (
                <Tooltip
                  title={`${formatCheckedAt(catalogInspection?.checkedAt)} · ${catalogInspection?.message ?? ''}`}
                >
                  <Tag bordered={false} color={outcome.color}>
                    {outcome.label}
                  </Tag>
                </Tooltip>
              )}
            </div>
          )}
        </div>
        {!sourceId ? (
          <Result status="404" title="缺少数据源标识" />
        ) : forbidden ? (
          <Result status="403" title="没有查看查询范围的权限" />
        ) : detail.isLoading ? (
          <div className={styles.panel}>
            <Skeleton active paragraph={{ rows: 8 }} />
          </div>
        ) : detail.isError && !detail.data ? (
          <Alert
            type="error"
            showIcon
            title="查询范围加载失败"
            description={errorText(detail.error)}
            action={<Button onClick={() => void detail.refetch()}>重试</Button>}
          />
        ) : (
          <>
            {detail.isError && (
              <Alert
                type="warning"
                showIcon
                title="刷新失败，当前显示上次配置"
                description={errorText(detail.error)}
              />
            )}
            {source?.status === 'DISABLED' && (
              <div className={styles.statusNotice}>
                <span>数据源已停用，重新检查并启用后生效。</span>
                {canReadSource && <Link to="/agent/datasources">去数据源管理 →</Link>}
              </div>
            )}
            {catalogInspection && catalogInspection.outcome !== 'PASSED' && (
              <Alert
                type="warning"
                showIcon
                title={outcome?.label ?? '探测未通过'}
                description={catalogInspection.message}
              />
            )}
            <div className={styles.workspace}>
              <aside className={styles.sidebar} aria-label="视图目录">
                <div className={styles.sidebarHeader}>
                  <div className={styles.sectionTitle}>
                    <h2>查询视图</h2>
                    <span>{savedViews.length}</span>
                  </div>
                  {canUpdate && (
                    <Button
                      size="small"
                      type="text"
                      icon={<SettingOutlined />}
                      disabled={saving}
                      onClick={openRange}
                    >
                      调整范围
                    </Button>
                  )}
                </div>
                <div className={styles.viewSearch}>
                  <Input.Search
                    aria-label="搜索视图"
                    placeholder="搜索视图"
                    allowClear
                    value={search}
                    onChange={(event) => setSearch(event.target.value)}
                  />
                </div>
                <ul className={styles.viewList}>
                  {filteredViews.map((view) => (
                    <li key={view}>
                      <button
                        type="button"
                        aria-pressed={view === activeView}
                        disabled={saving}
                        onClick={() => selectView(view)}
                      >
                        <TableOutlined aria-hidden="true" />
                        <span>
                          <b>{metaByView.get(view)?.displayName?.trim() || view}</b>
                          <code>{view}</code>
                        </span>
                        {!savedViews.includes(view) && <small>范围外</small>}
                      </button>
                    </li>
                  ))}
                </ul>
                {filteredViews.length === 0 && (
                  <div className={styles.empty}>
                    <Empty
                      image={Empty.PRESENTED_IMAGE_SIMPLE}
                      description={search ? '没有匹配视图' : '尚未设置查询范围'}
                    />
                  </div>
                )}
              </aside>
              {activeView ? (
                <ScopeMetadataEditor
                  key={activeView}
                  view={activeView}
                  meta={meta}
                  form={form}
                  rows={rows}
                  inspected={
                    !!viewInspections[activeView] &&
                    viewInspections[activeView].outcome === 'PASSED'
                  }
                  canUpdate={canUpdate}
                  canTest={canTest}
                  busy={saving || inspect.isPending}
                  reading={inspect.isPending && inspect.variables === activeView}
                  saving={saveMeta.isPending}
                  dirty={metaDirty}
                  onChange={changeMetadata}
                  onRead={() => inspect.mutate(activeView)}
                  onSave={submitMetadata}
                  onReset={() => setMetadataDraft(null)}
                />
              ) : (
                <section className={styles.editor}>
                  <div className={styles.empty}>
                    <Empty description="选择查询视图后，在这里维护说明">
                      {canUpdate && <Button onClick={openRange}>调整查询范围</Button>}
                    </Empty>
                  </div>
                </section>
              )}
            </div>
            {rangeOpen && canUpdate && (
              <ScopeRangeModal
                candidates={candidates}
                metadata={metaByView}
                chosen={chosen}
                saving={saveViews.isPending}
                reading={inspect.isPending}
                canTest={canTest}
                dirty={rangeDirty}
                onChange={setRangeDraft}
                onInspect={() => inspect.mutate(undefined)}
                onSave={() => saveViews.mutate(chosen)}
                onClose={closeRange}
              />
            )}
          </>
        )}
      </div>
    </div>
  );
}
