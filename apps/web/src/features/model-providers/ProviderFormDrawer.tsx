import type {
  DiscoveredProviderModel,
  ModelProvider,
  SaveModelProvider,
} from '@merine/api-contract';
import {
  CloudDownloadOutlined,
  DeleteOutlined,
  PlusOutlined,
  QuestionCircleOutlined,
} from '@ant-design/icons';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Alert, App, Button, Drawer, Form, Input, Select, Space, Tag, Tooltip } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { ApiError } from '../../shared/http';
import { errorText } from '../../shared/api-error';
import { DICTIONARY_CODES, useDictionary, toDictOptions } from '../dictionaries/public';
import { discoverProviderModels, saveProvider, providerKeys } from './api';
import { modelOptionKeys, useEffortCatalogQuery } from './queries';
import { DiscoverModelsModal } from './DiscoverModelsModal';
import { reasoningEffortOptions } from './reasoningEfforts';
import { ProviderIcon } from './ProviderIcon';
import { initialProvider, providerTemplates, templateFor } from './templates';
import styles from './ProviderFormDrawer.module.css';

/** 去抖：模型标识是逐个字符输入的，没必要每敲一下就查一次目录。 */
function useDebounced<T>(value: T, delay: number): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(timer);
  }, [value, delay]);
  return debounced;
}

