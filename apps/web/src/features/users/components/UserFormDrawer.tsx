import { Alert, Button, Checkbox, Drawer, Form, Input, Skeleton, Tag } from 'antd';
import type { InputRef } from 'antd';
import type { CreateUser, RoleSummary, UpdateUser, UserSummary } from '@merine/api-contract';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef } from 'react';
import { App } from 'antd';
import { ApiError } from '../../../shared/http';
import { validatePasswordLength } from '../../../shared/password';
import { createUser, updateUser } from '../api';
import { toFormFieldErrors } from '../model';
import { errorText, isForbiddenError } from '../../../shared/api-error';
import { AuthorizationSummary } from './AuthorizationSummary';
import { UnitTreeSelect, useUnitOptionsQuery } from '../../units/public';
import { useRoleOptionsQuery } from '../../roles/public';
import { userKeys } from '../queries';
import styles from './UserFormDrawer.module.css';

/** 表单值：新建与编辑共用一个表单；账号只在新建时可填，密码在编辑时留空表示不改。 */
interface UserFormValues {
  loginName: string;
  displayName: string;
  unitCode?: string;
  roleCodes: string[];
  password: string;
}

/** 账号格式与后端 @Pattern 一致，错误文案也保持一致，避免两处口径不同。 */
const LOGIN_NAME_PATTERN = /^[a-z0-9.]{4,32}$/;

function sameCodes(left: readonly string[], right: readonly string[]): boolean {
  if (left.length !== right.length) return false;
  const sorted = [...right].sort();
  return [...left].sort().every((code, index) => code === sorted[index]);
}

/** 表单到请求体：账号两端空格不属于账号本身，密码原样提交。 */
function toCreateInput(values: UserFormValues): CreateUser {
  return {
    loginName: values.loginName.trim(),
    displayName: values.displayName.trim(),
    unitCode: values.unitCode ?? '',
    roleCodes: values.roleCodes,
    password: values.password,
  };
}

/** 编辑只改姓名、所属单位与角色：状态与密码各有独立的动作接口与权限码。 */
function toUpdateInput(values: UserFormValues, version: number): UpdateUser {
  return {
    version,
    displayName: values.displayName.trim(),
    unitCode: values.unitCode ?? '',
    roleCodes: values.roleCodes,
  };
}

/** 角色多选卡：设计稿是单选卡，后端是用户-角色多对多，这里按多选实现。 */
function RoleCheckboxes({
  roles,
  value = [],
  onChange,
  disabled = false,
}: {
  roles: RoleSummary[];
  value?: string[];
  onChange?: (next: string[]) => void;
  disabled?: boolean;
}) {
  return (
    <div className={styles.roles}>
      {roles.map((role) => {
        const checked = value.includes(role.code);
        // 已停用的角色不能再分配；账号已持有的停用角色保留勾选，不会被静默摘掉
        const unavailable = role.status !== 'ENABLED' && !checked;
        return (
          <label
            key={role.code}
            className={styles.roleCard}
            data-checked={checked}
            data-disabled={unavailable}
          >
            <Checkbox
              className={styles.roleCheckbox}
              checked={checked}
              disabled={disabled || unavailable}
              onChange={(event) =>
                onChange?.(
                  event.target.checked
                    ? [...value, role.code]
                    : value.filter((code) => code !== role.code),
                )
              }
            />
            <span className={styles.roleBody}>
              <span className={styles.roleName}>
                {role.name}
                <span className={styles.roleCode}>{role.code}</span>
              </span>
              {role.status !== 'ENABLED' && (
                <span className={styles.roleDesc}>
                  该角色已停用，不能再分配；已持有它的账号保持原样。
                </span>
              )}
            </span>
          </label>
        );
      })}
    </div>
  );
}

/**
 * 新建 / 编辑用户抽屉（560px）。
 *
 * 表单值由 antd Form 管理，抽屉关闭即销毁，下次打开不会串用上一次的草稿。
 * 失败时保留输入：字段错误回填到对应字段，其余原因显示在抽屉顶部并带请求编号；
 * 403 不弹提示，交由页面切到「没有管理用户的权限」状态。
 */
