import { ReloadOutlined } from '@ant-design/icons';
import type { UserSummary } from '@merine/api-contract';
import { Alert, Button, Empty, Skeleton, Switch, Table, Tag } from 'antd';
import type { TableColumnsType } from 'antd';
import { TablePager } from '../../../shared/ui/TablePager';
import { DICTIONARY_CODES, dictLabel, useDictionary } from '../../dictionaries/public';
import { isUserEnabled } from '../model';
import styles from './UserTable.module.css';

/** 最近登录时间：本地时区 YYYY-MM-DD HH:mm；从未登录是真实状态，不显示成空。 */
function formatLastLogin(value: UserSummary['lastLoginAt']): string {
  if (!value) return '从未登录';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '时间未知';
  const pad = (part: number) => String(part).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
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
  statusChangingIds,
  canEdit,
  canToggle,
  canResetPassword,
  onPageChange,
  onSelectedIdsChange,
  onEdit,
  onResetPassword,
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
  /** 正在提交启用/停用的用户 id；目标行显示 loading，其余行暂时禁用 */
  statusChangingIds: string[];
  /** 按钮级权限：没有权限的动作按钮不渲染（DOM 里也不存在）；带状态展示的开关保留可见但禁用 */
  canEdit: boolean;
  canToggle: boolean;
  canResetPassword: boolean;
  onPageChange: (page: number) => void;
  onSelectedIdsChange: (ids: string[]) => void;
  onEdit: (user: UserSummary, trigger: HTMLElement) => void;
  onResetPassword: (user: UserSummary, trigger: HTMLElement) => void;
  onStatusAction: (user: UserSummary) => void;
  onBulkDisable: () => void;
  onRefresh: () => void;
  onRetry: () => void;
  onClearFilters: () => void;
}) {
  // 开关上的文案来自字典：改字典标签即可统一调整所有页面的措辞
  const statusDictionary = useDictionary(DICTIONARY_CODES.status).data;
  const columns: TableColumnsType<UserSummary> = [
    {
      title: '账号',
      dataIndex: 'loginName',
      width: 172,
      render: (value: string) => <span className={styles.mono}>{value}</span>,
    },
    { title: '姓名', dataIndex: 'displayName', width: 132 },
    { title: '所属单位', dataIndex: 'unitName', width: 240 },
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
      title: '启用状态',
      dataIndex: 'status',
      width: 130,
      render: (_value, user) => {
        const enabled = isUserEnabled(user.status);
        const changing = statusChangingIds.includes(user.id);
        return (
          <Switch
            size="small"
            checked={enabled}
            loading={changing}
            disabled={!canToggle || statusChangingIds.length > 0}
            checkedChildren={dictLabel(statusDictionary, 'ENABLED')}
            unCheckedChildren={dictLabel(statusDictionary, 'DISABLED')}
            title={canToggle ? undefined : '需要「用户管理 · 启停」权限'}
            aria-label={`${user.displayName}：${enabled ? '点击禁用账号' : '点击启用账号'}`}
            onChange={() => onStatusAction(user)}
          />
        );
      },
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
    ...(canEdit || canResetPassword
      ? [
          {
            title: '操作',
            key: 'actions',
            align: 'right' as const,
            width: 184,
            render: (_value: unknown, user: UserSummary) => (
              <span className={styles.actions}>
                {canEdit && (
                  <Button
                    type="text"
                    size="small"
                    onClick={(event) => onEdit(user, event.currentTarget)}
                  >
                    编辑
                  </Button>
                )}
                {canResetPassword && (
                  <Button
                    type="text"
                    size="small"
                    onClick={(event) => onResetPassword(user, event.currentTarget)}
                  >
                    重置密码
                  </Button>
                )}
              </span>
            ),
          },
        ]
      : []),
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
        {canToggle && <span className={styles.selected}>{selectedIds.length} 项已选</span>}
        {canToggle && (
          <Button size="small" disabled={selectedIds.length === 0} onClick={onBulkDisable}>
            批量禁用
          </Button>
        )}
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
              // 列宽合计超过内容区时让横向滚动留在表格容器内，不挤掉操作列
              scroll={{ x: 1220 }}
              loading={isRefreshing && !refreshError}
              dataSource={users}
              columns={columns}
              pagination={false}
              locale={{ emptyText }}
              // 选择框只服务于「批量禁用」：没有启停权限时不渲染，免得留一排没有出口的勾选框
              rowSelection={
                canToggle
                  ? {
                      columnWidth: 40,
                      selectedRowKeys: selectedIds,
                      onChange: (keys) => onSelectedIdsChange(keys.map((key) => String(key))),
                      // antd 默认给选择框的 aria-label 是英文（Select all / Select row N），中文界面下补上中文
                      getTitleCheckboxProps: () => ({ 'aria-label': '全选本页' }),
                      getCheckboxProps: (user) => ({ 'aria-label': `选择 ${user.loginName}` }),
                    }
                  : undefined
              }
            />
          </>
        )}
      </div>

      <TablePager
        total={total}
        page={page}
        pageSize={pageSize}
        itemCount={users.length}
        onPageChange={onPageChange}
      />
    </div>
  );
}
