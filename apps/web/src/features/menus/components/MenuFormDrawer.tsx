import type { IconOption, RouteKeyOption } from '@merine/api-contract';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Alert, App, Button, Drawer, Form, Input, Select, Tag } from 'antd';
import type { InputRef } from 'antd';
import { useEffect, useMemo, useRef } from 'react';
import { errorText, isForbiddenError } from '../../../shared/api-error';
import { ApiError } from '../../../shared/http';
import {
  DICTIONARY_CODES,
  dictLabel,
  toDictOptions,
  useDictionary,
} from '../../dictionaries/public';
import { createMenu, updateMenu } from '../api';
import { allowedChildTypes, permissionCodePrefix, type MenuRow, type MenuType } from '../model';
import { menuKeys } from '../queries';
import { IconPicker } from './IconPicker';
import styles from './MenuFormDrawer.module.css';

interface MenuFormValues {
  type: MenuType;
  name: string;
  routeKey?: string;
  iconName?: string;
  permissionCode?: string;
  description?: string;
  sortOrder: number;
  status: 'ENABLED' | 'DISABLED';
}

/** 字段名与后端请求一致；校验失败时错误能落回对应输入框。 */
const FORM_FIELDS = [
  'type',
  'name',
  'routeKey',
  'iconName',
  'permissionCode',
  'description',
  'sortOrder',
];

function toFieldErrors(fieldErrors: readonly { field: string; message: string }[]) {
  const fields: Partial<Record<string, string>> = {};
  const rest: string[] = [];
  for (const { field, message } of fieldErrors) {
    const name = field.replace(/\[\d+\]$/, '');
    if (FORM_FIELDS.includes(name)) fields[name] ??= message;
    else rest.push(message);
  }
  return { fields, rest };
}

/**
 * 菜单节点抽屉：新增与编辑共用。
 *
 * 类型创建后不可改（它决定 route key 与权限码的约束），权限码创建后也不可改
 * （它是判权用的 authority 与角色授权关系的依据）；改名、说明、排序、上级与状态都可以改。
 */
