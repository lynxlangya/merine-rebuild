import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import type { CreateProbe, ProbeRecord } from '@merine/api-contract';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, App, Button, Card, Form, Input, Result, Table, Tag, Typography } from 'antd';
import { ApiError } from '../../shared/http';
import { PERMISSIONS, hasPermission } from '../../shared/permissions';
import { PageHeader } from '../../shared/ui/PageHeader';
import { useAuth } from '../auth/public';
import { createProbe, diagnosticsQueryKey, getDiagnostics } from './api';

/**
 * 开发诊断入口：保留 M1.1 的 bootstrap 端到端联调能力，不再占据产品首页。
 * 这里的数据来自真实后端与数据库，不是合成演示数据。
 */
export function DiagnosticsPage() {
  const { message } = App.useApp();
  const { state } = useAuth();
  const me = state.status === 'authenticated' ? state.user : null;
  const canRead = hasPermission(me?.permissionCodes, PERMISSIONS.diagnosticsRead);
  const canWrite = hasPermission(me?.permissionCodes, PERMISSIONS.diagnosticsWrite);
  const [form] = Form.useForm<CreateProbe>();
  const queryClient = useQueryClient();
  const status = useQuery({
    queryKey: diagnosticsQueryKey,
    queryFn: ({ signal }) => getDiagnostics(signal),
  });
  const create = useMutation({
    mutationFn: createProbe,
    onSuccess: async () => {
      form.resetFields();
      await queryClient.invalidateQueries({ queryKey: diagnosticsQueryKey });
      message.success('记录已写入数据库');
    },
  });
  const connected = status.isSuccess;

  if (!canRead) {
    return (
      <div>
        <PageHeader
          demo={false}
          title="工程诊断"
          description="页面 → API → MySQL 的最小读写链路与探针记录。用于本地开发验证，不是业务功能。"
        />
        <div style={{ padding: '0 var(--sp-4) var(--sp-4)' }}>
          <Card>
            <Result
              status="403"
              title="没有查看工程诊断的权限"
              subTitle="工程诊断是开发工具；需要时可在「菜单管理 → 开发工具」里把它的权限分配给其它角色。"
            />
          </Card>
        </div>
      </div>
    );
  }

  return (
    <div>
      <PageHeader
        demo={false}
        title="工程诊断"
        description="页面 → API → MySQL 的最小读写链路与探针记录。用于本地开发验证，不是业务功能。"
        actions={
          <Button
            icon={<ReloadOutlined />}
            loading={status.isFetching}
            onClick={() => void status.refetch()}
          >
            刷新状态
          </Button>
        }
      />

      <div style={{ padding: '0 var(--sp-4) var(--sp-4)' }}>
        {status.isError && (
          <Alert
            type="error"
            showIcon
            style={{ marginBottom: 'var(--sp-3)' }}
            title="暂时无法连接后端或数据库"
            description={status.error.message}
            action={<Button onClick={() => void status.refetch()}>重新连接</Button>}
          />
        )}

        <Card
          title="端到端联调"
          extra={
            <Tag color={connected ? 'success' : status.isError ? 'error' : 'default'}>
              {status.isError ? '连接失败' : connected ? '已连通' : '连接中'}
            </Tag>
          }
          style={{ marginBottom: 'var(--sp-3)' }}
        >
          <Form<CreateProbe> form={form} layout="inline" onFinish={(input) => create.mutate(input)}>
            <Form.Item
              name="note"
              label="联调记录"
              rules={[
                { required: true, whitespace: true, message: '请填写联调记录' },
                { max: 120, message: '最多 120 个字符' },
              ]}
            >
              <Input
                style={{ width: 320 }}
                maxLength={120}
                disabled={!connected || create.isPending || !canWrite}
                placeholder="例如：我的第一次前后端数据库联调"
              />
            </Form.Item>
            <Button
              type="primary"
              htmlType="submit"
              icon={<PlusOutlined />}
              loading={create.isPending}
              disabled={!connected || !canWrite}
              title={canWrite ? undefined : '需要「工程诊断 · 写入」权限'}
            >
              写入测试记录
            </Button>
          </Form>
          {create.isError && (
            <Alert
              style={{ marginTop: 'var(--sp-3)' }}
              type="error"
              showIcon
              title={create.error.message}
              description={
                create.error instanceof ApiError && create.error.requestId
                  ? `请求编号：${create.error.requestId}`
                  : undefined
              }
            />
          )}
        </Card>

        <Card
          title="最近记录"
          extra={
            <Typography.Text type="secondary">
              共 {status.data?.totalProbes ?? '—'} 条 · 展示最近 5 条 · 当前数据库：
              {status.data?.database ?? '待连接'}
            </Typography.Text>
          }
        >
          <Table<ProbeRecord>
            rowKey="id"
            size="small"
            loading={status.isPending}
            dataSource={status.data?.recentProbes ?? []}
            pagination={false}
            locale={{ emptyText: '暂无联调记录' }}
            scroll={{ x: 560 }}
            columns={[
              { title: '记录', dataIndex: 'note', key: 'note' },
              {
                title: '写入时间',
                dataIndex: 'createdAt',
                key: 'createdAt',
                width: 230,
                render: (value: string) =>
                  new Date(value).toLocaleString('zh-CN', { hour12: false }),
              },
            ]}
          />
        </Card>
      </div>
    </div>
  );
}
