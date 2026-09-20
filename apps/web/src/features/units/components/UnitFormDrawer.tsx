import type { UnitTreeNode, UnitView } from '@merine/api-contract';
import { Alert, Button, Drawer, Form, Input, TreeSelect } from 'antd';
import type { InputRef, TreeSelectProps } from 'antd';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { App } from 'antd';
import { useEffect, useMemo, useRef } from 'react';
import { ApiError } from '../../../shared/http';
import { errorText, isForbiddenError } from '../../../shared/api-error';
import { createUnit, updateUnit } from '../api';
import { DICTIONARY_CODES, useDictionary } from '../../dictionaries/public';
import {
  buildParentSelectTree,
  findUnitTreeNode,
  unitLevelLabel,
  toUnitFormFieldErrors,
} from '../model';
import { unitKeys } from '../queries';
import styles from './UnitFormDrawer.module.css';

interface UnitFormValues {
  code: string;
  name: string;
  parentCode?: string;
  areaCode: string;
}

const UNIT_CODE_PATTERN = /^[A-Za-z0-9._-]{1,64}$/;
const AREA_CODE_PATTERN = /^[0-9]{2,12}$/;

export function UnitFormDrawer({
  open,
  target,
  parentTree,
  defaultParentCode,
  parentLoading,
  onClose,
  onSaved,
  onForbidden,
}: {
  open: boolean;
  target: UnitTreeNode | null;
  parentTree: UnitTreeNode[];
  defaultParentCode?: string;
  parentLoading: boolean;
  onClose: () => void;
  onSaved: (saved: UnitView) => void;
  onForbidden: () => void;
}) {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const levelDictionary = useDictionary(DICTIONARY_CODES.unitLevel).data;
  const [form] = Form.useForm<UnitFormValues>();
  const codeRef = useRef<InputRef>(null);
  const nameRef = useRef<InputRef>(null);
  const editing = target !== null;
  const targetCode = target?.code ?? null;

  const parentCode = Form.useWatch('parentCode', form);
  const derivedLevel = useMemo(() => {
    if (!parentCode) return 1;
    const parent = findUnitTreeNode(parentTree, parentCode);
    return parent ? parent.level + 1 : null;
  }, [parentCode, parentTree]);
  const parentTreeData = useMemo(
    () => buildParentSelectTree(parentTree, targetCode ?? undefined),
    [parentTree, targetCode],
  );

  const initialValues: UnitFormValues = target
    ? {
        code: target.code,
        name: target.name,
        parentCode: target.parentCode ?? undefined,
        areaCode: target.areaCode ?? '',
      }
    : {
        code: '',
        name: '',
        parentCode: defaultParentCode,
        areaCode: '',
      };

  const save = useMutation({
    mutationFn: (values: UnitFormValues) =>
      target
        ? updateUnit(target.code, {
            version: target.version,
            name: values.name.trim(),
            parentCode: values.parentCode?.trim() || null,
            areaCode: values.areaCode.trim(),
          })
        : createUnit({
            code: values.code.trim(),
            name: values.name.trim(),
            parentCode: values.parentCode?.trim() || null,
            areaCode: values.areaCode.trim(),
          }),
    onSuccess: async (saved) => {
      await queryClient.invalidateQueries({ queryKey: unitKeys.all });
      message.success(target ? `已保存单位 ${saved.name}` : `已创建单位 ${saved.name}`);
      onSaved(saved);
    },
    onError: (error) => {
      if (isForbiddenError(error)) {
        onClose();
        onForbidden();
        return;
      }
      if (!(error instanceof ApiError)) return;
      if (error.code === 'UNIT_VERSION_CONFLICT') {
        void queryClient.invalidateQueries({ queryKey: unitKeys.all });
      }
      const mapped = toUnitFormFieldErrors(error.fieldErrors);
      if (error.code === 'UNIT_CODE_TAKEN') mapped.fields.code ??= error.message;
      const fields = Object.entries(mapped.fields).map(([name, messageText]) => ({
        name: name as keyof UnitFormValues,
        errors: [messageText],
      }));
      if (fields.length > 0) {
        form.setFields(fields);
        const first = fields[0].name;
        if (first === 'code') codeRef.current?.focus();
        else if (first === 'name') nameRef.current?.focus();
      }
    },
  });

  const resetSave = save.reset;
  useEffect(() => {
    if (!open) return;
    resetSave();
    form.resetFields();
    form.setFieldsValue(initialValues);
  }, [open, targetCode, defaultParentCode, form, resetSave]); // eslint-disable-line react-hooks/exhaustive-deps

  const failureText = (() => {
    if (!save.isError || isForbiddenError(save.error)) return null;
    const base = errorText(save.error);
    if (!(save.error instanceof ApiError) || save.error.fieldErrors.length === 0) return base;
    const { rest } = toUnitFormFieldErrors(save.error.fieldErrors);
    return rest.length > 0 ? `${base}；${rest.join('；')}` : base;
  })();

  return (
    <Drawer
      open={open}
      size={520}
      destroyOnHidden
      onClose={onClose}
      title={
        <div>
          <div className={styles.title}>{editing ? `编辑单位 · ${target.name}` : '新增单位'}</div>
          <div className={styles.desc}>
            层级由上级单位自动确定，最多三级；单位编码创建后不可修改。
          </div>
        </div>
      }
      footer={
        <div className={styles.foot}>
          <span className={styles.footNote}>保存后立即影响用户所属单位与组织树。</span>
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
      <Form<UnitFormValues>
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
            title={editing ? '保存失败' : '创建失败'}
            description={failureText}
          />
        )}

        <Form.Item
          name="code"
          label="单位编码"
          extra={editing ? '单位编码创建后不可修改。' : '字母、数字与 . _ -，长度 1–64。'}
          rules={
            editing
              ? []
              : [
                  { required: true, whitespace: true, message: '请输入单位编码' },
                  { pattern: UNIT_CODE_PATTERN, message: '单位编码格式不正确' },
                ]
          }
        >
          <Input
            ref={codeRef}
            className={styles.mono}
            disabled={editing}
            autoComplete="off"
            spellCheck={false}
            placeholder="例如 ORG_012"
          />
        </Form.Item>

        <Form.Item
          name="name"
          label="单位名称"
          rules={[
            { required: true, whitespace: true, message: '请输入单位名称' },
            { max: 80, message: '单位名称最多 80 个字符' },
          ]}
        >
          <Input ref={nameRef} autoComplete="off" placeholder="例如 某市公安局海防管理支队" />
        </Form.Item>

        <Form.Item
          name="parentCode"
          label="上级单位"
          extra={
            derivedLevel
              ? `保存后层级：${unitLevelLabel(derivedLevel, levelDictionary)}。`
              : '系统只允许一个一级单位；没有上级时保存会由后端校验。'
          }
        >
          <TreeSelect<string>
            treeData={parentTreeData as TreeSelectProps<string>['treeData']}
            treeNodeFilterProp="searchText"
            showSearch
            allowClear
            loading={parentLoading}
            treeDefaultExpandAll
            placeholder="无上级（一级单位）"
            notFoundContent={parentLoading ? '加载中' : '没有可选上级'}
          />
        </Form.Item>

        <Form.Item
          name="areaCode"
          label="行政区划代码"
          extra="2–12 位数字，来自组织参考数据，不参与单位关联。"
          rules={[
            { required: true, whitespace: true, message: '请输入行政区划代码' },
            { pattern: AREA_CODE_PATTERN, message: '行政区划代码只能是 2–12 位数字' },
          ]}
        >
          <Input className={styles.mono} autoComplete="off" placeholder="例如 3301" />
        </Form.Item>
      </Form>
    </Drawer>
  );
}
