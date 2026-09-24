import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, App, Button, Empty, Result, Skeleton, Table, Tag } from 'antd';
import type { TableColumnsType } from 'antd';
import { useMemo, useRef, useState } from 'react';
import { iconByName } from '../../app/iconRegistry';
import { errorText, isForbiddenError } from '../../shared/api-error';
import { PERMISSIONS, hasPermission } from '../../shared/permissions';
import { ConfirmDialog } from '../../shared/ui/ConfirmDialog';
import { PageHeader } from '../../shared/ui/PageHeader';
import { useAuth } from '../auth/public';
import { DICTIONARY_CODES, DictTag, dictLabel, useDictionary } from '../dictionaries/public';
import { deleteMenu, fetchMenuDeleteImpact, restoreMenus } from './api';
import { MenuFormDrawer } from './components/MenuFormDrawer';
import {
  allowedChildTypes,
  defaultMenuExpandedKeys,
  toMenuRows,
  type MenuRow,
  type MenuType,
} from './model';
import { menuKeys, useIconOptionsQuery, useMenuTreeQuery, useRouteKeysQuery } from './queries';
import styles from './MenuListPage.module.css';

interface DrawerState {
  open: boolean;
  target: MenuRow | null;
  parent: MenuRow | null;
}

/**
 * 菜单管理：一棵树维护目录、页面、页签与按钮。
 *
 * 页面只能绑前端已注册的 route key；按钮/页签各自带一个权限码，角色的授权界面
 * 直接把这棵树当勾选树用。删除会连同子树、权限码与角色授权关系一起处理，
 * 因此确认弹窗先把影响数字摆出来。
 */
