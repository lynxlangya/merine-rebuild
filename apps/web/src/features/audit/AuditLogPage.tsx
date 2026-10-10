import {
  DeleteOutlined,
  DownOutlined,
  ReloadOutlined,
  RightOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import { useMutation, useQuery } from '@tanstack/react-query';
import { App, Button, Input, Popconfirm, Select, Table, Tag, Tooltip } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useMemo, useState } from 'react';
import { errorText } from '../../shared/api-error';
import { TablePager } from '../../shared/ui/TablePager';
import tableStyles from '../../shared/ui/RecordTable.module.css';
import {
  auditKeys,
  fetchAuditLogs,
  fetchAuditModules,
  purgeAuditLogs,
  type AuditLogEntry,
} from './api';
import { purgeResultText } from './purge';
import { rangeFromDays } from './range';
import styles from './AuditLogPage.module.css';

const PAGE_SIZE = 20;
/** 与后端 merine.audit.retention.days 一致，页面上把口说清（服务端才是判定方）。 */
const RETENTION_DAYS = 30;
const RANGES = [
  { value: '1', label: '近 24 小时' },
  { value: '7', label: '近 7 天' },
  { value: '30', label: '近 30 天' },
];
const RESULTS = [
  { value: '', label: '全部结果' },
  { value: 'SUCCEEDED', label: '成功' },
  { value: 'FAILED', label: '失败' },
];
const ACTION_LABELS: Record<string, string> = {
  'auth:login': '登录',
  'auth:logout': '退出登录',
  'user:create': '新建用户',
  'user:update': '编辑用户',
  'user:assign-roles': '调整用户角色',
  'user:reset-password': '重置密码',
  'user:toggle-status': '启停用户',
  'role:create': '新建角色',
  'role:update': '编辑角色',
  'role:grant-permissions': '调整角色权限',
  'role:toggle-status': '启停角色',
  'role:delete': '删除角色',
  'menu:create': '新建菜单',
  'menu:update': '编辑菜单',
  'menu:delete': '删除菜单',
  'menu:restore': '恢复默认菜单',
  'unit:create': '新建单位',
  'unit:update': '编辑单位',
  'unit:delete': '删除单位',
  'dict-type:create': '新建字典',
  'dict-type:update': '编辑字典',
  'dict-item:create': '新增字典项',
  'dict-item:update': '编辑字典项',
  'provider:create': '新建连接',
  'provider:update': '编辑连接',
  'provider:delete': '删除连接',
  'provider:discover-models': '获取模型列表',
  'port:create': '新增港口',
  'port:update': '编辑港口',
  'port:delete': '删除港口',
  'wharf:create': '新增码头',
  'wharf:update': '编辑码头',
  'wharf:delete': '删除码头',
  'anchorage:create': '新增锚地',
  'anchorage:update': '编辑锚地',
  'anchorage:delete': '删除锚地',
  'island:create': '新增海岛',
  'island:update': '编辑海岛',
  'island:delete': '删除海岛',
  'police-station:create': '新增派出所',
  'police-station:update': '编辑派出所',
  'police-station:delete': '删除派出所',
  'port-officer:create': '新增民警',
  'port-officer:update': '编辑民警',
  'port-officer:delete': '删除民警',
  'audit:purge': '清理过期流水',
};
const TARGET_LABELS: Record<string, string> = {
  USER: '用户',
  ROLE: '角色',
  MENU: '菜单',
  UNIT: '单位',
  DICT_TYPE: '字典',
  DICT_ITEM: '字典项',
  PROVIDER: '模型连接',
  PORT: '港口',
  WHARF: '码头',
  ANCHORAGE: '锚地',
  ISLAND: '海岛',
  POLICE_STATION: '派出所',
  PORT_OFFICER: '民警',
  AUDIT_LOG: '审计流水',
};

