import { ReloadOutlined } from '@ant-design/icons';
import type { UserSummary } from '@merine/api-contract';
import { Alert, Button, Empty, Skeleton, Table, Tag } from 'antd';
import type { TableColumnsType } from 'antd';
import { isUserEnabled } from '../model';
import styles from './UserTable.module.css';

/** 状态标签：启用为成功色 + 实心圆点，已禁用为中性色 + 空心圆点，两者都带文字。 */
function StatusTag({ status }: { status: UserSummary['status'] }) {
  const enabled = isUserEnabled(status);
  return (
    <Tag className={enabled ? styles.tagSuccess : styles.tagNeutral}>
      <span className={enabled ? styles.dot : styles.dotOutline} />
      {enabled ? '启用' : '已禁用'}
    </Tag>
  );
}

/** 最近登录时间：本地时区 YYYY-MM-DD HH:mm；从未登录是真实状态，不显示成空。 */
function formatLastLogin(value: UserSummary['lastLoginAt']): string {
  if (!value) return '从未登录';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '时间未知';
  const pad = (part: number) => String(part).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/** 页码按钮：首尾与当前页前后一页，其余折叠，避免页数多时铺满一行。 */
function pageItems(current: number, pageCount: number): (number | 'gap')[] {
  if (pageCount <= 7) return Array.from({ length: pageCount }, (_value, index) => index + 1);
  const wanted = [1, current - 1, current, current + 1, pageCount]
    .filter((page) => page >= 1 && page <= pageCount)
    .sort((left, right) => left - right);
  const items: (number | 'gap')[] = [];
  let previous = 0;
  for (const page of wanted) {
    if (previous && page - previous > 1) items.push('gap');
    items.push(page);
    previous = page;
  }
  return items;
}

/**
 * 用户列表卡：工具栏、表格与分页。
 * 加载、无匹配、失败与「有旧结果但刷新失败」都单独表达，失败不显示成暂无数据。
 * 排序由后端固定（按用户 id），本轮不提供列排序，因此不存在「改排序回第一页」。
 */
export function UserTable({
  users,
  total,
  page,
  pageSize,
  selectedIds,
  hasFilters,
  isInitialLoading,
  isRefreshing,
  refreshError,
  onPageChange,
  onSelectedIdsChange,
  onEdit,
  onStatusAction,
  onBulkDisable,
  onRefresh,
  onRetry,
  onClearFilters,
}: {
  users: UserSummary[];
  total: number;
  page: number;
  pageSize: number;
  selectedIds: string[];
  hasFilters: boolean;
  isInitialLoading: boolean;
  isRefreshing: boolean;
  /** 有旧结果时的刷新失败说明；没有旧结果时由页面整体表达失败 */
  refreshError: string | null;
  onPageChange: (page: number) => void;
  onSelectedIdsChange: (ids: string[]) => void;
  onEdit: (user: UserSummary, trigger: HTMLElement) => void;
  onStatusAction: (user: UserSummary) => void;
  onBulkDisable: () => void;
  onRefresh: () => void;
  onRetry: () => void;
  onClearFilters: () => void;
}) {
  const pageCount = Math.max(1, Math.ceil(total / pageSize));
  const from = users.length === 0 ? 0 : (page - 1) * pageSize + 1;
  const to = users.length === 0 ? 0 : from + users.length - 1;

  const columns: TableColumnsType<UserSummary> = [
    {
      title: '账号',
      dataIndex: 'loginName',
      width: 172,
      render: (value: string) => <span className={styles.mono}>{value}</span>,
    },
    { title: '姓名', dataIndex: 'displayName', width: 132 },
    { title: '所属单位', dataIndex: 'unitName', width: 132 },
    {
      title: '角色',
      dataIndex: 'roleNames',
      width: 150,
      render: (value: string[]) =>
        value.length === 0 ? (
          <span className={styles.muted}>未分配角色</span>
        ) : (
          <span className={styles.roles}>
            {value.map((name) => (
              <Tag key={name} className={styles.tagNeutral}>
                {name}
              </Tag>
            ))}
          </span>
        ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 104,
      render: (value: UserSummary['status']) => <StatusTag status={value} />,
    },
    {
      title: '最近登录时间',
      dataIndex: 'lastLoginAt',
      width: 164,
      align: 'right',
      render: (value: UserSummary['lastLoginAt']) => {
        const text = formatLastLogin(value);
        return (
          <span className={value ? styles.mono : styles.muted} title={value ?? undefined}>
            {text}
          </span>
        );
      },
    },
    {
      title: '操作',
      key: 'actions',
      align: 'right',
      render: (_value, user) => (
        <span className={styles.actions}>
          <Button type="text" size="small" onClick={(event) => onEdit(user, event.currentTarget)}>
            编辑
          </Button>
          <Button type="text" size="small" onClick={() => onStatusAction(user)}>
            {isUserEnabled(user.status) ? '禁用' : '启用'}
          </Button>
        </span>
      ),
    },
  ];

  const emptyText = hasFilters ? (
    <Empty
      image={Empty.PRESENTED_IMAGE_SIMPLE}
      description={
        <span className={styles.emptyText}>
          没有符合条件的用户
          <span className={styles.emptyHint}>当前筛选条件下没有匹配结果，可放宽或清空条件。</span>
        </span>
      }
    >
      <Button onClick={onClearFilters}>清空筛选条件</Button>
    </Empty>
  ) : (
    <Empty
      image={Empty.PRESENTED_IMAGE_SIMPLE}
      description={
        <span className={styles.emptyText}>
          还没有任何用户账号
          <span className={styles.emptyHint}>可以用右上角「新建用户」创建第一个账号。</span>
        </span>
      }
    />
  );

  return (
    <div className={styles.card}>
      <div className={styles.toolbar}>
        <b className={styles.toolbarTitle}>用户列表</b>
        <Tag className={styles.countTag}>{isInitialLoading ? '—' : total}</Tag>
        <span className={styles.spacer} />
        <span className={styles.selected}>{selectedIds.length} 项已选</span>
        <Button size="small" disabled={selectedIds.length === 0} onClick={onBulkDisable}>
          批量禁用
        </Button>
        <Button size="small" icon={<ReloadOutlined />} loading={isRefreshing} onClick={onRefresh}>
          刷新
        </Button>
      </div>

      <div className={styles.tableWrap}>
        {isInitialLoading ? (
          <div className={styles.skeleton} role="status" aria-live="polite">
            <Skeleton active title={false} paragraph={{ rows: 5 }} />
            <span className={styles.skeletonText}>正在加载用户列表…</span>
          </div>
        ) : (
          <>
            {refreshError && (
              <Alert
                className={styles.banner}
                type="warning"
                showIcon
                title="刷新失败，以下为上一次的结果"
                description={refreshError}
                action={
                  <Button size="small" onClick={onRetry}>
                    重试
                  </Button>
                }
              />
            )}
            <Table<UserSummary>
              rowKey="id"
              size="small"
              tableLayout="fixed"
              loading={isRefreshing && !refreshError}
              dataSource={users}
              columns={columns}
              pagination={false}
              locale={{ emptyText }}
              rowSelection={{
                columnWidth: 40,
                selectedRowKeys: selectedIds,
                onChange: (keys) => onSelectedIdsChange(keys.map((key) => String(key))),
                // antd 默认给选择框的 aria-label 是英文（Select all / Select row N），中文界面下补上中文
                getTitleCheckboxProps: () => ({ 'aria-label': '全选本页' }),
                getCheckboxProps: (user) => ({ 'aria-label': `选择 ${user.loginName}` }),
              }}
            />
          </>
        )}
      </div>

      <div className={styles.pager}>
        <span>
          共 {total} 条 · 每页 {pageSize} 条 · {total === 0 ? '本页 0 条' : `本页 ${from}–${to}`}
        </span>
        <div className={styles.pagerPages}>
          <button
            type="button"
            className={styles.pagerBtn}
            aria-label="上一页"
            disabled={page <= 1}
            onClick={() => onPageChange(page - 1)}
          >
            ‹
          </button>
          {pageItems(page, pageCount).map((item, index) =>
            item === 'gap' ? (
              <span key={`gap-${index}`} className={styles.pagerGap}>
                …
              </span>
            ) : (
              <button
                key={item}
                type="button"
                className={styles.pagerBtn}
                aria-current={item === page ? 'page' : undefined}
                onClick={() => onPageChange(item)}
              >
                {item}
              </button>
            ),
          )}
          <button
            type="button"
            className={styles.pagerBtn}
            aria-label="下一页"
            disabled={page >= pageCount}
            onClick={() => onPageChange(page + 1)}
          >
            ›
          </button>
        </div>
      </div>
    </div>
  );
}
