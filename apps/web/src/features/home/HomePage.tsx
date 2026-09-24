import { Card, Descriptions, List, Tag, Typography } from 'antd';
import { useMemo } from 'react';
import { Link } from 'react-router';
import { useAuth } from '../auth/public';
import { toHomeEntries, useMyMenusQuery } from '../menus/public';
import { PageHeader } from '../../shared/ui/PageHeader';
import { resolveIcon } from '../../app/iconRegistry';
import { routeByKey } from '../../app/routeRegistry';
import styles from './HomePage.module.css';

/**
 * 首页只呈现真实数据：当前身份来自服务端会话，入口指向本轮已实现的模块。
 * 这里不摆演示指标卡——没有真实数据支撑的数字不如不显示。
 * 「可用入口」直接来自后端的导航树：菜单里有什么，这里就有什么，不会出现两处口径不同。
 */
export function HomePage() {
  const { state } = useAuth();
  const user = state.status === 'authenticated' ? state.user : null;
  const myMenus = useMyMenusQuery();
  const entries = useMemo(
    () =>
      toHomeEntries(myMenus.data ?? [], (routeKey) => {
        const route = routeByKey(routeKey);
        return route ? { path: route.path, icon: route.icon } : undefined;
      }),
    [myMenus.data],
  );

  return (
    <div>
      <PageHeader
        demo={false}
        title="首页"
        description="本地开发环境下的海防研判工作台。系统管理里已开放用户、角色、菜单、单位与字典管理，功能权限按页面与按钮分配给角色；没有权限的入口与按钮不会出现在页面上。"
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
              <Descriptions.Item label="功能权限">
                {user.permissionCodes.length > 0
                  ? `${user.permissionCodes.length} 项`
                  : '无（只有首页与工程诊断入口）'}
              </Descriptions.Item>
              <Descriptions.Item label="授权版本">
                <span className="mono">{user.authorizationVersion}</span>
              </Descriptions.Item>
            </Descriptions>
          ) : (
            <Typography.Text type="secondary">正在读取身份…</Typography.Text>
          )}
          <p className={styles.note}>
            身份与功能权限由服务端会话提供。角色、角色权限或所属单位变化后授权版本递增，
            旧会话在下次请求即失效。
          </p>
        </Card>

        <Card title="可用入口" className={styles.card}>
          <List
            itemLayout="horizontal"
            rowKey={(item) => item.key}
            dataSource={entries}
            renderItem={(item) => (
              <List.Item>
                <List.Item.Meta
                  avatar={resolveIcon(item.iconName, item.icon)}
                  title={<Link to={item.path}>{item.title}</Link>}
                  description={item.description}
                />
              </List.Item>
            )}
          />
          <p className={styles.note}>
            {entries.length === 0 && !myMenus.isPending
              ? '当前角色没有可用的页面权限，请联系管理员调整。'
              : '入口与左侧导航来自同一份菜单数据；其他模块尚未开发，设计稿只作样式参考。'}
          </p>
        </Card>
      </div>
    </div>
  );
}
