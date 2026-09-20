import { ApartmentOutlined, SettingOutlined, TeamOutlined } from '@ant-design/icons';
import { Card, Descriptions, List, Tag, Typography } from 'antd';
import { Link } from 'react-router';
import { useAuth } from '../auth/AuthProvider';
import { PageHeader } from '../../shared/ui/PageHeader';
import styles from './HomePage.module.css';

/**
 * 首页只呈现真实数据：当前身份来自服务端会话，入口指向本轮已实现的模块。
 * 这里不摆演示指标卡——没有真实数据支撑的数字不如不显示。
 */
export function HomePage() {
  const { state } = useAuth();
  const user = state.status === 'authenticated' ? state.user : null;

  return (
    <div>
      <PageHeader
        demo={false}
        title="首页"
        description="本地开发环境下的海防研判工作台。当前开放系统管理中的用户管理与单位管理。"
      />

      <div className={styles.grid}>
        <Card title="当前身份" className={styles.card}>
          {user ? (
            <Descriptions column={1} size="small" colon={false}>
              <Descriptions.Item label="账号">
                <span className="mono">{user.loginName}</span>
              </Descriptions.Item>
              <Descriptions.Item label="姓名">{user.displayName}</Descriptions.Item>
              <Descriptions.Item label="所属单位">{user.unitName}</Descriptions.Item>
              <Descriptions.Item label="角色">
                {user.roleNames.length > 0
                  ? user.roleNames.map((role) => <Tag key={role}>{role}</Tag>)
                  : '未分配角色'}
              </Descriptions.Item>
              <Descriptions.Item label="授权版本">
                <span className="mono">{user.authorizationVersion}</span>
              </Descriptions.Item>
            </Descriptions>
          ) : (
            <Typography.Text type="secondary">正在读取身份…</Typography.Text>
          )}
          <p className={styles.note}>
            身份由服务端会话提供。角色或所属单位变化后授权版本递增，旧会话在下次请求即失效。
          </p>
        </Card>

        <Card title="可用入口" className={styles.card}>
          <List
            itemLayout="horizontal"
            dataSource={[
              {
                icon: <TeamOutlined />,
                title: '用户管理',
                description: '查询、新建与编辑账号：姓名、所属单位、角色与启用状态。',
                path: '/system/users',
              },
              {
                icon: <ApartmentOutlined />,
                title: '单位管理',
                description: '维护总队、支队、大队三级组织树，查看直属下级与用户。',
                path: '/system/units',
              },
              {
                icon: <SettingOutlined />,
                title: '工程诊断',
                description: '页面 → API → MySQL 的最小读写链路与探针记录，用于本地联调。',
                path: '/dev/diagnostics',
              },
            ]}
            renderItem={(item) => (
              <List.Item>
                <List.Item.Meta
                  avatar={item.icon}
                  title={<Link to={item.path}>{item.title}</Link>}
                  description={item.description}
                />
              </List.Item>
            )}
          />
          <p className={styles.note}>
            其他模块尚未开发。设计稿只作为样式与交互参考，不代表已经实现的功能。
          </p>
        </Card>
      </div>
    </div>
  );
}
