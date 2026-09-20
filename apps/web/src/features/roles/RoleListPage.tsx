import { PlusOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import type { RoleListItem } from '@merine/api-contract';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { App, Button, Result } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { errorText, isForbiddenError } from '../../shared/api-error';
import { PERMISSIONS, hasPermission } from '../../shared/permissions';
import { ConfirmDialog } from '../../shared/ui/ConfirmDialog';
import { PageHeader } from '../../shared/ui/PageHeader';
import { useAuth } from '../auth/public';
import { changeRoleStatus, deleteRole } from './api';
import { RoleFormDrawer } from './components/RoleFormDrawer';
import { RoleMembersDrawer } from './components/RoleMembersDrawer';
import { RoleSearchForm } from './components/RoleSearchForm';
import { RoleTable } from './components/RoleTable';
import {
  EMPTY_ROLE_FILTERS,
  holdsRole,
  isPageOutOfRangeError,
  isRoleEnabled,
  isRoleInUseError,
  isRoleNotFoundError,
  toRoleListQuery,
  type RoleFilters,
  type RoleListQuery,
} from './model';
import { roleKeys, useRoleListQuery } from './queries';
import styles from './RoleListPage.module.css';

interface DrawerState {
  target: RoleListItem | null;
  open: boolean;
}

/** 一次启用/停用请求的意图：写清对哪些角色、做什么，确认弹窗据此成文。 */
interface StatusChangeRequest {
  roles: RoleListItem[];
  enable: boolean;
}

/**
 * 角色管理：查询、列表、编辑抽屉（名称/说明/功能权限）与启停、删除。
 *
 * 权限只决定「能用什么功能」；能看哪些业务数据（数据范围）仍未建模，
 * 页面底部写出这条边界，避免把角色误读成数据授权。
 */
export function RoleListPage() {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const { state } = useAuth();
  const me = state.status === 'authenticated' ? state.user : null;
  const [permissionLost, setPermissionLost] = useState(false);
  const [query, setQuery] = useState<RoleListQuery>(() => toRoleListQuery(EMPTY_ROLE_FILTERS));
  const [drawer, setDrawer] = useState<DrawerState>({ target: null, open: false });
  const [membersTarget, setMembersTarget] = useState<RoleListItem | null>(null);
  const [statusRequest, setStatusRequest] = useState<StatusChangeRequest | null>(null);
  const [deleteRequest, setDeleteRequest] = useState<RoleListItem | null>(null);
  const triggerRef = useRef<HTMLElement | null>(null);

  const canRead = !permissionLost && hasPermission(me?.permissionCodes, PERMISSIONS.roleRead);
  const canCreate = hasPermission(me?.permissionCodes, PERMISSIONS.roleCreate);
  const canUpdate = hasPermission(me?.permissionCodes, PERMISSIONS.roleUpdate);
  const canToggle = hasPermission(me?.permissionCodes, PERMISSIONS.roleToggleStatus);
  const canDelete = hasPermission(me?.permissionCodes, PERMISSIONS.roleDelete);

  const list = useRoleListQuery(query, canRead);
  const forbidden = permissionLost || isForbiddenError(list.error);
  const rows = list.data?.items ?? [];
  const hasFilters = query.keyword !== '' || query.status !== '';

  // 页码越界：停用或筛选后本页可能已不存在，回到第一页并说明原因
  const pageOutOfRange = isPageOutOfRangeError(list.error);
  useEffect(() => {
    if (!pageOutOfRange || query.page === 1) return;
    setQuery((previous) => ({ ...previous, page: 1 }));
    message.warning('请求的页码超出结果范围，已回到第一页');
  }, [pageOutOfRange, query.page, message]);

  const statusChange = useMutation({
    mutationFn: (request: StatusChangeRequest) =>
      changeRoleStatus(
        request.roles.map((role) => role.code),
        request.enable,
      ),
    onSuccess: async (updated, request) => {
      setStatusRequest(null);
      await queryClient.invalidateQueries({ queryKey: roleKeys.all });
      message.success(
        request.roles.length === 1
          ? `已${request.enable ? '启用' : '停用'}角色 ${updated[0]?.name ?? request.roles[0].name}`
          : `已${request.enable ? '启用' : '停用'} ${updated.length} 个角色`,
      );
    },
    onError: (error) => {
      if (isForbiddenError(error)) {
        setStatusRequest(null);
        setPermissionLost(true);
        return;
      }
      if (isRoleNotFoundError(error)) {
        setStatusRequest(null);
        void queryClient.invalidateQueries({ queryKey: roleKeys.all });
        message.error(errorText(error));
      }
      // 其余失败（例如最后一个可用管理员的角色不能停用）留在弹窗里显示原因，可重试或取消
    },
  });

  const remove = useMutation({
    mutationFn: (role: RoleListItem) => deleteRole(role.code, role.version),
    onSuccess: async (_result, role) => {
      setDeleteRequest(null);
      await queryClient.invalidateQueries({ queryKey: roleKeys.all });
      message.success(`已删除角色 ${role.name}`);
    },
    onError: (error) => {
      if (isForbiddenError(error)) {
        setDeleteRequest(null);
        setPermissionLost(true);
        return;
      }
      if (isRoleNotFoundError(error) || isRoleInUseError(error)) {
        setDeleteRequest(null);
        void queryClient.invalidateQueries({ queryKey: roleKeys.all });
        message.error(errorText(error));
      }
    },
  });

  const applyFilters = (filters: RoleFilters) => setQuery(toRoleListQuery(filters));

  const changePage = (page: number) => setQuery((previous) => ({ ...previous, page }));

  const openDrawer = (target: RoleListItem | null, trigger: HTMLElement) => {
    triggerRef.current = trigger;
    setDrawer({ target, open: true });
  };

  const openStatusChange = (roles: RoleListItem[]) => {
    statusChange.reset();
    setStatusRequest({ roles, enable: !isRoleEnabled(roles[0].status) });
  };

  const openDelete = (role: RoleListItem, trigger: HTMLElement) => {
    triggerRef.current = trigger;
    remove.reset();
    setDeleteRequest(role);
  };

  const handleDrawerClosed = () => {
    triggerRef.current?.focus({ preventScroll: true });
    triggerRef.current = null;
  };

  const statusError =
    statusChange.isError && !isForbiddenError(statusChange.error)
      ? errorText(statusChange.error)
      : null;
  const deleteError =
    remove.isError && !isForbiddenError(remove.error) ? errorText(remove.error) : null;
  const statusChangingCodes =
    statusChange.isPending && statusRequest ? statusRequest.roles.map((role) => role.code) : [];

  return (
    <div>
      <PageHeader
        demo={false}
        title="角色管理"
        description="维护角色编码、说明与功能权限；用户在用户管理里被授予角色。角色决定能用什么功能，不决定能看哪些业务数据。"
        actions={
          <Button
            type="primary"
            icon={<PlusOutlined />}
            disabled={!canRead || !canCreate}
            title={canCreate ? undefined : '需要「角色管理 · 新建」权限'}
            onClick={(event) => openDrawer(null, event.currentTarget)}
          >
            新建角色
          </Button>
        }
      />

      <div className={styles.page}>
        {forbidden || !canRead ? (
          <div className={styles.stateCard}>
            <Result
              status="403"
              title="没有管理角色的权限"
              subTitle="角色管理需要「角色管理 · 查看」权限。这里不显示任何角色数据，也不会因此退出登录。"
            />
          </div>
        ) : (
          <>
            <RoleSearchForm
              onSearch={applyFilters}
              onReset={() => applyFilters(EMPTY_ROLE_FILTERS)}
            />

            {list.isError && !list.data ? (
              <div className={styles.stateCard}>
                <Result
                  status="warning"
                  title="角色列表加载失败"
                  subTitle={errorText(list.error)}
                  extra={
                    <Button type="primary" onClick={() => void list.refetch()}>
                      重新加载
                    </Button>
                  }
                />
              </div>
            ) : (
              <RoleTable
                roles={rows}
                total={list.data?.total ?? 0}
                page={query.page}
                pageSize={query.pageSize}
                hasFilters={hasFilters}
                isInitialLoading={list.isPending}
                isRefreshing={list.isFetching}
                refreshError={list.isError && list.data ? errorText(list.error) : null}
                canUpdate={canUpdate}
                canToggle={canToggle}
                canDelete={canDelete}
                statusChangingCodes={statusChangingCodes}
                onPageChange={changePage}
                onEdit={(role, trigger) => openDrawer(role, trigger)}
                onMembers={setMembersTarget}
                onStatusAction={(role) => openStatusChange([role])}
                onDelete={openDelete}
                onRefresh={() => void list.refetch()}
                onRetry={() => void list.refetch()}
                onClearFilters={() => applyFilters(EMPTY_ROLE_FILTERS)}
              />
            )}
          </>
        )}

        <div className={styles.notice}>
          <SafetyCertificateOutlined className={styles.noticeIcon} />
          <p>
            <b>权限边界：</b>角色决定账号<b>能使用哪些功能</b>
            （菜单与接口按同一份权限码判定），<b>不代表能看哪些业务数据</b>
            ；单位数据范围仍未建模，本轮没有可分配的数据范围。内置管理员角色恒拥有全部权限、不可删除。
          </p>
        </div>
      </div>

      <RoleFormDrawer
        open={drawer.open}
        target={drawer.target}
        onClose={() => setDrawer((previous) => ({ ...previous, open: false }))}
        onClosed={handleDrawerClosed}
        onSaved={() => setDrawer((previous) => ({ ...previous, open: false }))}
        onForbidden={() => setPermissionLost(true)}
      />

      <RoleMembersDrawer role={membersTarget} onClose={() => setMembersTarget(null)} />

      <ConfirmDialog
        open={statusRequest !== null}
        title={
          statusRequest
            ? `${statusRequest.enable ? '启用' : '停用'}角色 ${statusRequest.roles[0]?.name ?? ''}`
            : '确认操作'
        }
        confirmLabel={statusRequest?.enable ? '确认启用' : '确认停用'}
        danger={statusRequest?.enable === false}
        submitting={statusChange.isPending}
        error={statusError}
        onCancel={() => setStatusRequest(null)}
        onConfirm={() => statusRequest && statusChange.mutate(statusRequest)}
      >
        {statusRequest && (
          <>
            <p className={styles.confirmTarget}>
              角色 <b>{statusRequest.roles[0].name}</b>（
              <span className={styles.mono}>{statusRequest.roles[0].code}</span>）
            </p>
            <p>
              当前有 <b>{statusRequest.roles[0].userCount}</b> 个账号持有该角色。
              {statusRequest.enable ? (
                <>
                  启用后，这些账号需要<b>重新登录</b>才能取得该角色带来的功能权限。
                </>
              ) : (
                <>
                  停用后：这些账号的现有登录会话<b>立即失效</b>
                  ，重新登录后将失去该角色带来的功能权限； 账号与角色的授予关系<b>全部保留</b>。
                </>
              )}
            </p>
            <p className={styles.confirmNote}>
              服务端始终保证系统里至少有一个还能管理用户与角色的账号，最后一道防线被触及时会拒绝本次操作。
            </p>
            {me && holdsRole(me.roleCodes, statusRequest.roles[0].code) && (
              <p className={styles.confirmNote}>你自己也持有这个角色：操作成功后你需要重新登录。</p>
            )}
          </>
        )}
      </ConfirmDialog>

      <ConfirmDialog
        open={deleteRequest !== null}
        title={deleteRequest ? `删除角色 ${deleteRequest.name}` : '确认删除'}
        confirmLabel="确认删除"
        danger
        submitting={remove.isPending}
        error={deleteError}
        onCancel={() => setDeleteRequest(null)}
        onConfirm={() => deleteRequest && remove.mutate(deleteRequest)}
      >
        {deleteRequest && (
          <>
            <p className={styles.confirmTarget}>
              角色 <b>{deleteRequest.name}</b>（
              <span className={styles.mono}>{deleteRequest.code}</span>）
            </p>
            <p>
              删除后不可恢复，角色编码<b>不再复用</b>
              ；只有没有任何账号持有、且不是内置管理员的角色可以删除。
              删除前请确认已经没有人需要这个角色带来的功能权限。
            </p>
          </>
        )}
      </ConfirmDialog>
    </div>
  );
}
