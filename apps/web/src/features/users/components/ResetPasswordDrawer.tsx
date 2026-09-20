import type { ResetPassword, UserSummary } from '@merine/api-contract';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Alert, App, Button, Drawer, Form, Input } from 'antd';
import { useEffect } from 'react';
import { errorText, isForbiddenError } from '../../../shared/api-error';
import { ApiError } from '../../../shared/http';
import { validatePasswordLength } from '../../../shared/password';
import { resetUserPassword } from '../api';
import { userKeys } from '../queries';
import styles from './ResetPasswordDrawer.module.css';

interface ResetFormValues {
  password: string;
}

/**
 * 重置密码抽屉（独立动作，独立权限码）。
 *
 * 「能编辑姓名与角色」不等于「能改别人的密码」，因此这里是单独的入口与接口；
 * 保存后该账号的现有会话立即失效，必须重新登录——弹窗与成功提示都写清这点。
 */
export function ResetPasswordDrawer({
  open,
  target,
  onClose,
  onClosed,
  onSaved,
  onForbidden,
}: {
  open: boolean;
  target: UserSummary | null;
  onClose: () => void;
  onClosed: () => void;
  onSaved: () => void;
  onForbidden: () => void;
}) {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [form] = Form.useForm<ResetFormValues>();

  const save = useMutation({
    mutationFn: (values: ResetFormValues) => {
      const input: ResetPassword = {
        version: target?.version ?? 0,
        newPassword: values.password,
      };
      return resetUserPassword(target?.id as string, input);
    },
    onSuccess: async (saved) => {
      await queryClient.invalidateQueries({ queryKey: userKeys.lists });
      message.success(`已重置 ${saved.loginName} 的密码，其现有会话已失效`);
      onSaved();
    },
    onError: (error) => {
      if (isForbiddenError(error)) {
        onClose();
        onForbidden();
        return;
      }
      if (!(error instanceof ApiError)) return;
      if (error.code === 'USER_VERSION_CONFLICT') {
        void queryClient.invalidateQueries({ queryKey: userKeys.lists });
      }
      const fieldErrors = error.fieldErrors
        .filter((item) => item.field.replace(/\[\d+\]$/, '') === 'newPassword')
        .map((item) => ({ name: 'password' as keyof ResetFormValues, errors: [item.message] }));
      if (fieldErrors.length > 0) form.setFields(fieldErrors);
    },
  });

  const resetSave = save.reset;
  useEffect(() => {
    if (!open) return;
    resetSave();
    form.resetFields();
  }, [open, target?.id, form, resetSave]);

  const failureText = save.isError && !isForbiddenError(save.error) ? errorText(save.error) : null;

  return (
    <Drawer
      open={open}
      size={480}
      destroyOnHidden
      onClose={onClose}
      focusable={{ focusTriggerAfterClose: false }}
      afterOpenChange={(isOpen) => {
        if (!isOpen) onClosed();
      }}
      title={
        <div>
          <div className={styles.title}>重置密码{target ? ` · ${target.displayName}` : ''}</div>
          <div className={styles.desc}>
            账号 <span className={styles.mono}>{target?.loginName ?? ''}</span> 的密码会被替换，
            其现有登录会话立即失效，须用新密码重新登录。
          </div>
        </div>
      }
      footer={
        <div className={styles.foot}>
          <span className={styles.footNote}>
            密码只以哈希保存；本操作需要独立的「重置密码」权限。
          </span>
          <span className={styles.spacer} />
          <Button disabled={save.isPending} onClick={onClose}>
            取消
          </Button>
          <Button type="primary" loading={save.isPending} onClick={() => form.submit()}>
            重置密码
          </Button>
        </div>
      }
    >
      {failureText && (
        <Alert
          className={styles.failure}
          type="error"
          showIcon
          title="重置失败"
          description={failureText}
        />
      )}
      <Form<ResetFormValues>
        form={form}
        layout="vertical"
        onFinish={(values) => save.mutate(values)}
      >
        <Form.Item
          name="password"
          label="新密码"
          extra="至少 6 个字符，最多 72 个 UTF-8 字节（中文通常占 3 字节）。"
          rules={[
            { required: true, whitespace: true, message: '请输入新密码' },
            { min: 6, message: '密码至少 6 个字符' },
            { validator: (_rule, value?: string) => validatePasswordLength(value) },
          ]}
        >
          <Input.Password autoComplete="new-password" placeholder="至少 6 位" />
        </Form.Item>
      </Form>
    </Drawer>
  );
}
