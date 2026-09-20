import { ReloadOutlined } from '@ant-design/icons';
import type { RoleListItem } from '@merine/api-contract';
import { Alert, Button, Empty, Skeleton, Table, Tag } from 'antd';
import type { TableColumnsType } from 'antd';
import { TablePager } from '../../../shared/ui/TablePager';
import { deleteBlockedReason, isRoleEnabled } from '../model';
import styles from './RoleTable.module.css';

/** 最近修改时刻：本地时区 YYYY-MM-DD HH:mm。 */
function formatUpdatedAt(value: RoleListItem['updatedAt']): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '时间未知';
  const pad = (part: number) => String(part).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/**
 * 角色列表：工具栏、表格与分页。
 * 没有写权限时按钮保留可见但禁用，并写明原因——禁用状态本身是权限信息，
 * 静默隐藏会让「为什么别人能改」无从回答。
 */
export function RoleTable({
  roles,
  total,
  page,
  pageSize,
  hasFilters,
  isInitialLoading,
  isRefreshing,
  refreshError,
  canUpdate,
  canToggle,
  canDelete,
  statusChangingCodes,
  onPageChange,
  onEdit,
  onMembers,
  onStatusAction,
  onDelete,
  onRefresh,
  onRetry,
  onClearFilters,
}: {
  roles: RoleListItem[];
  total: number;
  page: number;
  pageSize: number;
  hasFilters: boolean;
  isInitialLoading: boolean;
  isRefreshing: boolean;
  /** 有旧结果时的刷新失败说明；没有旧结果时由页面整体表达失败 */
  refreshError: string | null;
  canUpdate: boolean;
  canToggle: boolean;
  canDelete: boolean;
  /** 正在提交启停的角色编码；目标行显示 loading，其余行暂时禁用 */
  statusChangingCodes: string[];
  onPageChange: (page: number) => void;
  onEdit: (role: RoleListItem, trigger: HTMLElement) => void;
  onMembers: (role: RoleListItem) => void;
  onStatusAction: (role: RoleListItem) => void;
  onDelete: (role: RoleListItem, trigger: HTMLElement) => void;
  onRefresh: () => void;
  onRetry: () => void;
  onClearFilters: () => void;
}) {
  const updateHint = canUpdate ? undefined : '需要「角色管理 · 编辑」权限';
  const toggleHint = canToggle ? undefined : '需要「角色管理 · 启停」权限';
  const deleteHint = canDelete ? undefined : '需要「角色管理 · 删除」权限';

  const columns: TableColumnsType<RoleListItem> = [
    {
      title: '角色编码',
      dataIndex: 'code',
      width: 190,
      render: (value: string) => <span className={styles.mono}>{value}</span>,
    },
    {
      title: '名称',
      dataIndex: 'name',
      width: 180,
      render: (value: string, role) => (
        <span className={styles.name}>
          {value}
          {role.builtin && <Tag className={styles.builtinTag}>内置</Tag>}
        </span>
      ),
    },
    {
      title: '说明',
      dataIndex: 'description',
      render: (value: string | null) =>
        value ? (
          <span className={styles.description}>{value}</span>
        ) : (
          <span className={styles.muted}>未填写</span>
        ),
    },
    {
      title: '功能权限',
      dataIndex: 'permissionCount',
      width: 110,
      align: 'right',
      render: (value: number, role) => (
        <span className={styles.mono} title={role.builtin ? '内置角色恒拥有全部权限' : undefined}>
          {value} 项
        </span>
      ),
    },
    {
      title: '成员',
      dataIndex: 'userCount',
      width: 90,
      align: 'right',
      render: (value: number) => <span className={styles.mono}>{value}</span>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 96,
      render: (value: RoleListItem['status']) =>
        isRoleEnabled(value) ? (
          <Tag className={styles.enabledTag}>启用</Tag>
        ) : (
          <Tag className={styles.disabledTag}>已停用</Tag>
        ),
    },
    {
      title: '最近修改',
      dataIndex: 'updatedAt',
      width: 156,
      align: 'right',
      render: (value: RoleListItem['updatedAt']) => (
        <span className={styles.mono} title={value}>
          {formatUpdatedAt(value)}
        </span>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      align: 'right',
      width: 220,
      render: (_value, role) => {
        const blocked = deleteBlockedReason(role);
        const changing = statusChangingCodes.includes(role.code);
        return (
          <span className={styles.actions}>
            <Button
              type="text"
              size="small"
              disabled={!canUpdate}
              title={updateHint}
              onClick={(event) => onEdit(role, event.currentTarget)}
            >
              编辑
            </Button>
            <Button type="text" size="small" onClick={() => onMembers(role)}>
              成员
            </Button>
            <Button
              type="text"
              size="small"
              loading={changing}
              disabled={!canToggle || statusChangingCodes.length > 0}
              title={toggleHint}
              onClick={() => onStatusAction(role)}
            >
              {isRoleEnabled(role.status) ? '停用' : '启用'}
            </Button>
            <Button
              type="text"
              size="small"
              danger
              disabled={!canDelete || blocked !== null}
              title={blocked ?? deleteHint}
              onClick={(event) => onDelete(role, event.currentTarget)}
            >
              删除
            </Button>
          </span>
        );
      },
    },
  ];

  const emptyText = hasFilters ? (
    <Empty
      image={Empty.PRESENTED_IMAGE_SIMPLE}
      description={
        <span className={styles.emptyText}>
          没有符合条件的角色
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
          还没有任何角色
          <span className={styles.emptyHint}>可以用右上角「新建角色」创建第一个角色。</span>
        </span>
      }
    />
  );

  return (
    <div className={styles.card}>
      <div className={styles.toolbar}>
        <b className={styles.toolbarTitle}>角色列表</b>
        <Tag className={styles.countTag}>{isInitialLoading ? '—' : total}</Tag>
        <span className={styles.spacer} />
        {!(canUpdate || canToggle || canDelete) && (
          <span className={styles.readonlyHint}>只读：当前账号没有角色维护权限</span>
        )}
        <Button size="small" icon={<ReloadOutlined />} loading={isRefreshing} onClick={onRefresh}>
          刷新
        </Button>
      </div>

      <div className={styles.tableWrap}>
        {isInitialLoading ? (
          <div className={styles.skeleton} role="status" aria-live="polite">
            <Skeleton active title={false} paragraph={{ rows: 5 }} />
            <span className={styles.skeletonText}>正在加载角色列表…</span>
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
            <Table<RoleListItem>
              rowKey="code"
              size="small"
              tableLayout="fixed"
              loading={isRefreshing && !refreshError}
              dataSource={roles}
              columns={columns}
              pagination={false}
              locale={{ emptyText }}
            />
          </>
        )}
      </div>

      <TablePager
        total={total}
        page={page}
        pageSize={pageSize}
        itemCount={roles.length}
        onPageChange={onPageChange}
      />
    </div>
  );
}
