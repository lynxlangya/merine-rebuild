import type { DictionaryListItem, DictionaryView } from '@merine/api-contract';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Alert, App, Button, Drawer, Form, Input, Select } from 'antd';
import type { InputRef } from 'antd';
import { useEffect, useRef } from 'react';
import { errorText, isForbiddenError } from '../../../shared/api-error';
import { ApiError } from '../../../shared/http';
import { createDictionary, updateDictionary } from '../api';
import { DICTIONARY_CODES, toDictOptions, useDictionary } from '../public';
import { dictionaryKeys } from '../queries';
import styles from './DictionaryDrawer.module.css';

interface TypeFormValues {
  code: string;
  name: string;
  description?: string;
  status: 'ENABLED' | 'DISABLED';
}

const FORM_FIELDS = ['code', 'name', 'description', 'status'];

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
 * 字典类型抽屉：新建与编辑共用；编码创建后不可修改。
 * 类型只停用不删除——取值是业务数据里的稳定标识，删掉整组等于让历史数据失去归属。
 */
export function DictionaryTypeDrawer({
  open,
  target,
  onClose,
  onClosed,
  onSaved,
  onForbidden,
}: {
  open: boolean;
  /** null 表示新建 */
  target: DictionaryListItem | null;
  onClose: () => void;
  onClosed: () => void;
  onSaved: (saved: DictionaryView) => void;
  onForbidden: () => void;
}) {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [form] = Form.useForm<TypeFormValues>();
  const statusDictionary = useDictionary(DICTIONARY_CODES.status).data;
  const nameRef = useRef<InputRef>(null);
  const editing = target !== null;

  const save = useMutation({
    mutationFn: (values: TypeFormValues) =>
      target
        ? updateDictionary(target.code, {
            version: target.version,
            name: values.name.trim(),
            description: values.description?.trim() ?? '',
            status: values.status,
          })
        : createDictionary({
            code: values.code.trim(),
            name: values.name.trim(),
            description: values.description?.trim() ?? '',
          }),
    onSuccess: async (saved) => {
      await queryClient.invalidateQueries({ queryKey: dictionaryKeys.adminList });
      // 详情面板用的是独立的 detail key：这里不失效，改完名字右侧还显示旧名字
      await queryClient.invalidateQueries({ queryKey: dictionaryKeys.adminDetail(saved.code) });
      await queryClient.invalidateQueries({ queryKey: dictionaryKeys.all });
      message.success(editing ? `已保存字典 ${saved.name}` : `已创建字典 ${saved.name}`);
      onSaved(saved);
    },
    onError: (error) => {
      if (isForbiddenError(error)) {
        onClose();
        onForbidden();
        return;
      }
      if (!(error instanceof ApiError)) return;
      const mapped = toFieldErrors(error.fieldErrors);
      if (error.code === 'DICT_CODE_TAKEN') mapped.fields.code ??= error.message;
      if (error.code === 'DICT_VERSION_CONFLICT') {
        void queryClient.invalidateQueries({ queryKey: dictionaryKeys.adminList });
      }
      const fields = Object.entries(mapped.fields).map(([name, text]) => ({
        name: name as keyof TypeFormValues,
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
        code: target.code,
        name: target.name,
        description: target.description ?? '',
        status: target.status === 'ENABLED' ? 'ENABLED' : 'DISABLED',
      });
      return;
    }
    form.setFieldsValue({ code: '', name: '', description: '', status: 'ENABLED' });
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
        if (isOpen) nameRef.current?.focus();
        else onClosed();
      }}
      title={
        <div>
          <div className={styles.title}>{target ? `编辑字典 · ${target.name}` : '新建字典'}</div>
          <div className={styles.desc}>
            字典编码是稳定标识，创建后不可修改；字典只停用、不删除。
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
      <Form<TypeFormValues>
        form={form}
        layout="vertical"
        onFinish={(values) => save.mutate(values)}
      >
        <Form.Item
          name="code"
          label="字典编码"
          extra="形如 vessel.type；只能使用小写字母、数字与 . _ -；编码创建后不可修改。"
          rules={
            editing
              ? []
              : [
                  { required: true, message: '请输入字典编码' },
                  {
                    pattern: /^[a-z][a-z0-9._-]{2,63}$/,
                    message: '字典编码只能使用小写字母、数字与 . _ -，长度 3–64',
                  },
                ]
          }
        >
          <Input
            className={styles.mono}
            disabled={editing}
            placeholder="例如 vessel.type"
            autoComplete="off"
          />
        </Form.Item>
        <Form.Item
          name="name"
          label="字典名称"
          rules={[
            { required: true, message: '请输入字典名称' },
            { max: 80, message: '字典名称最多 80 个字符' },
          ]}
        >
          <Input ref={nameRef} placeholder="例如 船舶类型" autoComplete="off" />
        </Form.Item>
        <Form.Item
          name="description"
          label="说明"
          rules={[{ max: 200, message: '说明最多 200 个字符' }]}
        >
          <Input.TextArea rows={2} maxLength={200} showCount placeholder="可以留空" />
        </Form.Item>
        {editing && (
          <Form.Item
            name="status"
            label="状态"
            extra="停用后使用侧拿不到这本字典的选项，历史数据不受影响"
          >
            <Select options={toDictOptions(statusDictionary)} />
          </Form.Item>
        )}
      </Form>
    </Drawer>
  );
}