/** 只读审计流水：条件与分页由服务端执行，技术字段保留在展开详情中。 */
export function AuditLogPage() {
  const { message } = App.useApp();
  const [days, setDays] = useState('7');
  const [actorDraft, setActorDraft] = useState('');
  const [actor, setActor] = useState('');
  const [module, setModule] = useState('');
  const [result, setResult] = useState('');
  const [page, setPage] = useState(1);

  const modules = useQuery({
    queryKey: auditKeys.modules,
    queryFn: ({ signal }) => fetchAuditModules(signal),
    staleTime: 30 * 60 * 1000,
  });
  // 保持查询键稳定，输入、展开行等渲染不重新计算时间边界。
  const from = useMemo(() => rangeFromDays(Number(days)), [days]);
  const query = { from, actor, module, result, page, pageSize: PAGE_SIZE };
  const logs = useQuery({
    queryKey: auditKeys.list(query),
    queryFn: ({ signal }) => fetchAuditLogs(query, signal),
    placeholderData: (previous) => previous,
  });
  const filtered = days !== '7' || !!actorDraft || !!actor || !!module || !!result;

  function searchActor(input: string) {
    const value = input.trim();
    setActorDraft(value);
    setActor(value);
    setPage(1);
  }

  function resetFilters() {
    setDays('7');
    setActorDraft('');
    setActor('');
    setModule('');
    setResult('');
    setPage(1);
  }

  const columns: ColumnsType<AuditLogEntry> = [
    {
      title: '时间',
      dataIndex: 'occurredAt',
      width: 130,
      render: (value: string) => (
        <time dateTime={value} className={styles.twoLines}>
          <span>{new Date(value).toLocaleDateString('zh-CN')}</span>
          <span className={styles.secondary}>
            {new Date(value).toLocaleTimeString('zh-CN', { hourCycle: 'h23' })}
          </span>
        </time>
      ),
    },
    {
      title: '操作者',
      key: 'actor',
      width: 180,
      render: (_, entry) => (
        <span className={styles.twoLines}>
          <span className={styles.primary}>{entry.actorName || '未知身份'}</span>
          <span className={styles.secondary} title={entry.actorLogin}>
            {entry.actorLogin || '—'}
          </span>
        </span>
      ),
    },
    { title: '模块', dataIndex: 'moduleName', width: 120, ellipsis: true },
    {
      title: '动作',
      dataIndex: 'action',
      width: 160,
      ellipsis: true,
      render: (value: string) => (
        <Tooltip title={value}>
          <span>{ACTION_LABELS[value] || value || '—'}</span>
        </Tooltip>
      ),
    },
    {
      title: '对象',
      key: 'target',
      width: 180,
      render: (_, entry) => (
        <span className={styles.twoLines}>
          <span className={styles.truncate} title={entry.targetLabel || entry.targetId}>
            {entry.targetLabel || entry.targetId || '—'}
          </span>
          {entry.targetType && (
            <span className={styles.secondary}>
              {TARGET_LABELS[entry.targetType] || entry.targetType}
            </span>
          )}
        </span>
      ),
    },
    {
      title: '结果',
      dataIndex: 'result',
      width: 90,
      render: (value: string) => (
        <Tag color={value === 'SUCCEEDED' ? 'green' : value === 'FAILED' ? 'red' : undefined}>
          {RESULTS.find((item) => item.value === value)?.label || value || '—'}
        </Tag>
      ),
    },
    {
      title: '摘要',
      dataIndex: 'summary',
      render: (value: string) => (
        <Tooltip title={value}>
          <span className={styles.summary}>{value || '—'}</span>
        </Tooltip>
      ),
    },
  ];

  const purge = useMutation({
    mutationFn: purgeAuditLogs,
    onSuccess: (result) => {
      message.success(purgeResultText(result));
      // 清理后当前筛选/分页下的数据一定变了：显式刷新当前查询，不用部分键去猜缓存
      void logs.refetch();
    },
    onError: (error) => message.error(errorText(error)),
  });

  return (
    <div className={styles.page}>
      <header className={styles.head}>
        <div>
          <h1 className={styles.title}>审计日志</h1>
          <p className={styles.description}>
            查看操作者、操作对象与执行结果。记录只读，不包含业务正文与密钥； 只保留最近{' '}
            {RETENTION_DAYS} 天，过期记录由每日定时任务或下方按钮清理，清理本身也会留痕。
          </p>
        </div>
        <div className={styles.headActions}>
          <Popconfirm
            title={`永久删除 ${RETENTION_DAYS} 天前的记录？`}
            description="删除不可恢复，只影响超过保留期的流水；本次操作会留下一条清理记录。"
            okText="确认清理"
            cancelText="取消"
            okButtonProps={{ danger: true }}
            onConfirm={() => purge.mutate()}
          >
            <Button danger icon={<DeleteOutlined />} loading={purge.isPending}>
              清理过期记录
            </Button>
          </Popconfirm>
          <Button
            icon={<ReloadOutlined spin={logs.isFetching} />}
            disabled={logs.isFetching}
            onClick={() => void logs.refetch()}
          >
            刷新
          </Button>
        </div>
      </header>

      <section className={styles.filterPanel} aria-label="筛选审计记录">
        <div className={styles.filters}>
          <div className={styles.field}>
            <label htmlFor="audit-range">时间范围</label>
            <Select
              id="audit-range"
              value={days}
              options={RANGES}
              onChange={(value) => {
                setDays(value);
                setPage(1);
              }}
            />
          </div>
          <div className={styles.field}>
            <label htmlFor="audit-actor">操作者</label>
            <Input.Search
              id="audit-actor"
              allowClear
              placeholder="搜索姓名或登录名"
              prefix={<SearchOutlined />}
              enterButton="查询"
              value={actorDraft}
              onChange={(event) => {
                const value = event.target.value;
                setActorDraft(value);
                if (!value) {
                  setActor('');
                  setPage(1);
                }
              }}
              onSearch={searchActor}
            />
          </div>
          <div className={styles.field}>
            <label htmlFor="audit-module">所属模块</label>
            <Select
              id="audit-module"
              value={module}
              loading={modules.isLoading}
              options={[
                { value: '', label: '全部模块' },
                ...(modules.data ?? []).map((item) => ({ value: item.code, label: item.name })),
              ]}
              onChange={(value) => {
                setModule(value);
                setPage(1);
              }}
            />
          </div>
          <div className={styles.field}>
            <label htmlFor="audit-result">执行结果</label>
            <Select
              id="audit-result"
              value={result}
              options={RESULTS}
              onChange={(value) => {
                setResult(value);
                setPage(1);
              }}
            />
          </div>
          <Button className={styles.reset} disabled={!filtered} onClick={resetFilters}>
            重置
          </Button>
        </div>
        {modules.isError && (
          <p className={styles.error} role="alert">
            模块选项加载失败：{errorText(modules.error)}
            <Button size="small" type="link" onClick={() => void modules.refetch()}>
              重试
            </Button>
          </p>
        )}
      </section>

      <section className={tableStyles.frame} aria-label="操作记录" aria-busy={logs.isFetching}>
        <div className={tableStyles.toolbar}>
          <h2>操作记录</h2>
          <span className={styles.hint}>展开记录查看完整详情</span>
        </div>
        {logs.isError && (
          <p className={`${styles.error} ${tableStyles.error}`} role="alert">
            加载失败：{errorText(logs.error)}
          </p>
        )}
        <Table<AuditLogEntry>
          rowKey="id"
          size="small"
          tableLayout="fixed"
          columns={columns}
          dataSource={logs.data?.items ?? []}
          loading={logs.isFetching}
          scroll={{ x: 1200 }}
          locale={{ emptyText: logs.isError ? '记录加载失败，请重试' : '当前筛选条件下暂无记录' }}
          expandable={{
            columnWidth: 44,
            expandIcon: ({ expanded, onExpand, record }) => (
              <button
                type="button"
                className={styles.expandButton}
                aria-label={expanded ? '收起记录详情' : '展开记录详情'}
                aria-expanded={expanded}
                onClick={(event) => onExpand(record, event)}
              >
                {expanded ? <DownOutlined /> : <RightOutlined />}
              </button>
            ),
            expandedRowRender: (entry) => (
              <div className={styles.detailPanel}>
                <h3>记录详情</h3>
                <dl className={styles.detail}>
                  <div>
                    <dt>所属单位</dt>
                    <dd>{entry.actorUnitName || '—'}</dd>
                  </div>
                  <div>
                    <dt>对象类型 / 编号</dt>
                    <dd className={styles.code}>
                      {entry.targetType || '—'} / {entry.targetId || '—'}
                    </dd>
                  </div>
                  <div>
                    <dt>动作代码</dt>
                    <dd className={styles.code}>{entry.action || '—'}</dd>
                  </div>
                  <div>
                    <dt>客户端 IP</dt>
                    <dd className={styles.code}>{entry.clientIp || '—'}</dd>
                  </div>
                  <div>
                    <dt>请求编号</dt>
                    <dd className={styles.code}>{entry.requestId || '—'}</dd>
                  </div>
                  <div>
                    <dt>记录编号</dt>
                    <dd className={styles.code}>{entry.id}</dd>
                  </div>
                  <div className={styles.fullSummary}>
                    <dt>完整摘要</dt>
                    <dd>{entry.summary || '—'}</dd>
                  </div>
                </dl>
              </div>
            ),
          }}
          pagination={false}
        />
        {logs.data && (
          <TablePager
            total={logs.data.total}
            page={logs.data.page}
            pageSize={PAGE_SIZE}
            itemCount={logs.data.items.length}
            onPageChange={(value) => {
              if (!logs.isFetching) setPage(value);
            }}
          />
        )}
      </section>
    </div>
  );
}