export function MenuListPage() {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const { state } = useAuth();
  const me = state.status === 'authenticated' ? state.user : null;
  const [permissionLost, setPermissionLost] = useState(false);
  const [drawer, setDrawer] = useState<DrawerState>({ open: false, target: null, parent: null });
  const [deleteTarget, setDeleteTarget] = useState<MenuRow | null>(null);
  const [restoreOpen, setRestoreOpen] = useState(false);
  const triggerRef = useRef<HTMLElement | null>(null);

  const canRead = !permissionLost && hasPermission(me?.permissionCodes, PERMISSIONS.menuRead);
  const canCreate = hasPermission(me?.permissionCodes, PERMISSIONS.menuCreate);
  const canUpdate = hasPermission(me?.permissionCodes, PERMISSIONS.menuUpdate);
  const canDelete = hasPermission(me?.permissionCodes, PERMISSIONS.menuDelete);
  const canRestore = hasPermission(me?.permissionCodes, PERMISSIONS.menuRestore);

  const tree = useMenuTreeQuery(canRead);
  const routeKeys = useRouteKeysQuery(canRead);
  const iconOptions = useIconOptionsQuery(canRead);
  const typeDictionary = useDictionary(DICTIONARY_CODES.menuType).data;
  const statusDictionary = useDictionary(DICTIONARY_CODES.status).data;
  const nodes = tree.data ?? [];
  // 叶子节点去掉空 children（否则按钮行前面会多一个没用的展开箭头）
  const rows = useMemo(() => toMenuRows(nodes), [nodes]);
  const expandedKeys = useMemo(() => defaultMenuExpandedKeys(rows), [rows]);
  const forbidden = permissionLost || isForbiddenError(tree.error) || !canRead;

  const impact = useQuery({
    queryKey: [...menuKeys.all, 'delete-impact', deleteTarget?.id ?? ''],
    queryFn: ({ signal }) => fetchMenuDeleteImpact(deleteTarget?.id as string, signal),
    enabled: deleteTarget !== null,
  });

  const remove = useMutation({
    mutationFn: (node: MenuRow) => deleteMenu(node.id, node.version),
    onSuccess: async (result, node) => {
      setDeleteTarget(null);
      await queryClient.invalidateQueries({ queryKey: menuKeys.all });
      await queryClient.invalidateQueries({ queryKey: menuKeys.myMenus });
      message.success(
        `已删除「${node.name}」，共 ${result.deletedNodes} 个节点，${result.affectedRoles} 个角色失去授权`,
      );
    },
    onError: (error) => {
      if (isForbiddenError(error)) {
        setDeleteTarget(null);
        setPermissionLost(true);
        return;
      }
      void queryClient.invalidateQueries({ queryKey: menuKeys.all });
    },
  });

  const restore = useMutation({
    mutationFn: restoreMenus,
    onSuccess: async (result) => {
      setRestoreOpen(false);
      await queryClient.invalidateQueries({ queryKey: menuKeys.all });
      await queryClient.invalidateQueries({ queryKey: menuKeys.myMenus });
      message.success(
        result.createdMenus === 0
          ? '默认菜单已经齐全，没有需要补齐的节点'
          : `已补齐 ${result.createdMenus} 个菜单节点、${result.createdPermissions} 个权限码`,
      );
    },
    onError: (error) => {
      if (isForbiddenError(error)) {
        setRestoreOpen(false);
        setPermissionLost(true);
      }
    },
  });

  const openCreate = (parent: MenuRow | null, trigger: HTMLElement) => {
    triggerRef.current = trigger;
    setDrawer({ open: true, target: null, parent });
  };

  const openEdit = (node: MenuRow, trigger: HTMLElement) => {
    triggerRef.current = trigger;
    setDrawer({ open: true, target: node, parent: null });
  };

  const openDelete = (node: MenuRow, trigger: HTMLElement) => {
    triggerRef.current = trigger;
    remove.reset();
    setDeleteTarget(node);
  };

  const handleDrawerClosed = () => {
    triggerRef.current?.focus({ preventScroll: true });
    triggerRef.current = null;
  };

  const columns: TableColumnsType<MenuRow> = [
    {
      title: '节点名称',
      dataIndex: 'name',
      width: 260,
      render: (value: string, node) => (
        <span className={styles.name}>
          {value}
          {node.type === 'PAGE' && !node.routeKey && <Tag className={styles.warnTag}>缺路由</Tag>}
        </span>
      ),
    },
    {
      title: '类型',
      dataIndex: 'type',
      width: 84,
      render: (value: string) => <DictTag dictionary={typeDictionary} value={value} />,
    },
    {
      title: '图标',
      dataIndex: 'iconName',
      width: 132,
      render: (value: string | null) =>
        value ? (
          <span className={styles.iconCell}>
            {iconByName(value)}
            <span className={styles.mono}>{value}</span>
          </span>
        ) : (
          <span className={styles.muted}>默认</span>
        ),
    },
    {
      title: '路由 key',
      dataIndex: 'routeKey',
      width: 168,
      render: (value: string | null) =>
        value ? (
          <span className={styles.mono}>{value}</span>
        ) : (
          <span className={styles.muted}>—</span>
        ),
    },
    {
      title: '权限码',
      dataIndex: 'permissionCode',
      width: 236,
      render: (value: string | null) =>
        value ? (
          <span className={styles.mono}>{value}</span>
        ) : (
          <span className={styles.muted}>—</span>
        ),
    },
    { title: '排序', dataIndex: 'sortOrder', width: 72, align: 'right' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 88,
      render: (value: string) => <DictTag dictionary={statusDictionary} value={value} />,
    },
    ...(canCreate || canUpdate || canDelete
      ? [
          {
            title: '操作',
            key: 'actions',
            align: 'right' as const,
            width: 208,
            render: (_value: MenuRow, node: MenuRow) => {
              // 按钮是叶子：不展示「新增下级」，也不留一个永远禁用的按钮
              const canAddChild = allowedChildTypes(node.type as MenuType).length > 0;
              return (
                <span className={styles.actions}>
                  {canCreate && canAddChild && (
                    <Button
                      type="text"
                      size="small"
                      onClick={(event) => openCreate(node, event.currentTarget)}
                    >
                      新增下级
                    </Button>
                  )}
                  {canUpdate && (
                    <Button
                      type="text"
                      size="small"
                      onClick={(event) => openEdit(node, event.currentTarget)}
                    >
                      编辑
                    </Button>
                  )}
                  {canDelete && (
                    <Button
                      type="text"
                      size="small"
                      danger
                      onClick={(event) => openDelete(node, event.currentTarget)}
                    >
                      删除
                    </Button>
                  )}
                </span>
              );
            },
          },
        ]
      : []),
  ];

  return (
    <div>
      <PageHeader
        demo={false}
        title="菜单管理"
        description="维护目录、页面、页签与按钮；页面只能绑前端已注册的路由 key。角色的功能权限直接来自这棵树。"
        actions={
          <>
            {canRestore && (
              <Button
                loading={restore.isPending}
                onClick={() => {
                  restore.reset();
                  setRestoreOpen(true);
                }}
              >
                恢复默认菜单
              </Button>
            )}
            {canCreate && (
              <Button
                type="primary"
                icon={<PlusOutlined />}
                onClick={(event) => openCreate(null, event.currentTarget)}
              >
                新增顶层节点
              </Button>
            )}
          </>
        }
      />

      <div className={styles.page}>
        {forbidden ? (
          <div className={styles.stateCard}>
            <Result
              status="403"
              title="没有管理菜单的权限"
              subTitle="菜单管理需要「菜单管理 · 查看」权限。这里不显示任何菜单数据，也不会因此退出登录。"
            />
          </div>
        ) : tree.isError && nodes.length === 0 ? (
          <div className={styles.stateCard}>
            <Result
              status="warning"
              title="菜单树加载失败"
              subTitle={errorText(tree.error)}
              extra={
                <Button type="primary" onClick={() => void tree.refetch()}>
                  重新加载
                </Button>
              }
            />
          </div>
        ) : (
          <div className={styles.card}>
            <div className={styles.toolbar}>
              <b className={styles.toolbarTitle}>菜单树</b>
              <Tag className={styles.countTag}>{tree.isPending ? '—' : nodes.length}</Tag>
              <span className={styles.spacer} />
              {routeKeys.isError && (
                <span className={styles.hint}>路由 key 清单加载失败，新增页面会缺少可选项。</span>
              )}
              {iconOptions.isError && (
                <span className={styles.hint}>图标清单加载失败，设置导航图标时会缺少可选项。</span>
              )}
              <Button
                size="small"
                icon={<ReloadOutlined />}
                loading={tree.isFetching}
                onClick={() => void tree.refetch()}
              >
                刷新
              </Button>
            </div>

            {tree.isError && nodes.length > 0 && (
              <Alert
                className={styles.banner}
                type="warning"
                showIcon
                title="刷新失败，以上为上一次的结果"
                description={errorText(tree.error)}
              />
            )}

            <div className={styles.tableWrap}>
              {tree.isPending ? (
                <div className={styles.skeleton} role="status" aria-live="polite">
                  <Skeleton active title={false} paragraph={{ rows: 6 }} />
                  <span className={styles.skeletonText}>正在加载菜单树…</span>
                </div>
              ) : (
                <Table<MenuRow>
                  rowKey="id"
                  size="small"
                  tableLayout="fixed"
                  loading={tree.isFetching && !tree.isError}
                  dataSource={rows}
                  columns={columns}
                  pagination={false}
                  expandable={{ defaultExpandedRowKeys: expandedKeys }}
                  locale={{
                    emptyText: (
                      <Empty
                        image={Empty.PRESENTED_IMAGE_SIMPLE}
                        description="菜单树是空的，可以用「恢复默认菜单」补齐引导节点"
                      />
                    ),
                  }}
                />
              )}
            </div>
          </div>
        )}

        <div className={styles.notice}>
          隐藏菜单不等于禁止访问：接口始终按权限码判定，停用节点只影响导航展示与可分配性。
          删除节点会连同子树、权限码与角色授权关系一起处理。
        </div>
      </div>

      <MenuFormDrawer
        open={drawer.open}
        target={drawer.target}
        parent={drawer.parent}
        routeKeys={routeKeys.data ?? []}
        iconOptions={iconOptions.data ?? []}
        onClose={() => setDrawer((previous) => ({ ...previous, open: false }))}
        onClosed={handleDrawerClosed}
        onSaved={() => setDrawer((previous) => ({ ...previous, open: false }))}
        onForbidden={() => setPermissionLost(true)}
      />

      <ConfirmDialog
        open={deleteTarget !== null}
        title={deleteTarget ? `删除节点 ${deleteTarget.name}` : '确认删除'}
        confirmLabel="确认删除"
        danger
        submitting={remove.isPending}
        error={remove.isError && !isForbiddenError(remove.error) ? errorText(remove.error) : null}
        onCancel={() => setDeleteTarget(null)}
        onConfirm={() => deleteTarget && remove.mutate(deleteTarget)}
      >
        {deleteTarget && (
          <>
            <p className={styles.confirmTarget}>
              {dictLabel(typeDictionary, deleteTarget.type)} <b>{deleteTarget.name}</b>
              {deleteTarget.permissionCode && (
                <>
                  （<span className={styles.mono}>{deleteTarget.permissionCode}</span>）
                </>
              )}
            </p>
            <p>
              删除会<b>连同整棵子树</b>一起移除，并解除相关角色的授权；
              {impact.isPending && '正在统计影响…'}
              {impact.data && (
                <>
                  本次将删除 <b>{impact.data.deletedNodes}</b> 个节点，
                  <b>{impact.data.affectedRoles}</b> 个角色会失去授权（其持有者需要重新登录）。
                </>
              )}
              {impact.isError && '影响预览加载失败，删除仍会执行同样的级联处理。'}
            </p>
            <p className={styles.confirmNote}>
              删除后不可恢复；如果删掉的是页面或按钮，也可以用「恢复默认菜单」把引导节点补回来。
            </p>
          </>
        )}
      </ConfirmDialog>

      <ConfirmDialog
        open={restoreOpen}
        title="恢复默认菜单"
        confirmLabel="确认补齐"
        submitting={restore.isPending}
        error={
          restore.isError && !isForbiddenError(restore.error) ? errorText(restore.error) : null
        }
        onCancel={() => setRestoreOpen(false)}
        onConfirm={() => restore.mutate()}
      >
        <p>
          恢复只会<b>补齐缺失的引导节点与权限码</b>，不会删除也不会覆盖你改过的名称、排序与说明；
          已有的同名目录会直接复用。
        </p>
      </ConfirmDialog>
    </div>
  );
}
