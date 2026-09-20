import { PlusOutlined } from '@ant-design/icons';
import type { UnitTreeNode, UnitView } from '@merine/api-contract';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { App, Button, Result, Skeleton } from 'antd';
import { useEffect, useState } from 'react';
import { errorText, isForbiddenError } from '../../shared/api-error';
import { PERMISSIONS, hasPermission } from '../../shared/permissions';
import { ConfirmDialog } from '../../shared/ui/ConfirmDialog';
import { PageHeader } from '../../shared/ui/PageHeader';
import { useAuth } from '../auth/public';
import { deleteUnit } from './api';
import { UnitDetailPanel } from './components/UnitDetailPanel';
import { UnitFormDrawer } from './components/UnitFormDrawer';
import { findUnitName, findUnitTreeNode } from './model';
import { unitKeys, useUnitTreeQuery } from './queries';
import { UnitTreePanel } from './components/UnitTreePanel';
import styles from './UnitListPage.module.css';

interface DrawerState {
  open: boolean;
  target: UnitTreeNode | null;
  defaultParentCode?: string;
}

/**
 * 单位管理：组织树是主视图，选中节点在右侧显示详情和直属下级。
 * 这里不把单位铺成表格，避免三级关系在一行一行里丢失。
 */
export function UnitListPage() {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const { state } = useAuth();
  const me = state.status === 'authenticated' ? state.user : null;
  const tree = useUnitTreeQuery();
  const nodes = tree.data ?? [];
  const [selectedCode, setSelectedCode] = useState<string | null>(null);
  const [search, setSearch] = useState('');
  const [drawer, setDrawer] = useState<DrawerState>({ open: false, target: null });
  const [deleting, setDeleting] = useState<UnitTreeNode | null>(null);
  const [permissionLost, setPermissionLost] = useState(false);

  const canCreate = hasPermission(me?.permissionCodes, PERMISSIONS.unitCreate);
  const canUpdate = hasPermission(me?.permissionCodes, PERMISSIONS.unitUpdate);
  const canDelete = hasPermission(me?.permissionCodes, PERMISSIONS.unitDelete);
  const forbidden =
    permissionLost ||
    isForbiddenError(tree.error) ||
    !hasPermission(me?.permissionCodes, PERMISSIONS.unitRead);
  const selected = selectedCode ? findUnitTreeNode(nodes, selectedCode) : null;
  const parentName = selected ? findUnitName(nodes, selected.parentCode) : null;

  useEffect(() => {
    if (nodes.length === 0) return;
    if (!selectedCode || !findUnitTreeNode(nodes, selectedCode)) {
      setSelectedCode(nodes[0].code);
    }
  }, [nodes, selectedCode]);

  const remove = useMutation({
    mutationFn: (node: UnitTreeNode) => deleteUnit(node.code),
    onSuccess: async (_result, node) => {
      await queryClient.invalidateQueries({ queryKey: unitKeys.all });
      setDeleting(null);
      setSelectedCode(node.parentCode ?? null);
      message.success(`已删除单位 ${node.name}`);
    },
    onError: (error) => {
      if (isForbiddenError(error)) {
        setDeleting(null);
        setPermissionLost(true);
      }
    },
  });

  const openCreate = (parentCode?: string) => {
    setDrawer({ open: true, target: null, defaultParentCode: parentCode });
  };

  const openCreateFromSelection = () => {
    if (!selected) {
      openCreate();
    } else if (selected.level < 3) {
      openCreate(selected.code);
    } else {
      openCreate(selected.parentCode ?? undefined);
    }
  };

  const openEdit = () => {
    if (selected) setDrawer({ open: true, target: selected });
  };

  const openDelete = () => {
    if (!selected) return;
    remove.reset();
    setDeleting(selected);
  };

  const handleSaved = (saved: UnitView) => {
    setDrawer((previous) => ({ ...previous, open: false, target: null }));
    setSelectedCode(saved.code);
  };

  const deleteError =
    remove.isError && !isForbiddenError(remove.error) ? errorText(remove.error) : null;

  return (
    <div className={styles.page}>
      <PageHeader
        demo={false}
        title="单位管理"
        description="维护总队、支队、大队三级组织树；单位编码稳定唯一，删除只允许无下级且无用户的空单位。"
        actions={
          <Button
            type="primary"
            icon={<PlusOutlined />}
            disabled={forbidden || !canCreate}
            title={canCreate ? undefined : '需要「单位管理 · 新增」权限'}
            onClick={openCreateFromSelection}
          >
            新增单位
          </Button>
        }
      />

      {forbidden ? (
        <div className={styles.stateCard}>
          <Result
            status="403"
            title="没有管理系统单位的权限"
            subTitle="单位管理需要具备系统管理角色的账号。这里不显示组织树，也不会因此退出登录。"
          />
        </div>
      ) : tree.isError && nodes.length === 0 ? (
        <div className={styles.stateCard}>
          <Result
            status="warning"
            title="单位组织树加载失败"
            subTitle={errorText(tree.error)}
            extra={
              <Button type="primary" onClick={() => void tree.refetch()}>
                重新加载
              </Button>
            }
          />
        </div>
      ) : tree.isPending && nodes.length === 0 ? (
        <div className={styles.stateCard}>
          <Skeleton active paragraph={{ rows: 8 }} />
        </div>
      ) : (
        <div className={styles.layout}>
          <UnitTreePanel
            nodes={nodes}
            selectedCode={selectedCode}
            onSelect={setSelectedCode}
            search={search}
            onSearchChange={setSearch}
            loading={tree.isFetching}
            error={tree.isError ? errorText(tree.error) : null}
            onRetry={() => void tree.refetch()}
          />
          <UnitDetailPanel
            node={selected}
            parentName={parentName}
            canCreate={canCreate}
            canUpdate={canUpdate}
            canDelete={canDelete}
            onSelectChild={setSelectedCode}
            onAddChild={() => selected && openCreate(selected.code)}
            onEdit={openEdit}
            onDelete={openDelete}
          />
        </div>
      )}

      <UnitFormDrawer
        open={drawer.open}
        target={drawer.target}
        parentTree={nodes}
        defaultParentCode={drawer.defaultParentCode}
        parentLoading={tree.isPending}
        onClose={() => setDrawer((previous) => ({ ...previous, open: false }))}
        onSaved={handleSaved}
        onForbidden={() => setPermissionLost(true)}
      />

      <ConfirmDialog
        open={deleting !== null}
        title={deleting ? `删除单位 ${deleting.name}` : '确认删除'}
        confirmLabel="确认删除"
        danger
        submitting={remove.isPending}
        error={deleteError}
        onCancel={() => setDeleting(null)}
        onConfirm={() => deleting && remove.mutate(deleting)}
      >
        {deleting && (
          <>
            <p className={styles.confirmTarget}>
              单位 <b>{deleting.name}</b>（<span className={styles.mono}>{deleting.code}</span>）
            </p>
            <p>删除后不可恢复，单位编码不再复用；只有无下级单位且无用户的单位可以删除。</p>
          </>
        )}
      </ConfirmDialog>
    </div>
  );
}
