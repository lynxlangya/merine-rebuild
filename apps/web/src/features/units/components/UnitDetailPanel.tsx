import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons';
import type { UnitTreeNode } from '@merine/api-contract';
import { Button, Descriptions, Empty, Tag, Tooltip } from 'antd';
import type { ReactNode } from 'react';
import { unitLevelLabel } from '../model';
import styles from './UnitDetailPanel.module.css';

function formatInstant(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '时间未知';
  const pad = (part: number) => String(part).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(
    date.getHours(),
  )}:${pad(date.getMinutes())}`;
}

function ActionButton({
  disabled,
  title,
  children,
  onClick,
}: {
  disabled: boolean;
  title: string;
  children: ReactNode;
  onClick: () => void;
}) {
  return disabled ? (
    <Tooltip title={title}>
      <span>
        <Button size="small" disabled>
          {children}
        </Button>
      </span>
    </Tooltip>
  ) : (
    <Button size="small" onClick={onClick}>
      {children}
    </Button>
  );
}

export function UnitDetailPanel({
  node,
  parentName,
  canCreate,
  canUpdate,
  canDelete,
  onSelectChild,
  onAddChild,
  onEdit,
  onDelete,
}: {
  node: UnitTreeNode | null;
  parentName: string | null;
  /** 按钮级权限：无权限时按钮保留可见但禁用，并写明原因 */
  canCreate: boolean;
  canUpdate: boolean;
  canDelete: boolean;
  onSelectChild: (code: string) => void;
  onAddChild: () => void;
  onEdit: () => void;
  onDelete: () => void;
}) {
  if (!node) {
    return (
      <section className={styles.card} aria-label="单位详情">
        <Empty description="从左侧组织树选择一个单位" />
      </section>
    );
  }

  const cannotAddChild = node.level >= 3;
  const blockedByReference = node.childCount > 0 || node.userCount > 0;
  const deleteReason = !canDelete
    ? '需要「单位管理 · 删除」权限'
    : node.childCount > 0
      ? '该单位还有下级单位，不能删除'
      : node.userCount > 0
        ? '该单位还有用户，不能删除'
        : '';

  return (
    <section className={styles.card} aria-label={`${node.name}详情`}>
      <div className={styles.head}>
        <div className={styles.identity}>
          <div className={styles.titleRow}>
            <h2 className={styles.title}>{node.name}</h2>
            <Tag className={styles.levelTag} variant="filled">
              {unitLevelLabel(node.level)}
            </Tag>
            {node.status !== 'ENABLED' && <Tag className={styles.disabledTag}>已停用</Tag>}
          </div>
          <span className={styles.code}>{node.code}</span>
        </div>
        <div className={styles.actions}>
          <ActionButton
            disabled={!canCreate || cannotAddChild}
            title={
              !canCreate
                ? '需要「单位管理 · 新增」权限'
                : cannotAddChild
                  ? '大队已是第三级，不能继续新增下级'
                  : ''
            }
            onClick={onAddChild}
          >
            <PlusOutlined /> 新增下级
          </ActionButton>
          <ActionButton
            disabled={!canUpdate}
            title={canUpdate ? '' : '需要「单位管理 · 编辑」权限'}
            onClick={onEdit}
          >
            <EditOutlined /> 编辑
          </ActionButton>
          <ActionButton
            disabled={!canDelete || blockedByReference}
            title={deleteReason}
            onClick={onDelete}
          >
            <DeleteOutlined /> 删除
          </ActionButton>
        </div>
      </div>

      <Descriptions column={2} size="small" colon={false} className={styles.details}>
        <Descriptions.Item label="单位编码">
          <span className={styles.mono}>{node.code}</span>
        </Descriptions.Item>
        <Descriptions.Item label="单位层级">{unitLevelLabel(node.level)}</Descriptions.Item>
        <Descriptions.Item label="上级单位">{parentName ?? '无（一级单位）'}</Descriptions.Item>
        <Descriptions.Item label="行政区划">
          <span className={styles.mono}>{node.areaCode ?? '—'}</span>
        </Descriptions.Item>
        <Descriptions.Item label="直属下级">{node.childCount}</Descriptions.Item>
        <Descriptions.Item label="直属用户">{node.userCount}</Descriptions.Item>
        <Descriptions.Item label="最近修改">{formatInstant(node.updatedAt)}</Descriptions.Item>
        <Descriptions.Item label="编辑版本">
          <span className={styles.mono}>{node.version}</span>
        </Descriptions.Item>
      </Descriptions>

      <div className={styles.children}>
        <div className={styles.childrenHead}>
          <b>直属下级</b>
          <Tag className={styles.countTag}>{node.children.length}</Tag>
        </div>
        {node.children.length === 0 ? (
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description="暂无下级单位"
            className={styles.childrenEmpty}
          />
        ) : (
          <div className={styles.childList}>
            {node.children.map((child) => (
              <button
                key={child.code}
                type="button"
                className={styles.child}
                onClick={() => onSelectChild(child.code)}
              >
                <span className={styles.childMain}>
                  <span className={styles.childName}>{child.name}</span>
                  <span className={styles.childMeta}>
                    <span className={styles.mono}>{child.code}</span>
                    <span>·</span>
                    <span>{unitLevelLabel(child.level)}</span>
                  </span>
                </span>
                <span className={styles.childCount}>
                  下级 {child.childCount} · 用户 {child.userCount}
                </span>
              </button>
            ))}
          </div>
        )}
      </div>
    </section>
  );
}
