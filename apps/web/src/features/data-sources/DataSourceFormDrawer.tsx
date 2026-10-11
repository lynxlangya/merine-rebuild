import type {
  CheckDataSourceDraft,
  DataSourceCheckResult,
  DataSourceView,
  SaveDataSource,
} from '@merine/api-contract';
import { SafetyCertificateOutlined } from '@ant-design/icons';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Alert, App, Button, Drawer, Form, Input, InputNumber, Select, Space } from 'antd';
import { useRef, useState } from 'react';
import { Link } from 'react-router';
import { errorText } from '../../shared/api-error';
import { ApiError } from '../../shared/http';
import { checkDataSourceDraft, createDataSource, dataSourceKeys, updateDataSource } from './api';
import { formatCheckedAt, outcomeMeta } from './model';
import styles from './DataSourceFormDrawer.module.css';

/**
 * 新增/编辑数据源。表单不碰启停状态：保存一律以「停用」落库，
 * 启用走列表里的「检查并启用」。允许查询的视图范围不在本表单维护，
 * 统一到「智能体管理 → 查询范围」页整表替换，避免两处写入口。
 * 唯一写入型字段是密码——编辑留空表示保持原值。
 */
export function DataSourceFormDrawer({
  target,
  onClose,
}: {
  target: DataSourceView | null;
  onClose: () => void;
}) {
  const { message, modal } = App.useApp();
  const client = useQueryClient();
  const [form] = Form.useForm<SaveDataSource>();
  const [checkResult, setCheckResult] = useState<DataSourceCheckResult | null>(null);
  const submitting = useRef(false);
  const checkMeta = outcomeMeta(checkResult?.outcome);
  const save = useMutation({
    mutationFn: (values: SaveDataSource) =>
      target?.id
        ? updateDataSource(target.id, { ...values, version: target.version })
        : createDataSource(values),
    // 密码只在当前表单短暂驻留，抽屉卸载后立即清理 mutation 变量。
    gcTime: 0,
    onSuccess: () => {
      form.resetFields();
      onClose();
      message.success('数据源已保存；连接参数变化后请重新检查再启用');
      void client.invalidateQueries({ queryKey: dataSourceKeys });
    },
    onError: (error) => {
      if (error instanceof ApiError) {
        const fields = [
          'name',
          'remark',
          'dbType',
          'host',
          'port',
          'databaseName',
          'username',
          'password',
          'version',
        ];
        form.setFields(
          error.fieldErrors
            .filter((item) => fields.includes(item.field))
            .map((item) => ({ name: item.field as keyof SaveDataSource, errors: [item.message] })),
        );
      }
    },
    onSettled: () => {
      submitting.current = false;
    },
  });
  const test = useMutation({
    mutationFn: (input: CheckDataSourceDraft) => checkDataSourceDraft(input),
    onSuccess: (result) => setCheckResult(result),
    onError: (error) => message.error(errorText(error)),
  });
  const runCheck = async () => {
    try {
      await form.validateFields(['host', 'port', 'databaseName', 'username', 'password']);
    } catch {
      return;
    }
    const values = form.getFieldsValue(true);
    test.mutate({
      id: target?.id,
      dbType: values.dbType ?? 'MYSQL',
      host: (values.host ?? '').trim(),
      port: values.port ?? 3306,
      databaseName: (values.databaseName ?? '').trim(),
      username: (values.username ?? '').trim(),
      password: values.password ? values.password : undefined,
    });
  };
  const close = () => {
    if (submitting.current) return;
    if (form.isFieldsTouched())
      modal.confirm({
        title: '放弃未保存的配置？',
        content: '当前修改和输入的密码不会保存。',
        okText: '放弃修改',
        cancelText: '继续编辑',
        onOk: onClose,
      });
    else onClose();
  };
  const required = (name: string) => [
    { required: true, whitespace: true, message: `请填写${name}` },
  ];
  return (
    <Drawer
      open
      title={target ? `编辑数据源 · ${target.name ?? ''}` : '新增数据源'}
      size={720}
      onClose={close}
      maskClosable={!save.isPending}
      keyboard={!save.isPending}
      destroyOnHidden
      footer={
        <div className={styles.footer}>
          <span>保存默认停用；视图范围在「查询范围」页维护，复检并启用后才生效</span>
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
        initialValues={
          target
            ? {
                name: target.name ?? '',
                remark: target.remark ?? '',
                dbType: target.dbType ?? 'MYSQL',
                host: target.host ?? '',
                port: target.port ?? 3306,
                databaseName: target.databaseName ?? '',
                username: target.username ?? '',
                password: '',
              }
            : { dbType: 'MYSQL', port: 3306 }
        }
        onValuesChange={(changed) => {
          // 连接参数变化后，之前的检测结果不再代表当前配置。
          if (
            Object.keys(changed).some((key) =>
              ['host', 'port', 'databaseName', 'username', 'password'].includes(key),
            )
          ) {
            setCheckResult(null);
          }
        }}
        disabled={save.isPending}
        onFinish={(values) => {
          if (submitting.current) return;
          submitting.current = true;
          save.mutate(values);
        }}
      >
        {save.isError && (
          <Alert type="error" showIcon title={errorText(save.error)} className={styles.alert} />
        )}
        <div className={styles.intro}>
          <strong>仅支持 MySQL 的专用只读账号</strong>
          <p>
            数据库账号自身必须只有必要的读取权限；JDBC
            只读设置不是安全边界。测试连接只读取授权元数据， 不会尝试写操作。
          </p>
        </div>
        <h3 className={styles.sectionTitle}>基本信息</h3>
        <Form.Item label="名称" name="name" rules={required('名称')}>
          <Input maxLength={80} placeholder="例如：海防数据只读 · 生产报表库" />
        </Form.Item>
        <Form.Item label="备注" name="remark">
          <Input.TextArea
            rows={2}
            maxLength={300}
            showCount
            placeholder="用途、环境或维护人（可选）"
          />
        </Form.Item>
        <Form.Item label="数据库类型" name="dbType" rules={[{ required: true }]}>
          <Select
            options={[{ value: 'MYSQL', label: 'MySQL' }]}
            disabled
            aria-label="数据库类型固定为 MySQL"
          />
        </Form.Item>
        <h3 className={styles.sectionTitle}>连接与凭据</h3>
        <div className={styles.twoColumns}>
          <Form.Item
            label="主机地址"
            name="host"
            rules={[
              ...required('主机地址'),
              {
                pattern: /^[A-Za-z0-9.:-]+$/,
                message: '只填写域名、IPv4 或 IPv6 字面量，不含协议与路径',
              },
            ]}
            extra="连接前由服务端校验 DNS 解析结果是否在部署允许的网段内。"
          >
            <Input maxLength={253} placeholder="例如：10.20.0.15 或 db.internal" />
          </Form.Item>
          <Form.Item label="端口" name="port" rules={[{ required: true, message: '请填写端口' }]}>
            <InputNumber min={1} max={65535} precision={0} className={styles.port} />
          </Form.Item>
        </div>
        <Form.Item
          label="数据库名"
          name="databaseName"
          rules={[
            ...required('数据库名'),
            {
              pattern: /^[\p{L}\p{N}_$]{1,64}$/u,
              message: '数据库名只能是 1 到 64 位的字母、数字、下划线或 $',
            },
          ]}
        >
          <Input maxLength={64} placeholder="例如：haifang_report" />
        </Form.Item>
        <Form.Item
          label="用户名"
          name="username"
          rules={required('用户名')}
          extra="请使用只授予视图 SELECT 的专用账号；有写权限或持有角色的账号无法通过检查。"
        >
          <Input maxLength={64} autoComplete="off" placeholder="只读数据库账号" />
        </Form.Item>
        <Form.Item
          label="密码"
          name="password"
          rules={target ? [] : [{ required: true, whitespace: true, message: '请填写数据库密码' }]}
          extra={
            target
              ? '已保存密码不回显；留空保留原值，只改名称或备注时不会让检查结果失效。'
              : '仅提交到本项目后端加密保存，不写入浏览器本地存储。'
          }
        >
          <Input.Password
            maxLength={128}
            autoComplete="new-password"
            placeholder={target ? '已配置 · 输入新密码以替换' : '填写数据库密码'}
          />
        </Form.Item>
        <h3 className={styles.sectionTitle}>允许查询的视图</h3>
        <p className={styles.hint}>
          当前已选 {target?.views?.length ?? 0} 个视图。范围与说明统一在{' '}
          <Link to="/agent/query-scopes">智能体管理 → 查询范围</Link> 维护：先测试连接并检查授权，
          再从服务端检测到的、账号具备 SELECT
          的视图中勾选；勾选只约束本平台，不会创建或修改数据库授权。
        </p>
        <div className={styles.checkRow}>
          <Button
            icon={<SafetyCertificateOutlined />}
            loading={test.isPending}
            onClick={() => void runCheck()}
          >
            测试连接与检查授权
          </Button>
          {target && <span className={styles.hint}>编辑态密码留空时，用已保存的密码发起检查</span>}
        </div>
        {checkResult && (
          <Alert
            className={styles.alert}
            type={checkResult.outcome === 'PASSED' ? 'success' : 'warning'}
            showIcon
            title={`${checkMeta?.label ?? '检查结论'} · ${formatCheckedAt(checkResult.checkedAt)}`}
            description={`${checkResult.message ?? ''}；检测到 ${checkResult.detectedViews?.length ?? 0} 个可读视图`}
          />
        )}
      </Form>
    </Drawer>
  );
}