export function MenuFormDrawer({
  open,
  target,
  parent,
  routeKeys,
  iconOptions,
  onClose,
  onClosed,
  onSaved,
  onForbidden,
}: {
  open: boolean;
  /** null 表示新增 */
  target: MenuRow | null;
  /** 新增时的上级节点；null 表示顶层 */
  parent: MenuRow | null;
  routeKeys: RouteKeyOption[];
  iconOptions: IconOption[];
  onClose: () => void;
  onClosed: () => void;
  onSaved: () => void;
  onForbidden: () => void;
}) {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [form] = Form.useForm<MenuFormValues>();
  const typeDictionary = useDictionary(DICTIONARY_CODES.menuType).data;
  const statusDictionary = useDictionary(DICTIONARY_CODES.status).data;
  const nameRef = useRef<InputRef>(null);
  const editing = target !== null;
  const type = (editing ? target.type : undefined) as MenuType | undefined;
  const watchedType = Form.useWatch('type', form) ?? type;
  const parentType = (parent?.type ?? null) as MenuType | null;
  /**
   * 必须 memo：allowedChildTypes 每次调用都返回新数组，直接进下面的 effect 依赖，
   * 会让"打开时初始化表单"这个 effect 每次渲染都重跑——用户刚输入的字会被 resetFields 抹掉。
   */
  const typeOptions = useMemo(() => allowedChildTypes(parentType), [parentType]);

  const save = useMutation({
    mutationFn: (values: MenuFormValues) => {
      const parentId = editing ? (target.parentId ?? null) : (parent?.id ?? null);
      // 图标只对目录与页面开放：页签/按钮强制为空，与后端校验一致
      const iconName =
        values.type === 'DIRECTORY' || values.type === 'PAGE' ? (values.iconName ?? null) : null;
      if (editing) {
        return updateMenu(target.id, {
          version: target.version,
          parentId,
          name: values.name.trim(),
          routeKey: values.type === 'PAGE' ? (values.routeKey ?? '') : null,
          iconName,
          description: values.description?.trim() ?? '',
          sortOrder: values.sortOrder,
          status: values.status,
        });
      }
      return createMenu({
        parentId,
        type: values.type,
        name: values.name.trim(),
        routeKey: values.type === 'PAGE' ? (values.routeKey ?? '') : null,
        iconName,
        permissionCode: values.type === 'DIRECTORY' ? null : (values.permissionCode ?? '').trim(),
        description: values.description?.trim() ?? '',
        sortOrder: values.sortOrder,
      });
    },
    onSuccess: async (saved) => {
      await queryClient.invalidateQueries({ queryKey: menuKeys.all });
      await queryClient.invalidateQueries({ queryKey: menuKeys.myMenus });
      message.success(editing ? `已保存菜单 ${saved.name}` : `已新增菜单 ${saved.name}`);
      onSaved();
    },
    onError: (error) => {
      if (isForbiddenError(error)) {
        onClose();
        onForbidden();
        return;
      }
      if (!(error instanceof ApiError)) return;
      if (error.code === 'MENU_VERSION_CONFLICT') {
        void queryClient.invalidateQueries({ queryKey: menuKeys.all });
      }
      const mapped = toFieldErrors(error.fieldErrors);
      if (error.code === 'MENU_ROUTE_KEY_UNKNOWN' || error.code === 'MENU_ROUTE_KEY_TAKEN') {
        mapped.fields.routeKey ??= error.message;
      }
      if (error.code === 'MENU_ICON_UNKNOWN') {
        mapped.fields.iconName ??= error.message;
      }
      if (error.code === 'PERMISSION_CODE_TAKEN' || error.code === 'MENU_PERMISSION_TAKEN') {
        mapped.fields.permissionCode ??= error.message;
      }
      const fields = Object.entries(mapped.fields).map(([name, text]) => ({
        name: name as keyof MenuFormValues,
        errors: [text ?? '参数不正确'],
      }));
      if (fields.length > 0) form.setFields(fields);
    },
  });

  const resetSave = save.reset;
  useEffect(() => {
    if (!open) return;
    resetSave();
    form.resetFields();
    if (target) {
      form.setFieldsValue({
        type: target.type as MenuType,
        name: target.name,
        routeKey: target.routeKey ?? undefined,
        iconName: target.iconName ?? undefined,
        permissionCode: target.permissionCode ?? undefined,
        description: target.description ?? '',
        sortOrder: target.sortOrder,
        status: isEnabled(target.status) ? 'ENABLED' : 'DISABLED',
      });
      return;
    }
    form.setFieldsValue({
      type: typeOptions[0],
      name: '',
      sortOrder: 0,
      status: 'ENABLED',
      description: '',
    });
  }, [open, target, form, resetSave, typeOptions]);

  const failureText = (() => {
    if (!save.isError || isForbiddenError(save.error)) return null;
    const base = errorText(save.error);
    if (!(save.error instanceof ApiError) || save.error.fieldErrors.length === 0) return base;
    const { rest } = toFieldErrors(save.error.fieldErrors);
    return rest.length > 0 ? `${base}；${rest.join('；')}` : base;
  })();

  return (
    <Drawer
      open={open}
      size={520}
      destroyOnHidden
      onClose={onClose}
      focusable={{ focusTriggerAfterClose: false }}
      afterOpenChange={(isOpen) => {
        if (isOpen) nameRef.current?.focus();
        else onClosed();
      }}
      title={
        <div>
          <div className={styles.title}>
            {editing
              ? `编辑节点 · ${target.name}`
              : parent
                ? `在「${parent.name}」下新增`
                : '新增顶层节点'}
          </div>
          <div className={styles.desc}>
            节点类型与权限码创建后不可修改；页面只能绑定前端已注册的路由
            key，导航图标从注册清单选择。
          </div>
        </div>
      }
      footer={
        <div className={styles.foot}>
          <span className={styles.footNote}>
            停用只影响导航展示与可分配性，已授权的功能仍然可用。
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
      <Form<MenuFormValues>
        form={form}
        layout="vertical"
        className={styles.form}
        onFinish={(values) => save.mutate(values)}
      >
        <Form.Item
          name="type"
          label="节点类型"
          rules={[{ required: true, message: '请选择节点类型' }]}
        >
          <Select
            disabled={editing}
            options={typeOptions.map((option) => ({
              value: option,
              label: dictLabel(typeDictionary, option),
            }))}
          />
        </Form.Item>
        <Form.Item
          name="name"
          label="节点名称"
          rules={[
            { required: true, message: '请输入节点名称' },
            { max: 80, message: '节点名称最多 80 个字符' },
          ]}
        >
          <Input ref={nameRef} placeholder="例如 用户管理 / 新建用户" autoComplete="off" />
        </Form.Item>
        {watchedType === 'PAGE' && (
          <Form.Item
            name="routeKey"
            label="路由 key"
            extra="只能选前端已注册的页面；新增页面需要前端发版并登记。"
            rules={[{ required: true, message: '请选择路由 key' }]}
          >
            <Select
              showSearch
              optionFilterProp="label"
              placeholder="选择已注册页面"
              options={routeKeys.map((route) => ({
                value: route.key,
                label: `${route.label}（${route.key}）`,
              }))}
            />
          </Form.Item>
        )}
        {(watchedType === 'DIRECTORY' || watchedType === 'PAGE') && (
          <Form.Item
            name="iconName"
            label="导航图标"
            extra="默认表示回落到路由注册表图标（目录为文件夹）；图标清单与前端注册表同步。"
          >
            <IconPicker options={iconOptions} />
          </Form.Item>
        )}
        {watchedType && watchedType !== 'DIRECTORY' && (
          <Form.Item
            name="permissionCode"
            label="权限码"
            extra="创建后不可修改；页面一个码、按钮一个码，角色按权限码授权。"
            rules={[{ required: true, message: '请输入权限码' }]}
          >
            <Input
              className={styles.monoInput}
              disabled={editing}
              placeholder={`${permissionCodePrefix(parent?.permissionCode) || 'system:module:'}action`}
              autoComplete="off"
            />
          </Form.Item>
        )}
        <Form.Item name="sortOrder" label="排序" extra="同级排序，越小越靠前">
          <Input type="number" min={0} />
        </Form.Item>
        {editing && (
          <Form.Item name="status" label="状态" extra="停用只影响导航与可分配性">
            <Select options={toDictOptions(statusDictionary)} />
          </Form.Item>
        )}
        <Form.Item
          name="description"
          label="说明"
          rules={[{ max: 200, message: '说明最多 200 个字符' }]}
        >
          <Input.TextArea rows={2} maxLength={200} showCount placeholder="可以留空" />
        </Form.Item>
        {editing && target.permissionCode && (
          <p className={styles.readonlyNote}>
            当前权限码：<Tag className={styles.code}>{target.permissionCode}</Tag>
          </p>
        )}
      </Form>
    </Drawer>
  );
}

function isEnabled(status: string): boolean {
  return status === 'ENABLED';
}
