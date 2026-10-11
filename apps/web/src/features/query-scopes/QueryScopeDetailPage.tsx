import type {
  DataSourceCheckResult,
  SaveQueryScopeMetadata,
  ScopeViewMeta,
} from '@merine/api-contract';
import { CloudSyncOutlined, ReloadOutlined, SaveOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  App,
  Button,
  Checkbox,
  Input,
  Popconfirm,
  Result,
  Skeleton,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import { useEffect, useRef, useState } from 'react';
import { useBlocker, useParams } from 'react-router';
import { errorText, isForbiddenError } from '../../shared/api-error';
import { PERMISSIONS, hasPermission } from '../../shared/permissions';
import { DetailBackLink } from '../../shared/ui/DetailBackLink';
import { PageHeader } from '../../shared/ui/PageHeader';
import { useAuth } from '../auth/public';
import {
  fetchQueryScopeDetail,
  inspectQueryScope,
  queryScopeKeys,
  replaceQueryScopeViews,
  saveQueryScopeMetadata,
} from './api';
import {
  type ScopeColumnRow,
  columnRows,
  effectiveDescription,
  formatCheckedAt,
  outcomeMeta,
  scopeCandidates,
  viewMetaOf,
} from './model';
import styles from './QueryScopePage.module.css';

const LIST_PATH = '/agent/query-scopes';

/**
 * 查询范围详情：维护单个数据源的允许查询视图与视图/字段说明。
 *
 * 范围与说明分开保存：范围变化会清除检查结果并停用数据源（需回数据源管理复检启用），
 * 说明变化只递增语义版本。探测候选视图与字段、新增字段说明需要实时连接目标库，
 * 因此除查看/维护权限外还要求数据源测试连接权限。未保存的改动在离开页面前会提示。
 */
export function QueryScopeDetailPage() {
  const { sourceId = '' } = useParams<{ sourceId: string }>();
  const { state } = useAuth();
  const { message, modal } = App.useApp();
  const client = useQueryClient();
  const codes = state.status === 'authenticated' ? state.user.permissionCodes : [];
  const canUpdate = hasPermission(codes, PERMISSIONS.scopeUpdate);
  const canTest = hasPermission(codes, PERMISSIONS.datasourceTest);
  const detail = useQuery({
    queryKey: [...queryScopeKeys, 'detail', sourceId],
    queryFn: ({ signal }) => fetchQueryScopeDetail(sourceId, signal),
    enabled: sourceId !== '',
  });
  const source = detail.data?.source;
  const [inspected, setInspected] = useState<DataSourceCheckResult | null>(null);
  const [inspectedView, setInspectedView] = useState<string | null>(null);
  const [draft, setDraft] = useState<string[] | null>(null);
  const [activeView, setActiveView] = useState<string | null>(null);
  const [displayName, setDisplayName] = useState('');
  const [description, setDescription] = useState('');
  const [overrides, setOverrides] = useState<Record<string, string>>({});
  const dirtyRef = useRef(false);

  const candidates = scopeCandidates(detail.data, inspected);
  const chosen = draft ?? source?.views ?? [];
  const savedViews = source?.views ?? [];
  const metaViews = (detail.data?.meta ?? []).map((item) => item.view ?? '').filter(Boolean);
  const editableViews = [...new Set([...savedViews, ...metaViews])].sort((left, right) =>
    left.localeCompare(right, 'en'),
  );
  const rows = columnRows(
    inspectedView === activeView ? inspected : null,
    detail.data,
    activeView ?? '',
  );
  const meta: ScopeViewMeta | undefined = activeView
    ? viewMetaOf(detail.data, activeView)
    : undefined;

  useEffect(() => {
    if (!activeView) return;
    // 详情刷新（如保存范围后的重新拉取）不能覆盖用户正在编辑的草稿；
    // 只有明确切换视图或保存成功后（dirtyRef 为假）才从服务端同步表单。
    if (dirtyRef.current) return;
    setDisplayName(meta?.displayName ?? '');
    setDescription(meta?.description ?? '');
    setOverrides(
      Object.fromEntries(
        (meta?.columns ?? []).map((item) => [item.name ?? '', item.description ?? '']),
      ),
    );
  }, [activeView, meta]);

  const inspect = useMutation({
    mutationFn: (view?: string) => inspectQueryScope(sourceId, view),
    onSuccess: (result, view) => {
      setInspected(result);
      setInspectedView(view ?? null);
      if (result.outcome !== 'PASSED') message.warning(result.message ?? '探测未通过');
    },
    onError: (error) => message.error(errorText(error)),
  });
  const saveViews = useMutation({
    mutationFn: (views: string[]) =>
      replaceQueryScopeViews(sourceId, { version: source?.version ?? 0, views }),
    onSuccess: () => {
      setDraft(null);
      message.success('范围已保存；数据源已停用，请在数据源管理复检并启用后生效');
      void client.invalidateQueries({ queryKey: queryScopeKeys });
    },
    onError: (error) => message.error(errorText(error)),
  });
  const saveMeta = useMutation({
    mutationFn: (input: SaveQueryScopeMetadata) => saveQueryScopeMetadata(sourceId, input),
    onSuccess: (data) => {
      client.setQueryData([...queryScopeKeys, 'detail', sourceId], data);
      message.success('说明已保存；查询时模型会读取最新语义版本');
    },
    onError: (error) => message.error(errorText(error)),
  });

  const columns = [
    {
      title: '字段',
      dataIndex: 'name',
      key: 'name',
      width: 150,
      render: (value: string) => <code className={styles.mono}>{value}</code>,
    },
    {
      title: '类型',
      dataIndex: 'type',
      key: 'type',
      width: 110,
      render: (value: string | undefined, row: ScopeColumnRow) => (
        <span className={styles.muted}>
          {value ?? '—'}
          {row.nullable ? ' · 可空' : ''}
        </span>
      ),
    },
    {
      title: '数据库注释（默认说明）',
      dataIndex: 'comment',
      key: 'comment',
      render: (value: string | undefined) =>
        value ? (
          <Typography.Text type="secondary">{value}</Typography.Text>
        ) : (
          <span className={styles.muted}>未提供</span>
        ),
    },
    {
      title: '覆盖说明',
      key: 'override',
      width: 240,
      render: (_value: unknown, row: ScopeColumnRow) => (
        <Input
          maxLength={300}
          size="small"
          value={overrides[row.name] ?? ''}
          placeholder={row.comment ? '留空回退数据库注释' : '输入应用内说明'}
          disabled={!canUpdate}
          onChange={(event) =>
            setOverrides((state) => ({ ...state, [row.name]: event.target.value }))
          }
        />
      ),
    },
    {
      title: '有效说明',
      key: 'effective',
      render: (_value: unknown, row: ScopeColumnRow) => {
        const effective = effectiveDescription({ ...row, override: overrides[row.name] ?? '' });
        return (
          <span>
            <Tag bordered={false}>{effective.source}</Tag>
            {effective.text}
          </span>
        );
      },
    },
  ];
  const rangeDirty =
    chosen.length !== savedViews.length || chosen.some((view) => !savedViews.includes(view));
  const metaDirty =
    displayName !== (meta?.displayName ?? '') ||
    description !== (meta?.description ?? '') ||
    rows.some((row) => (overrides[row.name] ?? '') !== (row.override ?? ''));
  const dirty = rangeDirty || metaDirty;
  // 渲染期同步一次，供离开页面确认与副作用读取最新的脏状态。
  dirtyRef.current = dirty;

  // 未保存的改动阻止路由离开（返回链接、侧栏导航、浏览器前进后退都走同一入口）。
  const blocker = useBlocker(dirty);
  useEffect(() => {
    if (blocker.state !== 'blocked') return;
    modal.confirm({
      title: '放弃未保存的修改？',
      content: '范围勾选或视图说明尚未保存，离开后不会保留。',
      okText: '放弃修改',
      cancelText: '继续编辑',
      onOk: () => blocker.proceed(),
      onCancel: () => blocker.reset(),
    });
    // 弹窗只依赖阻塞状态，不跟随输入变化重复弹出。
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [blocker.state]);

  /** 切换视图前保护草稿：确认放弃后才同步新视图的表单。 */
  const selectView = (view: string) => {
    if (view === activeView) return;
    const go = () => {
      dirtyRef.current = false;
      setActiveView(view);
      if (view !== inspectedView) inspect.mutate(view);
    };
    if (dirty) {
      modal.confirm({
        title: '放弃未保存的说明？',
        content: '当前视图的说明修改尚未保存，切换后不会保留。',
        okText: '放弃修改',
        cancelText: '继续编辑',
        onOk: go,
      });
      return;
    }
    go();
  };

  if (!sourceId) {
    return (
      <div>
        <PageHeader demo={false} title="维护查询范围" />
        <div className={styles.page}>
          <DetailBackLink to={LIST_PATH}>返回查询范围列表</DetailBackLink>
          <div className={styles.stateCard}>
            <Result status="404" title="缺少数据源标识" subTitle="请从查询范围列表进入维护页。" />
          </div>
        </div>
      </div>
    );
  }

  const forbidden = isForbiddenError(detail.error);
  const title = source?.name ?? '维护查询范围';
  return (
    <div>
      <PageHeader
        demo={false}
        title={title}
        description="维护该数据源允许 Agent 查询的视图与视图/字段说明；范围变化会停用数据源，说明变化只递增语义版本。"
        actions={
          !forbidden && (
            <Button
              icon={<ReloadOutlined />}
              loading={detail.isFetching}
              onClick={() => void detail.refetch()}
            >
              刷新
            </Button>
          )
        }
      />
      <div className={styles.page}>
        <div className={styles.backRow}>
          <DetailBackLink to={LIST_PATH}>返回查询范围列表</DetailBackLink>
          {source && (
            <span className={styles.detailMeta}>
              <Tag color={source.status === 'ENABLED' ? 'success' : 'default'} bordered={false}>
                {source.status === 'ENABLED' ? '启用' : '停用'}
              </Tag>
              <Typography.Text type="secondary">
                配置 v{source.version ?? 0} · 语义 v{source.semanticVersion ?? 0}
              </Typography.Text>
            </span>
          )}
        </div>
        {forbidden ? (
          <div className={styles.stateCard}>
            <Result
              status="403"
              title="没有查看查询范围的权限"
              subTitle="查询范围需要具备查询范围查看权限的账号。这里不显示任何数据源配置，也不会因此退出登录。"
            />
          </div>
        ) : detail.isLoading ? (
          <div className={styles.panel}>
            <div className={styles.panelBody}>
              <Skeleton active paragraph={{ rows: 8 }} />
            </div>
          </div>
        ) : detail.isError && !detail.data ? (
          <div className={styles.detailError}>
            <Alert
              type="error"
              showIcon
              title="查询范围详情加载失败"
              description="未取得已保存的范围与说明，为避免覆盖旧配置，暂时不能编辑或保存。"
            />
            <Button type="primary" onClick={() => void detail.refetch()}>
              重新加载
            </Button>
          </div>
        ) : (
          <>
            {detail.isError && detail.data && (
              <Alert
                type="warning"
                showIcon
                title="刷新失败，以下为上一次加载的配置"
                description={errorText(detail.error)}
                action={
                  <Button size="small" onClick={() => void detail.refetch()}>
                    重试
                  </Button>
                }
              />
            )}
            {source && source.status !== 'ENABLED' && (
              <Alert
                type="warning"
                showIcon
                title="当前数据源处于停用状态"
                description="范围保存后会清除检查结果并停用；请在「数据源管理」复检通过并启用后，新查询才会生效。"
              />
            )}
            {inspected && (
              <Alert
                type={inspected.outcome === 'PASSED' ? 'success' : 'warning'}
                showIcon
                title={`${outcomeMeta(inspected.outcome)?.label ?? '探测结论'} · ${formatCheckedAt(inspected.checkedAt)}`}
                description={`${inspected.message ?? ''}；检测到 ${inspected.detectedViews?.length ?? 0} 个可读视图`}
              />
            )}

            <div className={styles.panel}>
              <div className={styles.toolbar}>
                <b className={styles.toolbarTitle}>允许查询的视图</b>
                <Tag className={styles.countTag}>{chosen.length}</Tag>
                <span className={styles.spacer} />
                <Button
                  size="small"
                  icon={<CloudSyncOutlined />}
                  loading={inspect.isPending && inspectedView === null}
                  disabled={!canTest}
                  title={canTest ? undefined : '需要数据源测试连接权限'}
                  onClick={() => inspect.mutate(undefined)}
                >
                  刷新可读视图
                </Button>
              </div>
              <div className={styles.panelBody}>
                <p className={styles.hint}>
                  勾选先作为草稿；保存后数据源停用，需回数据源管理复检并启用。未勾选不代表删除数据库视图，
                  也不会撤销数据库账号授权。
                </p>
                <div className={styles.candidates}>
                  {candidates.length === 0 && (
                    <span className={styles.muted}>
                      还没有候选视图：点击右上角「刷新可读视图」从目标库读取账号具备 SELECT 的视图。
                    </span>
                  )}
                  {candidates.map((candidate) => (
                    <Checkbox
                      key={candidate.name}
                      checked={chosen.includes(candidate.name)}
                      disabled={!canUpdate}
                      onChange={(event) =>
                        setDraft(
                          event.target.checked
                            ? [...chosen, candidate.name].sort((left, right) =>
                                left.localeCompare(right, 'en'),
                              )
                            : chosen.filter((view) => view !== candidate.name),
                        )
                      }
                    >
                      <code className={styles.mono}>{candidate.name}</code>
                      {candidate.saved && (
                        <Tag color="blue" bordered={false} className={styles.tag}>
                          已选
                        </Tag>
                      )}
                      {candidate.detected === false && (
                        <Tag color="warning" bordered={false} className={styles.tag}>
                          未检测到授权
                        </Tag>
                      )}
                      {!candidate.saved && metaViews.includes(candidate.name) && (
                        <Tag bordered={false} className={styles.tag}>
                          保留说明
                        </Tag>
                      )}
                    </Checkbox>
                  ))}
                </div>
                {canUpdate && (
                  <Popconfirm
                    title="保存范围？"
                    description="保存后该数据源会停用并清除检查结果，需要重新检查并启用。"
                    okText="保存范围"
                    cancelText="取消"
                    onConfirm={() => saveViews.mutate(chosen)}
                  >
                    <Button
                      type="primary"
                      icon={<SaveOutlined />}
                      disabled={!rangeDirty || !detail.data || saveViews.isPending}
                      loading={saveViews.isPending}
                    >
                      保存范围
                    </Button>
                  </Popconfirm>
                )}
              </div>
            </div>

            <div className={styles.panel}>
              <div className={styles.toolbar}>
                <b className={styles.toolbarTitle}>视图与字段说明</b>
                <span className={styles.spacer} />
                <Typography.Text type="secondary">
                  说明变化不影响启停与检查结果，只递增语义版本
                </Typography.Text>
              </div>
              <div className={styles.panelBody}>
                {editableViews.length === 0 ? (
                  <span className={styles.muted}>先保存至少一个允许查询的视图，再填写说明。</span>
                ) : (
                  <>
                    <Space wrap>
                      {editableViews.map((view) => (
                        <Button
                          key={view}
                          size="small"
                          type={view === activeView ? 'primary' : 'default'}
                          onClick={() => selectView(view)}
                        >
                          {view}
                          {metaViews.includes(view) ? ' · 有说明' : ''}
                        </Button>
                      ))}
                    </Space>
                    {activeView && (
                      <>
                        <div className={styles.metaForm}>
                          <label className={styles.field}>
                            <span className={styles.label}>显示名</span>
                            <Input
                              maxLength={80}
                              value={displayName}
                              placeholder={activeView}
                              disabled={!canUpdate}
                              onChange={(event) => setDisplayName(event.target.value)}
                            />
                          </label>
                          <label className={styles.field}>
                            <span className={styles.label}>用途 / 指标说明</span>
                            <Input.TextArea
                              rows={4}
                              maxLength={2000}
                              showCount
                              value={description}
                              placeholder="说明统计对象、条件、时间字段与去重口径；例如总体办结 = status = 'COMPLETED' 且 completed_at 非空。"
                              disabled={!canUpdate}
                              onChange={(event) => setDescription(event.target.value)}
                            />
                          </label>
                        </div>
                        {inspectedView !== activeView && (
                          <Alert
                            type="info"
                            showIcon
                            className={styles.alert}
                            title="尚未读取该视图字段"
                            description={
                              canTest
                                ? '点击上方视图按钮会读取字段名、类型与数据库列注释；当前只显示已保存的覆盖值。'
                                : '需要数据源测试连接权限才能读取字段名、类型与数据库列注释；当前只显示已保存的覆盖值。'
                            }
                          />
                        )}
                        <div className={styles.metaTableWrap}>
                          <Table<ScopeColumnRow>
                            rowKey="name"
                            size="small"
                            tableLayout="fixed"
                            pagination={false}
                            dataSource={rows}
                            columns={columns}
                            locale={{ emptyText: '没有字段信息：读取目标库或先保存覆盖值' }}
                          />
                        </div>
                        {canUpdate && (
                          <div className={styles.detailActions}>
                            <Button
                              type="primary"
                              icon={<SaveOutlined />}
                              disabled={!metaDirty || !detail.data || saveMeta.isPending}
                              loading={saveMeta.isPending}
                              onClick={() =>
                                saveMeta.mutate({
                                  version: source?.version ?? 0,
                                  semanticVersion: source?.semanticVersion ?? 0,
                                  view: activeView,
                                  displayName,
                                  description,
                                  columns: Object.entries(overrides)
                                    .filter(([, value]) => value.trim() !== '')
                                    .map(([name, value]) => ({ name, description: value.trim() })),
                                })
                              }
                            >
                              保存说明
                            </Button>
                          </div>
                        )}
                      </>
                    )}
                  </>
                )}
              </div>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
