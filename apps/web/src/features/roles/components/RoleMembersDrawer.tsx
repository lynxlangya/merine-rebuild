import type { RoleListItem, RoleMember } from '@merine/api-contract';
import { Alert, Button, Drawer, Empty, Skeleton, Table, Tag } from 'antd';
import type { TableColumnsType } from 'antd';
import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { errorText } from '../../../shared/api-error';
import { PERMISSIONS, hasPermission } from '../../../shared/permissions';
import { TablePager } from '../../../shared/ui/TablePager';
import { useAuth } from '../../auth/public';
import { ROLE_PAGE_SIZE } from '../model';
import { useRoleMembersQuery } from '../queries';
import styles from './RoleMembersDrawer.module.css';

/**
 * 角色成员（只读）。
 *
 * 授权关系只在用户管理里写：这里回答「谁在用这个角色」，并给出按角色筛选用户列表的入口，
 * 不做增删成员——同一个写入保持一条路径，避免两处各自实现同一套规则。
 */
export function RoleMembersDrawer({
  role,
  onClose,
}: {
  /** null 表示关闭 */
  role: RoleListItem | null;
  onClose: () => void;
}) {
  const { state } = useAuth();
  const me = state.status === 'authenticated' ? state.user : null;
  const canOpenUsers = hasPermission(me?.permissionCodes, PERMISSIONS.userRead);
  const [page, setPage] = useState(1);

  // 换一个角色就回到第一页，避免把上一个角色的页码带过来
  useEffect(() => setPage(1), [role?.code]);

  const members = useRoleMembersQuery(role?.code ?? null, page, ROLE_PAGE_SIZE);
  const rows = members.data?.items ?? [];

  const columns: TableColumnsType<RoleMember> = [
    {
      title: '账号',
      dataIndex: 'loginName',
      width: 168,
      render: (value: string) => <span className={styles.mono}>{value}</span>,
    },
    { title: '姓名', dataIndex: 'displayName', width: 120 },
    { title: '所属单位', dataIndex: 'unitName' },
    {
      title: '账号状态',
      dataIndex: 'status',
      width: 96,
      render: (value: RoleMember['status']) =>
        value === 'ENABLED' ? (
          <Tag className={styles.tag}>启用</Tag>
        ) : (
          <Tag className={styles.tagOff}>已禁用</Tag>
        ),
    },
  ];

  return (
    <Drawer
      open={role !== null}
      width={640}
      onClose={onClose}
      destroyOnHidden
      title={
        <div className={styles.title}>
          {role ? `成员 · ${role.name}` : '成员'}
          {role && <span className={styles.code}>{role.code}</span>}
        </div>
      }
    >
      {role && (
        <div className={styles.body}>
          <p className={styles.note}>
            成员清单只读：给账号授予或移除角色仍在
            <b>用户管理</b>
            里完成，这里只回答「谁在用这个角色」。
          </p>

          {members.isError && !members.data ? (
            <Alert
              type="error"
              showIcon
              title="成员清单加载失败"
              description={errorText(members.error)}
              action={
                <Button size="small" onClick={() => void members.refetch()}>
                  重试
                </Button>
              }
            />
          ) : members.isPending ? (
            <Skeleton active paragraph={{ rows: 4 }} />
          ) : (
            <>
              {members.isError && members.data && (
                <Alert
                  className={styles.banner}
                  type="warning"
                  showIcon
                  title="刷新失败，以下为上一次的结果"
                  description={errorText(members.error)}
                />
              )}
              <Table<RoleMember>
                rowKey="id"
                size="small"
                tableLayout="fixed"
                loading={members.isFetching && !members.isError}
                dataSource={rows}
                columns={columns}
                pagination={false}
                locale={{
                  emptyText: (
                    <Empty
                      image={Empty.PRESENTED_IMAGE_SIMPLE}
                      description="还没有账号持有这个角色"
                    />
                  ),
                }}
              />
              <TablePager
                total={members.data?.total ?? 0}
                page={page}
                pageSize={ROLE_PAGE_SIZE}
                itemCount={rows.length}
                onPageChange={setPage}
              />
            </>
          )}

          <div className={styles.foot}>
            {canOpenUsers ? (
              <Link to={`/system/users?roleCode=${encodeURIComponent(role.code)}`}>
                <Button>在用户管理中查看这些账号</Button>
              </Link>
            ) : (
              <span className={styles.footHint}>
                需要「用户管理 · 查看」权限才能在用户管理里按角色筛选账号。
              </span>
            )}
          </div>
        </div>
      )}
    </Drawer>
  );
}
