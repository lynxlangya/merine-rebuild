import {
  ApiOutlined,
  DatabaseOutlined,
  DesktopOutlined,
  PlusOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import type { CreateProbe, ProbeRecord } from '@merine/api-contract';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, App, Button, Card, Form, Input, Space, Table, Tag, Typography } from 'antd';
import { ApiError } from '../../shared/http';
import { bootstrapQueryKey, createProbe, getBootstrap } from './api';

const { Title, Text, Paragraph } = Typography;

export function BootstrapPage() {
  const { message } = App.useApp();
  const [form] = Form.useForm<CreateProbe>();
  const queryClient = useQueryClient();
  const status = useQuery({
    queryKey: bootstrapQueryKey,
    queryFn: ({ signal }) => getBootstrap(signal),
  });
  const create = useMutation({
    mutationFn: createProbe,
    onSuccess: async () => {
      form.resetFields();
      await queryClient.invalidateQueries({ queryKey: bootstrapQueryKey });
      message.success('记录已写入数据库');
    },
  });
  const connected = status.isSuccess && !status.isError;
  const connectionLabel = status.isError ? '连接失败' : connected ? '已连通' : '连接中';

  return (
    <main className="workspace">
      <div className="page-heading">
        <div>
          <Text className="eyebrow">FOUNDATION / M1.1</Text>
          <Title level={1}>工程工作台</Title>
          <Paragraph type="secondary">从页面到数据库，让第一条请求链路真正跑通。</Paragraph>
        </div>
        <Button
          icon={<ReloadOutlined />}
          loading={status.isFetching}
          onClick={() => void status.refetch()}
        >
          刷新状态
        </Button>
      </div>

      <div className="service-grid">
        {[
          {
            icon: <DesktopOutlined />,
            name: '前端',
            tech: 'React · TypeScript · Ant Design',
            state: '已加载',
            ready: true,
          },
          {
            icon: <ApiOutlined />,
            name: '后端',
            tech: 'Java 21 · Spring Boot · MyBatis',
            state: connectionLabel,
            ready: connected,
          },
          {
            icon: <DatabaseOutlined />,
            name: '数据库',
            tech: 'MySQL 8.4 · 独立本地数据卷',
            state: connectionLabel,
            ready: connected,
          },
        ].map((item) => (
          <Card key={item.name} className="service-card">
            <div className="service-header">
              <span className="service-icon">{item.icon}</span>
              <Tag color={item.ready ? 'success' : status.isError ? 'error' : 'default'}>
                {item.state}
              </Tag>
            </div>
            <Title level={4}>{item.name}</Title>
            <Text type="secondary">{item.tech}</Text>
          </Card>
        ))}
      </div>

      {status.isError && (
        <Alert
          type="error"
          showIcon
          title="暂时无法连接后端或数据库"
          description={status.error.message}
          action={<Button onClick={() => void status.refetch()}>重新连接</Button>}
        />
      )}

      <Card className="probe-card" title="端到端联调" extra={<Tag color="blue">合成数据</Tag>}>
        <Paragraph type="secondary">
          写入一条记录，再刷新页面。记录从 MySQL 读取，容器重启后仍会保留。
        </Paragraph>
        <Form<CreateProbe> form={form} layout="vertical" onFinish={(input) => create.mutate(input)}>
          <div className="probe-form">
            <Form.Item
              name="note"
              label="联调记录"
              rules={[
                { required: true, whitespace: true, message: '请填写联调记录' },
                { max: 120, message: '最多 120 个字符' },
              ]}
            >
              <Input
                placeholder="例如：我的第一次前后端数据库联调"
                maxLength={120}
                disabled={!connected || create.isPending}
              />
            </Form.Item>
            <Button
              type="primary"
              htmlType="submit"
              icon={<PlusOutlined />}
              loading={create.isPending}
              disabled={!connected}
            >
              写入测试记录
            </Button>
          </div>
        </Form>
        {create.isError && (
          <Alert
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
        <Table<ProbeRecord>
          rowKey="id"
          loading={status.isPending}
          dataSource={status.data?.recentProbes ?? []}
          pagination={false}
          locale={{ emptyText: '暂无联调记录' }}
          scroll={{ x: 560 }}
          columns={[
            { title: '最近记录', dataIndex: 'note', key: 'note' },
            {
              title: '写入时间',
              dataIndex: 'createdAt',
              key: 'createdAt',
              width: 230,
              render: (value: string) => new Date(value).toLocaleString('zh-CN', { hour12: false }),
            },
          ]}
        />
        <div className="probe-footer">
          <Text type="secondary">共 {status.data?.totalProbes ?? '—'} 条 · 展示最近 5 条</Text>
          <Text type="secondary">当前数据库：{status.data?.database ?? '待连接'}</Text>
        </div>
      </Card>
      <Space className="workspace-footer" wrap>
        <Text type="secondary">独立开发环境</Text>
        <Text type="secondary">·</Text>
        <a href="http://127.0.0.1:9002/api/docs" target="_blank" rel="noreferrer">
          查看接口文档
        </a>
        <Text type="secondary">·</Text>
        <Text type="secondary">本轮范围：基础工程与连通性验证</Text>
      </Space>
    </main>
  );
}