export function UserFormDrawer({
  open,
  target,
  onClose,
  onClosed,
  onSaved,
  onForbidden,
}: {
  open: boolean;
  /** null 表示新建 */
  target: UserSummary | null;
  onClose: () => void;
  /** 关闭动画结束后触发，用于把焦点还给触发按钮 */
  onClosed: () => void;
  onSaved: () => void;
  onForbidden: () => void;
}) {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [form] = Form.useForm<UserFormValues>();
  const units = useUnitOptionsQuery();
  const roles = useRoleOptionsQuery();
  const accountRef = useRef<InputRef>(null);
  const nameRef = useRef<InputRef>(null);

  const editing = target !== null;
  const editingUser = target;
  const targetId = editingUser?.id ?? null;

  const save = useMutation({
    mutationFn: (values: UserFormValues) =>
      editingUser
        ? updateUser(editingUser.id, toUpdateInput(values, editingUser.version))
        : createUser(toCreateInput(values)),
    onSuccess: async (saved, values) => {
      await queryClient.invalidateQueries({ queryKey: userKeys.lists });
      if (editingUser) {
        const permissionsChanged =
          !sameCodes(editingUser.roleCodes, values.roleCodes) ||
          editingUser.unitCode !== values.unitCode;
        message.success(
          permissionsChanged
            ? `已保存用户 ${saved.displayName}，其现有会话已失效`
            : `已保存用户 ${saved.displayName}`,
        );
      } else {
        message.success(`已创建用户 ${saved.displayName}`);
      }
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
        // 保留本次输入供核对；列表刷新后，关闭并重新打开才能取得新版本。
        void queryClient.invalidateQueries({ queryKey: userKeys.lists });
      }
      // 字段错误必须落回表单字段，未知字段显示为整体提示
      const mapped = toFormFieldErrors(error.fieldErrors);
      if (error.code === 'LOGIN_NAME_TAKEN') mapped.fields.loginName ??= error.message;
      const fields = Object.entries(mapped.fields).map(([name, messageText]) => ({
        name: name as keyof UserFormValues,
        errors: [messageText],
      }));
      if (fields.length > 0) {
        form.setFields(fields);
        const first = fields[0].name;
        if (first === 'loginName') accountRef.current?.focus();
        else if (first === 'displayName') nameRef.current?.focus();
      }
    },
  });

  // 新建时所属单位留空（undefined 而不是 ''），Select 才会显示 placeholder 而不是某个选项
  const initialValues: UserFormValues = editingUser
    ? {
        loginName: editingUser.loginName,
        displayName: editingUser.displayName,
        unitCode: editingUser.unitCode,
        roleCodes: [...editingUser.roleCodes],
        password: '',
      }
    : { loginName: '', displayName: '', unitCode: undefined, roleCodes: [], password: '' };

  const resetSave = save.reset;

  /**
   * 每次打开都把表单重置到本次对象的值。
   * 关闭抽屉只销毁 DOM，antd Form 的 Store 仍挂在组件上，不重置就会串用上一次的草稿
   * （例如新建完成后再次点新建，账号与密码还留着上一次的输入）；resetFields 同时清掉
   * 上一次的校验错误。依赖里放 targetId 而不是对象本身：行数据每次刷新都会换新对象，
   * 而这里只需要「打开时」和「目标换人时」两个时机。
   */
  useEffect(() => {
    if (!open) return;
    resetSave();
    form.resetFields();
    form.setFieldsValue(initialValues);
  }, [open, targetId, form, resetSave]); // eslint-disable-line react-hooks/exhaustive-deps

  const rolesUnavailable = roles.isError;
  const selectedRoleCodes = Form.useWatch('roleCodes', form) ?? initialValues.roleCodes;
  const selectedRoleNames = selectedRoleCodes.map(
    (code) => roles.data?.find((role) => role.code === code)?.name ?? code,
  );

  // 认不出字段名的校验错误没有对应输入框可落，附在整体提示里；丢掉会让用户
  // 只看到"参数不正确"却不知道是哪个字段。
  const failureText = (() => {
    if (!save.isError || isForbiddenError(save.error)) return null;
    const base = errorText(save.error);
    if (!(save.error instanceof ApiError) || save.error.fieldErrors.length === 0) return base;
    const { rest } = toFormFieldErrors(save.error.fieldErrors);
    return rest.length > 0 ? `${base}；${rest.join('；')}` : base;
  })();

  return (
    <Drawer
      open={open}
      size={560}
      destroyOnHidden
      onClose={onClose}
      focusable={{ focusTriggerAfterClose: false }}
      afterOpenChange={(isOpen) => {
        if (isOpen) {
          // 焦点进入首个可编辑字段：编辑态账号只读，落在姓名上
          if (editing) nameRef.current?.focus();
          else accountRef.current?.focus();
        } else {
          onClosed();
        }
      }}
      title={
        <div>
          <div className={styles.title}>
            {editingUser ? `编辑用户 · ${editingUser.displayName}` : '新建用户'}
          </div>
          <div className={styles.desc}>
            {editingUser ? (
              <>
                账号 <span className={styles.mono}>{editingUser.loginName}</span> 不可修改；角色、
                所属单位或密码变化后，该账号现有会话将失效。
              </>
            ) : (
              '带 * 的字段为必填。账号创建后即可用初始密码登录。'
            )}
          </div>
        </div>
      }
      footer={
        <div className={styles.foot}>
          <span className={styles.footNote}>
            {editingUser
              ? '角色、所属单位或密码有变化时，该账号现有会话将失效。'
              : '保存后立即生效。'}
          </span>
          <span className={styles.spacer} />
          <Button type="text" size="small" disabled={save.isPending} onClick={onClose}>
            取消
          </Button>
          <Button
            type="primary"
            size="small"
            loading={save.isPending}
            disabled={save.isPending}
            onClick={() => form.submit()}
          >
            保存
          </Button>
        </div>
      }
    >
      <Form<UserFormValues>
        form={form}
        layout="vertical"
        requiredMark
        disabled={save.isPending}
        initialValues={initialValues}
        onFinish={(values) => save.mutate(values)}
        className={styles.form}
      >
        {failureText && (
          <Alert
            className={styles.failure}
            type="error"
            showIcon
            role="alert"
            title={editingUser ? '保存失败' : '创建失败'}
            description={failureText}
            action={
              save.error instanceof ApiError && save.error.code === 'USER_VERSION_CONFLICT' ? (
                <Button size="small" onClick={onClose}>
                  关闭并查看最新数据
                </Button>
              ) : undefined
            }
          />
        )}

        <section className={styles.section}>
          <h3 className={styles.sectionTitle}>基本信息</h3>

          <Form.Item
            name="loginName"
            label="账号"
            extra={
              editingUser
                ? '账号保存后不可修改。'
                : '小写字母、数字与点，长度 4–32；保存后不可修改。'
            }
            rules={
              editingUser
                ? []
                : [
                    { required: true, whitespace: true, message: '请输入账号' },
                    {
                      pattern: LOGIN_NAME_PATTERN,
                      message: '账号只能使用小写字母、数字与点，长度 4–32',
                    },
                  ]
            }
          >
            <Input
              ref={accountRef}
              className={styles.mono}
              disabled={editing}
              autoComplete="off"
              spellCheck={false}
              autoCapitalize="off"
              placeholder="例如 demo.yanpan.i"
            />
          </Form.Item>

          <Form.Item
            name="displayName"
            label="姓名"
            extra="填写用于业务识别的姓名。"
            rules={[
              { required: true, whitespace: true, message: '请输入姓名' },
              { max: 80, message: '姓名最多 80 个字符' },
            ]}
          >
            <Input ref={nameRef} autoComplete="off" spellCheck={false} placeholder="例如 陈知远" />
          </Form.Item>

          <Form.Item
            name="unitCode"
            label="所属单位"
            rules={[{ required: true, message: '请选择所属单位' }]}
            extra={
              units.isError
                ? '单位列表加载失败，保存会因单位不存在而失败。'
                : '只列出已启用的单位。'
            }
          >
            <UnitTreeSelect
              className={styles.select}
              loading={units.isPending}
              placeholder="请选择所属单位"
              currentCode={editingUser?.unitCode}
            />
          </Form.Item>

          {!editingUser && (
            <Form.Item
              name="password"
              label="初始密码"
              extra="至少 6 个字符，最多 72 个 UTF-8 字节（中文通常占 3 字节）。重置密码是独立动作，创建后在列表里操作。"
              rules={[
                { required: true, whitespace: true, message: '请输入初始密码' },
                { min: 6, message: '密码至少 6 个字符' },
                { validator: (_rule, value?: string) => validatePasswordLength(value) },
              ]}
            >
              <Input.Password autoComplete="new-password" placeholder="至少 6 位" />
            </Form.Item>
          )}
        </section>

        <section className={styles.section}>
          <h3 className={styles.sectionTitle}>
            角色（功能权限）
            <span className={styles.sectionNote}>可多选，决定「能使用什么功能」</span>
          </h3>
          {roles.isPending ? (
            <Skeleton active title={false} paragraph={{ rows: 2 }} />
          ) : rolesUnavailable ? (
            <Alert
              type="warning"
              showIcon
              title="角色列表加载失败"
              description="没有角色选项时无法保存，请先重新加载。"
              action={
                <Button size="small" onClick={() => void roles.refetch()}>
                  重新加载
                </Button>
              }
            />
          ) : (
            <Form.Item
              name="roleCodes"
              rules={[
                {
                  validator: (_rule, value?: string[]) =>
                    value && value.length > 0
                      ? Promise.resolve()
                      : Promise.reject(new Error('请至少选择一个角色')),
                },
              ]}
            >
              <RoleCheckboxes roles={roles.data ?? []} disabled={save.isPending} />
            </Form.Item>
          )}
        </section>

        <section className={styles.section}>
          <h3 className={styles.sectionTitle}>授权摘要（保存前请确认）</h3>
          <AuthorizationSummary
            roleNames={selectedRoleNames}
            mode={editingUser ? 'edit' : 'create'}
          />
        </section>

        {editingUser && (
          <p className={styles.identity}>
            当前状态：
            <Tag className={styles.statusTag}>
              {editingUser.status === 'ENABLED' ? '启用' : '已禁用'}
            </Tag>
            账号状态不在这里修改，请在列表中用「启用 / 禁用」操作。
          </p>
        )}
      </Form>
    </Drawer>
  );
}
