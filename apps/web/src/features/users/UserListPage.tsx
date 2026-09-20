import { LockOutlined, PlusOutlined } from '@ant-design/icons';
import type { UserSummary } from '@merine/api-contract';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { App, Button, Result } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { PageHeader } from '../../shared/ui/PageHeader';
import {
  EMPTY_USER_FILTERS,
  changeUserStatus,
  errorText,
  isForbiddenError,
  isPageOutOfRangeError,
  isUserEnabled,
  isUserNotFoundError,
  toUserListQuery,
  type UserFilters,
  type UserListQuery,
} from './api';
import { ConfirmDialog } from './ConfirmDialog';
import { userKeys, useUserListQuery } from './queries';
import { UserFormDrawer } from './UserFormDrawer';
import { UserSearchForm } from './UserSearchForm';
import { UserTable } from './UserTable';
import styles from './UserListPage.module.css';

interface DrawerState {
  target: UserSummary | null;
  open: boolean;
}

/** 一次启用/停用请求的意图：写清对谁、做什么，确认弹窗据此成文。 */
interface StatusChangeRequest {
  ids: string[];
  enable: boolean;
  /** 弹窗要写出对象，行内操作带一个用户，批量操作带当前页选中的用户 */
  users: UserSummary[];
}

/**
 * P04 用户管理：查询、列表、编辑抽屉与启用/停用，数据全部来自 /api/system/users。
 * 设计稿里的「数据范围」单位树本轮没有后端模型，整块不做；「授权摘要」只保留
 * 角色与真实存在的会话失效说明。
 */
