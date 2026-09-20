import type { CreateRole, MenuNode, RoleListItem, UpdateRole } from '@merine/api-contract';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Alert, App, Button, Drawer, Form, Input, Skeleton, Tag, Tree } from 'antd';
import type { InputRef } from 'antd';
import { useEffect, useMemo, useRef } from 'react';
import { errorText, isForbiddenError } from '../../../shared/api-error';
import { ApiError } from '../../../shared/http';
import { useAuth } from '../../auth/public';
import { createRole, updateRole } from '../api';
import {
  collectPermissionCodes,
  deriveCheckState,
  holdsRole,
  toPermissionNodes,
  toRoleFormFieldErrors,
  type PermissionNode,
} from '../model';
import { roleKeys, useRoleDetailQuery, useRolePermissionTreeQuery } from '../queries';
import styles from './RoleFormDrawer.module.css';

/** 表单值：新建与编辑共用一个表单；角色编码只在新建时可填。 */
interface RoleFormValues {
  code: string;
  name: string;
  description: string;
  permissionCodes: string[];
}

/** 角色编码格式与后端 @Pattern 一致，错误文案也保持一致，避免两处口径不同。 */
const ROLE_CODE_PATTERN = /^[A-Za-z0-9._-]{1,64}$/;

function sameCodes(left: readonly string[], right: readonly string[]): boolean {
  if (left.length !== right.length) return false;
  const sorted = [...right].sort();
  return [...left].sort().every((code, index) => code === sorted[index]);
}

/**
 * 紧凑树形权限勾选（独立勾选 + 自行推导父行状态）。
 *
 * 为什么不用 Ant Design 默认的父子联动：默认联动下「勾页面」会把子按钮一起勾上，
 * 于是「只给页面查看、不给按钮」这种最常见的只读角色无法表达，保存后回显也会骗人。
 * 这里改成每个权限码独立勾选：勾一个页面（或目录）会把它的后代一起勾上，方便批量；
 * 取消某个按钮后，父行按「部分选中」显示半选，回显与保存的集合始终一致。
 */
function PermissionTreeSelect({
  nodes,
  disabled,
  value = [],
  onChange,
}: {
  nodes: readonly MenuNode[];
  disabled?: boolean;
  // Form.Item 注入的受控属性
  value?: string[];
  onChange?: (next: string[]) => void;
}) {
  const permissionNodes = useMemo(() => toPermissionNodes(nodes), [nodes]);
  const selected = useMemo(() => new Set(value), [value]);
  const nodeByKey = useMemo(() => {
    const map = new Map<string, PermissionNode>();
    const walk = (list: readonly PermissionNode[]) => {
      for (const node of list) {
        map.set(node.key, node);
        if (node.children) walk(node.children);
      }
    };
    walk(permissionNodes);
    return map;
  }, [permissionNodes]);
  const checkState = useMemo(
    () => deriveCheckState(permissionNodes, selected),
    [permissionNodes, selected],
  );

  const treeData = useMemo(() => {
    const toNode = (node: PermissionNode): Record<string, unknown> => ({
      key: node.key,
      title: (
        <span className={styles.treeRow}>
          <span className={styles.treeName}>{node.name}</span>
          {node.code && <span className={styles.treeCode}>{node.code}</span>}
          {node.status !== 'ENABLED' && <Tag className={styles.treeTag}>已停用</Tag>}
        </span>
      ),
      // 停用节点不能再新勾选，但已勾选的保留，否则会把历史授权悄悄抹掉
      disableCheckbox:
        disabled || (node.status !== 'ENABLED' && node.code !== null && !selected.has(node.code)),
      children: node.children?.map(toNode),
    });
    return permissionNodes.map(toNode);
  }, [permissionNodes, selected, disabled]);

  return (
    <Tree
      checkable
      checkStrictly
      selectable={false}
      defaultExpandAll
      treeData={treeData}
      checkedKeys={checkState}
      onCheck={(_checked, info) => {
        const node = nodeByKey.get(String(info.node.key));
        if (!node) return;
        const next = new Set(selected);
        for (const code of collectPermissionCodes(node)) {
          if (info.checked) next.add(code);
          else next.delete(code);
        }
        onChange?.([...next].sort());
      }}
    />
  );
}

/**
 * 新建 / 编辑角色抽屉（560px）。
 *
 * 编辑时先用列表行填基本信息，权限集合与勾选树一起等详情返回后补上；两者都只在本次打开时填一次，
 * 之后重新拉取详情不会覆盖用户正在输入的内容。
 */
