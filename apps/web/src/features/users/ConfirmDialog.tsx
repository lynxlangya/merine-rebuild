import { WarningOutlined } from '@ant-design/icons';
import { Alert, Button, Modal } from 'antd';
import type { ReactNode } from 'react';
import styles from './ConfirmDialog.module.css';

/**
 * 影响确认弹窗（440px，非 Ant Design 默认宽度）。
 * 禁用/启用这类操作先写清对象与结果再执行；确认按钮在请求进行中进入 loading
 * 并禁用，失败原因留在弹窗里，用户可以直接重试或取消。
 */
export function ConfirmDialog({
  open,
  title,
  confirmLabel,
  danger = false,
  submitting,
  error,
  onCancel,
  onConfirm,
  children,
}: {
  open: boolean;
  title: string;
  confirmLabel: string;
  /** 破坏性操作（禁用）用 danger 主按钮 */
  danger?: boolean;
  submitting: boolean;
  error: string | null;
  onCancel: () => void;
  onConfirm: () => void;
  children: ReactNode;
}) {
  return (
    <Modal
      open={open}
      width={440}
      centered
      mask={{ closable: false }}
      keyboard={!submitting}
      onCancel={onCancel}
      title={
        <span className={styles.title}>
          <WarningOutlined className={styles.icon} />
          {title}
        </span>
      }
      footer={
        <div className={styles.foot}>
          <Button disabled={submitting} onClick={onCancel}>
            取消
          </Button>
          <Button type="primary" danger={danger} loading={submitting} onClick={onConfirm}>
            {confirmLabel}
          </Button>
        </div>
      }
    >
      <div className={styles.text}>{children}</div>
      {error && (
        <Alert
          className={styles.error}
          type="error"
          showIcon
          title="操作失败"
          description={error}
        />
      )}
    </Modal>
  );
}