export function ProviderFormDrawer({
  target,
  vendor,
  onClose,
}: {
  target: ModelProvider | null;
  vendor: ModelProvider['vendor'];
  onClose: () => void;
}) {
  const { message, modal } = App.useApp();
  const client = useQueryClient();
  const [form] = Form.useForm<SaveModelProvider>();
  const [catalog, setCatalog] = useState<DiscoveredProviderModel[] | null>(null);
  const submitting = useRef(false);
  const dict = useDictionary(DICTIONARY_CODES.status).data;
  const selectedVendor = Form.useWatch('vendor', form) ?? vendor;
  const template = templateFor(selectedVendor);
  const baseUrl = Form.useWatch('baseUrl', form) ?? target?.baseUrl ?? templateFor(vendor).baseUrl;
  const modelRows = Form.useWatch('models', form) as SaveModelProvider['models'] | undefined;
  const watchedIds = (modelRows ?? []).map((row) => (row?.modelId ?? '').trim()).filter(Boolean);
  const debouncedIds = useDebounced(watchedIds.join('\n'), 400);
  const queriedIds = debouncedIds ? debouncedIds.split('\n') : [];
  const effortCatalog = useEffortCatalogQuery(selectedVendor, queriedIds);
  const baseUrlValue = Form.useWatch('baseUrl', form) ?? target?.baseUrl;
  const apiKeyValue = Form.useWatch('apiKey', form);
  const canDiscoverExchange = Boolean(baseUrlValue && (apiKeyValue || target));
  const save = useMutation({
    // 密钥只随当前表单短暂驻留，抽屉卸载后立即清理 mutation 变量。
    gcTime: 0,
    mutationFn: (values: SaveModelProvider) =>
      saveProvider(target, normalizeEffortsForSave(values)),
    onSuccess: () => {
      form.resetFields();
      onClose();
      message.success('模型连接已保存');
      void client.invalidateQueries({ queryKey: providerKeys });
      void client.invalidateQueries({ queryKey: modelOptionKeys.all });
    },
    onError: (error) => {
      if (error instanceof ApiError)
        form.setFields(
          error.fieldErrors
            .filter((e) =>
              [
                'vendor',
                'name',
                'remark',
                'website',
                'baseUrl',
                'apiKey',
                'status',
                'models',
                'version',
              ].includes(e.field),
            )
            .map((e) => ({ name: e.field as keyof SaveModelProvider, errors: [e.message] })),
        );
    },
    onSettled: () => {
      submitting.current = false;
    },
  });
  // 只有地址和密钥都齐了才值得发请求：新连接必须先填密钥，已保存的连接可以用存好的那份。
  const discover = useMutation({
    mutationFn: discoverProviderModels,
    onSuccess: (data) => setCatalog(data.models),
    onError: (error) => {
      if (error instanceof ApiError) {
        form.setFields(
          error.fieldErrors
            .filter((e) => e.field === 'baseUrl' || e.field === 'apiKey')
            .map((e) => ({ name: e.field as keyof SaveModelProvider, errors: [e.message] })),
        );
        message.error(error.message);
      } else message.error('获取模型列表失败，请稍后重试');
    },
  });
  const close = () => {
    if (submitting.current) return;
    if (form.isFieldsTouched())
      modal.confirm({
        title: '放弃未保存的配置？',
        content: '当前修改和输入的密钥不会保存。',
        okText: '放弃修改',
        cancelText: '继续编辑',
        onOk: onClose,
      });
    else onClose();
  };
  const applyTemplate = (next: ModelProvider['vendor']) => {
    const apply = () => {
      form.setFieldsValue({ ...initialProvider(next), remark: form.getFieldValue('remark') ?? '' });
      save.reset();
    };
    if (form.isFieldsTouched(['name', 'baseUrl', 'website', 'apiKey']))
      modal.confirm({
        title: '更换供应商模板？',
        content: '名称、图标和地址将使用所选模板，已输入的密钥与已配置的模型都会清空。备注保留。',
        okText: '更换',
        cancelText: '取消',
        onOk: apply,
        onCancel: () => form.setFieldValue('vendor', selectedVendor),
      });
    else apply();
  };
  /**
   * 该模型可以勾选的档位：
   * 官方目录覆盖的供应商只给目录里的取值（没收录就是不使用档位参数）；
   * 尚未整理目录的供应商保留全部档位，由管理员自行决定。
   */
  const effortChoices = (modelId: string) => {
    const catalog = effortCatalog.data;
    if (!catalog || !catalog.covered) return reasoningEffortOptions;
    const matched = catalog.models.find((entry) => entry.modelId === modelId.trim());
    const allowed = new Set(matched?.reasoningEfforts ?? []);
    return reasoningEffortOptions.filter((option) => allowed.has(option.value));
  };
  const effortNote = (modelId: string) => {
    const catalog = effortCatalog.data;
    if (!catalog) return null;
    if (!catalog.covered) return '未整理该供应商的官方取值，可自行勾选';
    return effortChoices(modelId).length === 0 ? '官方未收录该模型的档位，将不发送强度参数' : null;
  };
  /** 保存前按目录收口：目录覆盖的供应商只留官方取值，避免历史数据里留着不支持的档位。 */
  const normalizeEffortsForSave = (values: SaveModelProvider): SaveModelProvider => {
    const catalog = effortCatalog.data;
    if (!catalog?.covered) return values;
    return {
      ...values,
      models: (values.models ?? []).map((model) => {
        const allowed = new Set(effortChoices(model.modelId).map((option) => option.value));
        return {
          ...model,
          reasoningEfforts: (model.reasoningEfforts ?? []).filter((effort) => allowed.has(effort)),
        };
      }),
    };
  };
  const required = (name: string) => [
    { required: true, whitespace: true, message: `请填写${name}` },
  ];
  return (
    <Drawer
      open
      title={target ? `编辑连接 · ${target.name}` : '添加模型连接'}
      size={680}
      onClose={close}
      maskClosable={!save.isPending}
      keyboard={!save.isPending}
      destroyOnHidden
      footer={
        <div className={styles.footer}>
          <span>保存后同步更新聊天模型选项</span>
          <Space>
            <Button onClick={close} disabled={save.isPending}>
              取消
            </Button>
            <Button type="primary" loading={save.isPending} onClick={() => form.submit()}>
              保存配置
            </Button>
          </Space>
        </div>
      }
    >
      <Form
        form={form}
        layout="vertical"
        scrollToFirstError={{ focus: true }}
        initialValues={target ? { ...target, apiKey: '' } : initialProvider(vendor)}
        disabled={save.isPending}
        onFinish={(values) => {
          if (submitting.current) return;
          const kept = values.models?.length ?? 0;
          const had = target?.models.length ?? 0;
          // 保存会让既有模型全部消失时先确认：连接被清空后，输入区会立刻少掉这些模型。
          if (had > 0 && kept === 0) {
            modal.confirm({
              title: '保存后该连接下不再有模型？',
              content: `当前配置有 ${had} 个模型，保存后会全部删除，助手输入区里也不再出现。`,
              okText: '仍要保存',
              cancelText: '继续编辑',
              onOk: () => {
                submitting.current = true;
                save.mutate(values);
              },
            });
            return;
          }
          submitting.current = true;
          save.mutate(values);
        }}
      >
        {save.isError && (
          <Alert type="error" showIcon title={errorText(save.error)} className={styles.alert} />
        )}
        <div className={styles.formIntro}>
          <ProviderIcon
            vendor={selectedVendor}
            name={form.getFieldValue('name') || template.name}
          />
          <div>
            <strong>{template.name}</strong>
            <p>OpenAI Chat Completions 兼容配置</p>
          </div>
        </div>
        <Form.Item label="供应商模板" name="vendor" rules={[{ required: true }]}>
          <Select
            options={providerTemplates.map((t) => ({ value: t.vendor, label: t.name }))}
            onChange={applyTemplate}
          />
        </Form.Item>
        <h3 className={styles.sectionTitle}>基本信息</h3>
        <div className={styles.nameRow}>
          <Form.Item label="名称" name="name" rules={required('名称')}>
            <Input maxLength={80} placeholder="例如：DeepSeek · 研发账号" />
          </Form.Item>
        </div>
        <Form.Item label="备注" name="remark" rules={required('备注')}>
          <Input.TextArea
            rows={2}
            maxLength={300}
            showCount
            placeholder="说明这个配置的用途、账号或所属环境"
          />
        </Form.Item>
        <Form.Item
          label="官网链接"
          name="website"
          rules={[...required('官网链接'), { type: 'url', message: '请填写完整 HTTPS 链接' }]}
        >
          <Input maxLength={500} placeholder="https://" />
        </Form.Item>
        <h3 className={styles.sectionTitle}>连接与凭据</h3>
        <p className={styles.hint}>
          {template.note}{' '}
          {template.docs && (
            <a href={template.docs} target="_blank" rel="noopener noreferrer">
              查看官方文档 ↗
            </a>
          )}
        </p>
        <Form.Item
          label="请求地址（Base URL）"
          name="baseUrl"
          rules={[...required('请求地址'), { type: 'url', message: '请填写完整 HTTPS 地址' }]}
          extra="填写基础地址，不含 /chat/completions；仅支持 HTTPS。"
        >
          <Input maxLength={500} placeholder="https://api.example.com/v1" />
        </Form.Item>
        {baseUrl && (
          <div className={styles.endpoint}>
            <span>接口路径预览</span>
            <code>{baseUrl.replace(/\/+$/, '')}/chat/completions</code>
          </div>
        )}
        <Form.Item
          label="API Key"
          name="apiKey"
          rules={
            !target ||
            selectedVendor !== target.vendor ||
            baseUrl.trim().replace(/\/+$/, '') !== target.baseUrl
              ? required('API Key')
              : []
          }
          extra={
            target
              ? '已保存密钥不回显；留空保留原值，更换供应商或请求地址时须重新输入。'
              : '仅提交到本项目后端加密保存，不写入浏览器本地存储。'
          }
        >
          <Input.Password
            maxLength={4096}
            autoComplete="new-password"
            placeholder={target ? '已配置 · 输入新密钥以替换' : '填写供应商提供的 API Key'}
          />
        </Form.Item>
        <Form.Item
          label="配置状态"
          name="status"
          rules={[{ required: true }]}
          extra="启用连接中的启用模型可在聊天页选择；启用不代表已验证连通。"
        >
          <Select options={toDictOptions(dict)} />
        </Form.Item>
        <div className={styles.sectionHead}>
          <h3 className={styles.sectionTitle}>
            可用模型{' '}
            <span className={styles.modelCount}>
              {modelRows?.length ?? target?.models.length ?? 0} / 20
            </span>
          </h3>
          <Tooltip
            title={
              canDiscoverExchange
                ? '按当前请求地址与 API Key 读取上游模型清单'
                : '先填写请求地址；新连接还需填 API Key，已保存的连接可直接读取'
            }
          >
            <Button
              size="small"
              icon={<CloudDownloadOutlined />}
              loading={discover.isPending}
              disabled={!canDiscoverExchange}
              onClick={() =>
                discover.mutate({
                  providerId: target?.id,
                  vendor: selectedVendor,
                  baseUrl: String(baseUrlValue ?? ''),
                  apiKey: apiKeyValue ?? '',
                })
              }
            >
              获取模型列表
            </Button>
          </Tooltip>
        </div>
        <p className={styles.hint}>
          真实对话按选中的模型调用；模型标识是供应商接口的 model 字段（如 deepseek-flash）。
          可以手动添加，也可以配好密钥后用「获取模型列表」从上游拉取。
        </p>
        <Form.List name="models">
          {(fields, { add, remove }) => (
            <div className={styles.modelList}>
              {fields.map((field) => (
                <div className={styles.modelCard} key={field.key}>
                  <div className={styles.modelCardHead}>
                    <strong>模型 {field.name + 1}</strong>
                    <Button
                      type="text"
                      size="small"
                      danger
                      icon={<DeleteOutlined />}
                      aria-label={`删除模型 ${modelRows?.[field.name]?.modelId || field.name + 1}`}
                      disabled={save.isPending}
                      onClick={() => remove(field.name)}
                    />
                  </div>
                  <Form.Item
                    label="模型标识"
                    name={[field.name, 'modelId']}
                    rules={[
                      { required: true, whitespace: true, message: '请填写模型标识' },
                      {
                        pattern: /^[A-Za-z0-9._:/-]+$/,
                        message: '只能包含字母、数字与 . _ : / -',
                      },
                    ]}
                  >
                    <Input maxLength={120} placeholder="模型标识，如 deepseek-flash" />
                  </Form.Item>
                  <Form.Item label="显示名称" name={[field.name, 'displayName']}>
                    <Input maxLength={80} placeholder="显示名（可空）" />
                  </Form.Item>
                  <Form.Item label="状态" name={[field.name, 'status']} initialValue="ENABLED">
                    <Select options={toDictOptions(dict)} aria-label="模型状态" />
                  </Form.Item>
                  <div className={styles.modelEfforts}>
                    <span className={styles.effortLabel}>
                      推理强度
                      <Tooltip title="只列该模型官方支持的档位；全部取消则该模型不显示强度选择（默认档按官方默认值）">
                        <QuestionCircleOutlined className={styles.effortHelp} />
                      </Tooltip>
                    </span>
                    <Form.Item name={[field.name, 'reasoningEfforts']} noStyle>
                      <Tag.CheckableTagGroup
                        className={styles.effortTags}
                        multiple
                        aria-label="可用推理强度"
                        options={effortChoices(
                          (modelRows?.[field.name]?.modelId as string | undefined) ?? '',
                        )}
                      />
                    </Form.Item>
                    {effortNote((modelRows?.[field.name]?.modelId as string | undefined) ?? '') && (
                      <span className={styles.effortNote}>
                        {effortNote((modelRows?.[field.name]?.modelId as string | undefined) ?? '')}
                      </span>
                    )}
                  </div>
                  <Form.Item
                    label="备注"
                    name={[field.name, 'remark']}
                    className={styles.modelRemark}
                  >
                    <Input maxLength={300} placeholder="备注（可空）" />
                  </Form.Item>
                </div>
              ))}
              <Button
                type="dashed"
                block
                icon={<PlusOutlined />}
                disabled={save.isPending || fields.length >= 20}
                onClick={() =>
                  add({
                    modelId: '',
                    displayName: '',
                    remark: '',
                    reasoningEfforts: reasoningEffortOptions.map((option) => option.value),
                    status: 'ENABLED',
                  })
                }
              >
                添加模型
              </Button>
              {catalog && (
                <DiscoverModelsModal
                  found={catalog}
                  existing={form.getFieldValue('models') ?? []}
                  limit={20 - fields.length}
                  onCancel={() => setCatalog(null)}
                  onImport={(models) => {
                    models.forEach((model) => add(model));
                    setCatalog(null);
                    message.success(
                      models.length ? `已添加 ${models.length} 个模型` : '没有可添加的模型',
                    );
                  }}
                />
              )}
            </div>
          )}
        </Form.List>
      </Form>
    </Drawer>
  );
}