export function UserListPage() {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [query, setQuery] = useState<UserListQuery>(() => toUserListQuery(EMPTY_USER_FILTERS));
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [drawer, setDrawer] = useState<DrawerState>({ target: null, open: false });
  const [statusRequest, setStatusRequest] = useState<StatusChangeRequest | null>(null);
  const [permissionLost, setPermissionLost] = useState(false);
  const triggerRef = useRef<HTMLElement | null>(null);

  const list = useUserListQuery(query);
  const forbidden = permissionLost || isForbiddenError(list.error);
  const rows = list.data?.items ?? [];
  const hasFilters =
    query.keyword !== '' || query.unitCode !== '' || query.roleCode !== '' || query.status !== '';

  // 页码越界：停用或筛选后本页可能已不存在，服务端明确报错（400 PAGE_OUT_OF_RANGE），
  // 这里回到第一页并说明原因，而不是把它显示成一次普通加载失败。
  const pageOutOfRange = isPageOutOfRangeError(list.error);
  useEffect(() => {
    if (!pageOutOfRange || query.page === 1) return;
    setQuery((previous) => ({ ...previous, page: 1 }));
    setSelectedIds([]);
    message.warning('请求的页码超出结果范围，已回到第一页');
  }, [pageOutOfRange, query.page, message]);

  const statusChange = useMutation({
    mutationFn: (request: StatusChangeRequest) => changeUserStatus(request.ids, request.enable),
    onSuccess: async (updated, request) => {
      setStatusRequest(null);
      setSelectedIds([]);
      await queryClient.invalidateQueries({ queryKey: userKeys.lists });
      message.success(
        request.ids.length === 1
          ? `已${request.enable ? '启用' : '禁用'}账号 ${updated[0]?.loginName ?? request.ids[0]}`
          : `已${request.enable ? '启用' : '禁用'} ${updated.length} 个账号`,
      );
    },
    onError: (error) => {
      if (isForbiddenError(error)) {
        setStatusRequest(null);
        setPermissionLost(true);
        return;
      }
      // 对象已不存在：提示后刷新列表，让页面回到服务端的真实状态
      if (isUserNotFoundError(error)) {
        setStatusRequest(null);
        void queryClient.invalidateQueries({ queryKey: userKeys.lists });
        message.error(errorText(error));
      }
      // 其余失败（例如最后一个管理员不能停用）留在弹窗里显示原因，可重试或取消
    },
  });

  const applyFilters = (filters: UserFilters) => {
    setQuery(toUserListQuery(filters));
    setSelectedIds([]);
  };

  const changePage = (page: number) => {
    setQuery((previous) => ({ ...previous, page }));
    setSelectedIds([]);
  };

  const openDrawer = (target: UserSummary | null, trigger: HTMLElement) => {
    triggerRef.current = trigger;
    setDrawer({ target, open: true });
  };

  const closeDrawer = () => setDrawer((previous) => ({ ...previous, open: false }));

  // 抽屉关闭动画结束后把焦点还给触发按钮
  const handleDrawerClosed = () => {
    triggerRef.current?.focus({ preventScroll: true });
    triggerRef.current = null;
  };

  const openStatusChange = (request: StatusChangeRequest) => {
    statusChange.reset();
    setStatusRequest(request);
  };

  const requestSingleChange = (user: UserSummary) => {
    openStatusChange({
      ids: [user.id],
      enable: !isUserEnabled(user.status),
      users: [user],
    });
  };

  const requestBulkDisable = () => {
    const picked = rows.filter((user) => selectedIds.includes(user.id));
    openStatusChange({ ids: selectedIds, enable: false, users: picked });
  };

  const confirmStatusChange = () => {
    if (statusRequest) statusChange.mutate(statusRequest);
  };

  const describeTarget = (request: StatusChangeRequest) => {
    if (request.users.length === 1) {
      const user = request.users[0];
      return (
        <>
          账号 <b className={styles.mono}>{user.loginName}</b>（{user.displayName}）
        </>
      );
    }
    const head = request.users.slice(0, 5);
    return (
      <>
        已选中的 <b>{request.ids.length}</b> 个账号
        {head.length > 0 && (
          <>
            ：{head.map((user) => user.loginName).join('、')}
            {request.ids.length > head.length && ` 等 ${request.ids.length} 个`}
          </>
        )}
      </>
    );
  };

  const statusError =
    statusChange.isError && !isForbiddenError(statusChange.error)
      ? errorText(statusChange.error)
      : null;

  return (
    <div>
      <PageHeader
        demo={false}
        title="用户管理"
        description="账号、所属单位、角色与启用状态。账号管理权限不包含跨单位业务资料的访问范围。"
        actions={
          <Button
            type="primary"
            icon={<PlusOutlined />}
            disabled={forbidden}
            onClick={(event) => openDrawer(null, event.currentTarget)}
          >
            新建用户
          </Button>
        }
      />

      <div className={styles.page}>
        {forbidden ? (
          <div className={styles.stateCard}>
            <Result
              status="403"
              title="没有管理用户的权限"
              subTitle="用户管理需要具备用户管理角色（如系统管理员）的账号。这里不显示任何用户数据，也不会因此退出登录。"
            />
          </div>
        ) : (
          <>
            <UserSearchForm
              onSearch={applyFilters}
              onReset={() => applyFilters(EMPTY_USER_FILTERS)}
            />

            {list.isError && !list.data ? (
              <div className={styles.stateCard}>
                <Result
                  status="warning"
                  title="用户列表加载失败"
                  subTitle={errorText(list.error)}
                  extra={
                    <Button type="primary" onClick={() => void list.refetch()}>
                      重新加载
                    </Button>
                  }
                />
              </div>
            ) : (
              <UserTable
                users={rows}
                total={list.data?.total ?? 0}
                page={query.page}
                pageSize={query.pageSize}
                selectedIds={selectedIds}
                hasFilters={hasFilters}
                isInitialLoading={list.isPending}
                isRefreshing={list.isFetching}
                refreshError={list.isError && list.data ? errorText(list.error) : null}
                onPageChange={changePage}
                onSelectedIdsChange={setSelectedIds}
                onEdit={(user, trigger) => openDrawer(user, trigger)}
                onStatusAction={requestSingleChange}
                onBulkDisable={requestBulkDisable}
                onRefresh={() => void list.refetch()}
                onRetry={() => void list.refetch()}
                onClearFilters={() => applyFilters(EMPTY_USER_FILTERS)}
              />
            )}
          </>
        )}

        <div className={styles.notice}>
          <LockOutlined className={styles.noticeIcon} />
          <p>
            <b>权限边界：</b>用户管理只负责账号、所属单位与角色，<b>不因此获得</b>
            查看其他单位业务资料的权限；业务数据的可见范围尚未建模，本轮没有可分配的「数据范围」。
          </p>
        </div>
      </div>

      <UserFormDrawer
        open={drawer.open}
        target={drawer.target}
        onClose={closeDrawer}
        onClosed={handleDrawerClosed}
        onSaved={closeDrawer}
        onForbidden={() => setPermissionLost(true)}
      />

      <ConfirmDialog
        open={statusRequest !== null}
        title={
          statusRequest
            ? statusRequest.ids.length === 1
              ? `${statusRequest.enable ? '启用' : '禁用'}账号 ${statusRequest.users[0]?.displayName ?? ''}`
              : `${statusRequest.enable ? '批量启用' : '批量禁用'} ${statusRequest.ids.length} 个账号`
            : '确认操作'
        }
        confirmLabel={statusRequest?.enable ? '确认启用' : '确认禁用'}
        danger={statusRequest?.enable === false}
        submitting={statusChange.isPending}
        error={statusError}
        onCancel={() => setStatusRequest(null)}
        onConfirm={confirmStatusChange}
      >
        {statusRequest && (
          <>
            <p className={styles.confirmTarget}>{describeTarget(statusRequest)}</p>
            <p>
              {statusRequest.enable ? (
                <>
                  启用后该账号可以重新登录，并按<b>原有的角色</b>
                  访问系统；如需调整权限，请同时编辑该用户。
                </>
              ) : (
                <>
                  禁用后：该账号的现有登录会话<b>立即失效</b>，无法再登录；已有记录
                  <b>全部保留</b>；重新启用后可按原有角色继续使用。
                </>
              )}
            </p>
            {statusRequest.enable === false && (
              <p className={styles.confirmNote}>
                最后一个具备用户管理角色的启用账号不能停用，服务端会拒绝这类请求。
              </p>
            )}
          </>
        )}
      </ConfirmDialog>
    </div>
  );
}