export function RoleFormDrawer({
  open,
  target,
  onClose,
  onClosed,
  onSaved,
  onForbidden,
}: {
  open: boolean;
  /** null 表示新建 */
  target: RoleListItem | null;
  onClose: () => void;
  /** 关闭动画结束后触发，用于把焦点还给触发按钮 */
  onClosed: () => void;
  onSaved: () => void;
  onForbidden: () => void;
}) {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const { state } = useAuth();
  const me = state.status === 'authenticated' ? state.user : null;
  const [form] = Form.useForm<RoleFormValues>();
  const nameRef = useRef<InputRef>(null);

  const editing = target !== null;
  const detail = useRoleDetailQuery(open && target ? target.code : null);
  const permissionTree = useRolePermissionTreeQuery(open);
  const builtin = target?.builtin ?? false;

  const save = useMutation({
    mutationFn: (values: RoleFormValues) => {
      if (target) {
        const input: UpdateRole = {
          version: target.version,
          name: values.name.trim(),
          description: values.description.trim(),
          permissionCodes: values.permissionCodes,
        };
        return updateRole(target.code, input);
      }
      const input: CreateRole = {
        code: values.code.trim(),
        name: values.name.trim(),
        description: values.description.trim(),
        permissionCodes: values.permissionCodes,
      };
      return createRole(input);
    },
    onSuccess: async (saved, values) => {
      await queryClient.invalidateQueries({ queryKey: roleKeys.all });
      if (target) {
        const permissionsChanged = !sameCodes(
          detail.data?.permissionCodes ?? [],
          values.permissionCodes,
        );
        message.success(
          permissionsChanged
            ? `已保存角色 ${saved.name}，持有该角色的账号需要重新登录`
            : `已保存角色 ${saved.name}`,
        );
      } else {
        message.success(`已创建角色 ${saved.name}`);
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
      if (error.code === 'ROLE_VERSION_CONFLICT') {
        // 保留本次输入供核对；列表刷新后关闭重开才能取到新版本
        void queryClient.invalidateQueries({ queryKey: roleKeys.all });
      }
      const mapped = toRoleFormFieldErrors(error.fieldErrors);
      if (error.code === 'ROLE_CODE_TAKEN') mapped.fields.code ??= error.message;
      if (error.code === 'UNKNOWN_PERMISSION') mapped.fields.permissionCodes ??= error.message;
      const fields = Object.entries(mapped.fields).map(([name, text]) => ({
        name: name as keyof RoleFormValues,
        errors: [text],
      }));
      if (fields.length > 0) form.setFields(fields);
    },
  });

  const resetSave = save.reset;
  const filledFor = useRef<string | null>(null);
  const permissionsAppliedFor = useRef<string | null>(null);

  // 打开时把表单重置到本次对象的值；关闭时清掉标记，下次打开重新填
  useEffect(() => {
    if (!open) {
      filledFor.current = null;
      permissionsAppliedFor.current = null;
      return;
    }
    resetSave();
    form.resetFields();
    if (!target) {
      filledFor.current = 'create';
      form.setFieldsValue({ code: '', name: '', description: '', permissionCodes: [] });
      return;
    }
    filledFor.current = target.code;
    form.setFieldsValue({
      code: target.code,
      name: target.name,
      description: target.description ?? '',
      permissionCodes: [],
    });
  }, [open, target, form, resetSave]);

  // 详情到了再补权限集合；同一个角色的详情只应用一次，后续刷新不覆盖正在编辑的内容
  useEffect(() => {
    if (!open || !target || !detail.data) return;
    if (permissionsAppliedFor.current === target.code) return;
    permissionsAppliedFor.current = target.code;
    form.setFieldsValue({ permissionCodes: detail.data.permissionCodes });
  }, [open, target, detail.data, form]);

  const treeNodes = permissionTree.data ?? [];
  const permissionNames = useMemo(() => {
    const map = new Map<string, string>();
    const walk = (list: readonly MenuNode[]) => {
      for (const node of list) {
        if (node.permissionCode) map.set(node.permissionCode, node.name);
        walk(node.children);
      }
    };
    walk(treeNodes);
    return map;
  }, [treeNodes]);

  const selectedCodes = Form.useWatch('permissionCodes', form) ?? [];
  const nameValue = Form.useWatch('name', form);
  const selectedNames = selectedCodes.map((code) => permissionNames.get(code) ?? code);
  const selfAffected = editing && holdsRole(me?.roleCodes, target.code);

  const failureText = (() => {
    if (!save.isError || isForbiddenError(save.error)) return null;
    const base = errorText(save.error);
    if (!(save.error instanceof ApiError) || save.error.fieldErrors.length === 0) return base;
    const { rest } = toRoleFormFieldErrors(save.error.fieldErrors);
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
        if (isOpen) nameRef.current?.focus();
        else onClosed();
      }}
      title={
        <div>
          <div className={styles.title}>{target ? `编辑角色 · ${target.name}` : '新建角色'}</div>
          <div className={styles.desc}>
            {target ? (
              <>
                编码 <span className={styles.mono}>{target.code}</span> 不可修改；
                权限集合变化后，持有该角色的账号现有会话将失效。
              </>
            ) : (
              '角色编码创建后不可修改；权限决定这个角色能使用哪些功能，不代表能看哪些业务数据。'
            )}
          </div>
        </div>
      }
      footer={
        <div className={styles.foot}>
          <span className={styles.footNote}>
            {target
              ? '只改名称与说明不影响在线会话；权限变化会让持有者重新登录。'
              : '保存后立即可在用户管理里分配给账号。'}
          </span>
          <span className={styles.spacer} />
          <Button disabled={save.isPending} onClick={onClose}>
            取消
          </Button>
          <Button type="primary" loading={save.isPending} onClick={() => form.submit()}>
            保存
          </Button>
        </div>
      }
    >
      {failureText && (
        <Alert
          className={styles.failure}
          type="error"
          showIcon
          title="保存失败"
          description={failureText}
        />
      )}
      {detail.isError && !isForbiddenError(detail.error) && (
        <Alert
          className={styles.failure}
          type="warning"
          showIcon
          title="角色详情加载失败"
          description={errorText(detail.error)}
          action={
            <Button size="small" onClick={() => void detail.refetch()}>
              重试
            </Button>
          }
        />
      )}

      <Form<RoleFormValues>
        form={form}
        layout="vertical"
        className={styles.form}
        onFinish={(values) => save.mutate(values)}
      >
        <section className={styles.section}>
          <div className={styles.sectionTitle}>基本信息</div>
          <Form.Item
            name="code"
            label="角色编码"
            rules={
              editing
                ? []
                : [
                    { required: true, message: '请输入角色编码' },
                    {
                      pattern: ROLE_CODE_PATTERN,
                      message: '角色编码只能使用字母、数字与 . _ -，长度 1–64',
                    },
                  ]
            }
          >
            <Input
              className={styles.monoInput}
              disabled={editing}
              placeholder="例如 ORG_OPERATOR"
              autoComplete="off"
            />
          </Form.Item>
          <Form.Item
            name="name"
            label="角色名称"
            rules={[
              { required: true, message: '请输入角色名称' },
              { max: 80, message: '角色名称最多 80 个字符' },
            ]}
          >
            <Input ref={nameRef} placeholder="例如 单位业务人员" autoComplete="off" />
          </Form.Item>
          <Form.Item
            name="description"
            label="说明"
            extra="写给使用者看：这个角色能用哪些功能。改说明不影响在线会话。"
            rules={[{ max: 200, message: '角色说明最多 200 个字符' }]}
          >
            <Input.TextArea rows={2} maxLength={200} showCount placeholder="可以留空" />
          </Form.Item>
        </section>

        <section className={styles.section}>
          <div className={styles.sectionTitle}>
            功能权限
            <span className={styles.sectionNote}>
              {builtin ? '内置角色：全部权限只读' : `已选 ${selectedCodes.length} 项`}
            </span>
          </div>
          {permissionTree.isError ? (
            <Alert
              type="error"
              showIcon
              title="权限树加载失败"
              description={errorText(permissionTree.error)}
              action={
                <Button size="small" onClick={() => void permissionTree.refetch()}>
                  重试
                </Button>
              }
            />
          ) : permissionTree.isPending ? (
            <Skeleton active paragraph={{ rows: 4 }} />
          ) : (
            <Form.Item name="permissionCodes" noStyle>
              <PermissionTreeSelect nodes={treeNodes} disabled={builtin || save.isPending} />
            </Form.Item>
          )}
        </section>

        <section className={styles.section}>
          <div className={styles.sectionTitle}>授权摘要</div>
          <dl className={styles.summary}>
            <dt className={styles.term}>角色</dt>
            <dd className={styles.detail}>
              {nameValue || '未命名'}
              {builtin && (
                <Tag className={styles.builtinTag}>内置管理员角色，恒拥有全部权限、不可删除</Tag>
              )}
            </dd>
            <dt className={styles.term}>功能权限</dt>
            <dd className={styles.detail}>
              {selectedNames.length > 0 ? selectedNames.join('、') : '未勾选任何权限'}
            </dd>
            <dt className={styles.term}>变更影响</dt>
            <dd className={styles.detail}>
              {builtin
                ? '内置角色的权限不可修改；停用后持有它的账号将失去管理系统功能，服务端会保证仍有其它可用管理员。'
                : '权限集合变化后，持有该角色的账号现有会话立即失效，须重新登录；只改名称与说明不影响在线会话。'}
              {selfAffected && (
                <span className={styles.selfNote}>
                  你自己也持有这个角色：保存后你需要重新登录。
                </span>
              )}
            </dd>
          </dl>
        </section>
      </Form>
    </Drawer>
  );
}
