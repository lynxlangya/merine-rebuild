import type { DictionaryItemView } from '@merine/api-contract';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Alert, App, Button, Drawer, Form, Input, Select } from 'antd';
import type { InputRef } from 'antd';
import { useEffect, useRef } from 'react';
import { errorText, isForbiddenError } from '../../../shared/api-error';
import { ApiError } from '../../../shared/http';
import { createDictionaryItem, updateDictionaryItem } from '../api';
import { DICTIONARY_CODES, toDictOptions, useDictionary } from '../public';
import { dictionaryKeys } from '../queries';
import styles from './DictionaryDrawer.module.css';

interface ItemFormValues {
  value: string;
  label: string;
  description?: string;
  sortOrder: number;
  status: 'ENABLED' | 'DISABLED';
}

const FORM_FIELDS = ['value', 'label', 'description', 'sortOrder', 'status'];

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
 * 字典项抽屉：取值创建后不可修改（业务数据保存的就是它），标签、说明、排序与状态可改。
 * 「停用」只让它不再出现在选择项里，历史数据仍然能解析出标签。
 */
export function DictionaryItemDrawer({
  open,
  dictionaryCode,
  target,
  onClose,
  onClosed,
  onSaved,
  onForbidden,
}: {
  open: boolean;
  dictionaryCode: string;
  /** null 表示新建 */
  target: DictionaryItemView | null;
  onClose: () => void;
  onClosed: () => void;
  onSaved: () => void;
  onForbidden: () => void;
}) {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [form] = Form.useForm<ItemFormValues>();
  const statusDictionary = useDictionary(DICTIONARY_CODES.status).data;
  const labelRef = useRef<InputRef>(null);
  const editing = target !== null;

  const save = useMutation({
    mutationFn: (values: ItemFormValues) =>
      target
        ? updateDictionaryItem(dictionaryCode, target.value, {
            version: target.version,
            label: values.label.trim(),
            description: values.description?.trim() ?? '',
            sortOrder: values.sortOrder,
            status: values.status,
          })
        : createDictionaryItem(dictionaryCode, {
            value: values.value.trim(),
            label: values.label.trim(),
            description: values.description?.trim() ?? '',
            sortOrder: values.sortOrder,
          }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: dictionaryKeys.adminList });
      await queryClient.invalidateQueries({
        queryKey: dictionaryKeys.adminDetail(dictionaryCode),
      });
      await queryClient.invalidateQueries({ queryKey: dictionaryKeys.all });
      message.success(editing ? '已保存字典项' : '已新增字典项');
      onSaved();
    },
    onError: (error) => {
      if (isForbiddenError(error)) {
        onClose();
        onForbidden();
        return;
      }
      if (!(error instanceof ApiError)) return;
      const mapped = toFieldErrors(error.fieldErrors);
      if (error.code === 'DICT_ITEM_VALUE_TAKEN') mapped.fields.value ??= error.message;
      if (error.code === 'DICT_ITEM_VERSION_CONFLICT') {
        void queryClient.invalidateQueries({
          queryKey: dictionaryKeys.adminDetail(dictionaryCode),
        });
      }
      const fields = Object.entries(mapped.fields).map(([name, text]) => ({
        name: name as keyof ItemFormValues,
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
        value: target.value,
        label: target.label,
        description: target.description ?? '',
        sortOrder: target.sortOrder,
        status: target.status === 'ENABLED' ? 'ENABLED' : 'DISABLED',
      });
      return;
    }
    form.setFieldsValue({
      value: '',
      label: '',
      description: '',
      sortOrder: 10,
      status: 'ENABLED',
    });
  }, [open, target, form, resetSave]);

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
      size={480}
      destroyOnHidden
      onClose={onClose}
      focusable={{ focusTriggerAfterClose: false }}
      afterOpenChange={(isOpen) => {
        if (isOpen) labelRef.current?.focus();
        else onClosed();
      }}
      title={
        <div>
          <div className={styles.title}>
            {target ? `编辑字典项 · ${target.label}` : '新增字典项'}
          </div>
          <div className={styles.desc}>
            取值是业务数据里保存的值，创建后不可修改；停用后不再出现在选择项里，历史数据仍显示标签。
          </div>
        </div>
      }
      footer={
        <div className={styles.foot}>
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
      <Form<ItemFormValues>
        form={form}
        layout="vertical"
        onFinish={(values) => save.mutate(values)}
      >
        <Form.Item
          name="value"
          label="取值"
          extra="只能使用字母、数字与 . _ -；创建后不可修改。"
          rules={
            editing
              ? []
              : [
                  { required: true, message: '请输入取值' },
                  {
                    pattern: /^[A-Za-z0-9._-]{1,64}$/,
                    message: '取值只能使用字母、数字与 . _ -，长度 1–64',
                  },
                ]
          }
        >
          <Input
            className={styles.mono}
            disabled={editing}
            placeholder="例如 FISHING"
            autoComplete="off"
          />
        </Form.Item>
        <Form.Item
          name="label"
          label="标签"
          rules={[
            { required: true, message: '请输入标签' },
            { max: 80, message: '标签最多 80 个字符' },
          ]}
        >
          <Input ref={labelRef} placeholder="例如 渔船" autoComplete="off" />
        </Form.Item>
        <Form.Item
          name="description"
          label="说明"
          rules={[{ max: 200, message: '说明最多 200 个字符' }]}
        >
          <Input.TextArea rows={2} maxLength={200} showCount placeholder="可以留空" />
        </Form.Item>
        <Form.Item
          name="sortOrder"
          label="排序"
          extra="同类型内排序，越小越靠前"
          rules={[{ required: true, message: '请填写排序' }]}
        >
          <Input type="number" min={0} />
        </Form.Item>
        {editing && (
          <Form.Item name="status" label="状态">
            <Select options={toDictOptions(statusDictionary)} />
          </Form.Item>
        )}
      </Form>
    </Drawer>
  );
}
