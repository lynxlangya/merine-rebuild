import { LogoutOutlined, SettingOutlined } from '@ant-design/icons';
import { App, Avatar, Button, Dropdown } from 'antd';
import { useState } from 'react';
import { useNavigate } from 'react-router';
import { errorText } from '../../shared/api-error';
import { PERMISSIONS, hasPermission } from '../../shared/permissions';
import { useAuth } from '../auth/public';
import styles from './AgentAccountMenu.module.css';

export function AgentAccountMenu() {
  const { state, signOut } = useAuth();
  const { message } = App.useApp();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [signingOut, setSigningOut] = useState(false);

  if (state.status !== 'authenticated') return null;
  const { user } = state;
  // 模型连接的管理入口按权限显示：连接本身对所有登录用户可用，只有管理需要权限码。
  const canManageModels = hasPermission(user.permissionCodes, PERMISSIONS.providerRead);

  const handleSignOut = async () => {
    if (signingOut) return;
    setSigningOut(true);
    try {
      await signOut();
      // 由 RequireAuth 统一跳转；退出失败时保留用户信息与重试入口。
    } catch (error) {
      message.error(`退出未完成：${errorText(error)}`);
    } finally {
      setSigningOut(false);
    }
  };

  return (
    <Dropdown
      trigger={['click']}
      placement="topLeft"
      autoFocus
      open={open}
      onOpenChange={(next, info) => {
        if (info.source === 'trigger') setOpen(next);
      }}
      menu={{
        style: { boxShadow: 'none', padding: 0, background: 'transparent' },
        items: [
          ...(canManageModels
            ? [
                {
                  key: 'settings',
                  icon: <SettingOutlined />,
                  label: '设置',
                  onClick: () => {
                    setOpen(false);
                    void navigate('/agent/settings');
                  },
                },
                { type: 'divider' as const },
              ]
            : []),
          {
            key: 'sign-out',
            icon: <LogoutOutlined />,
            label: signingOut ? '正在退出…' : '退出登录',
            disabled: signingOut,
            onClick: () => void handleSignOut(),
          },
        ],
      }}
      popupRender={(menu) => (
        <div className={styles.panel}>
          <div className={styles.identity}>
            <Avatar size={36} className={styles.avatar}>
              {user.displayName.slice(0, 1)}
            </Avatar>
            <strong className={styles.name}>{user.displayName}</strong>
          </div>
          <dl className={styles.facts} aria-label="当前用户信息">
            <div>
              <dt>账号</dt>
              <dd>{user.loginName}</dd>
            </div>
            <div>
              <dt>单位</dt>
              <dd>{user.unitName}</dd>
            </div>
            <div>
              <dt>角色</dt>
              <dd>{user.roleNames.join('、') || '未分配角色'}</dd>
            </div>
          </dl>
          <div className={styles.actions}>{menu}</div>
        </div>
      )}
    >
      <Button
        type="text"
        className={styles.trigger}
        aria-label={`用户菜单：${user.displayName}`}
        aria-haspopup="menu"
        aria-expanded={open}
      >
        <Avatar size={28} className={styles.avatar}>
          {user.displayName.slice(0, 1)}
        </Avatar>
      </Button>
    </Dropdown>
  );
}
