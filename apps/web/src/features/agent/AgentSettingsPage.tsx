import { Result } from 'antd';
import { PERMISSIONS, hasPermission } from '../../shared/permissions';
import { useAuth } from '../auth/public';
import { ModelProvidersPage } from '../model-providers/public';
import styles from './AgentSettingsPage.module.css';

/**
 * 助手设置页：目前只承载模型连接配置（与后台“智能体管理 → 模型供应商”共用同一份实现）。
 *
 * 连接配置好后**全体登录用户都能在对话里使用**（使用侧模型列表登录即可读）；
 * 这个页面与页面里的按钮则由权限码控制，与后台其它管理页一致：查看、新增、编辑、删除各一个码。
 */
export function AgentSettingsPage() {
  const { state } = useAuth();
  const allowed =
    state.status === 'authenticated' &&
    hasPermission(state.user.permissionCodes, PERMISSIONS.providerRead);
  if (!allowed) {
    return (
      <div className={styles.settings}>
        <div className={styles.forbidden}>
          <Result
            status="403"
            title="没有管理模型连接的权限"
            subTitle="请联系管理员为你的角色授予「模型供应商 · 查看」；模型连接本身对所有登录用户可用。"
          />
        </div>
      </div>
    );
  }
  return (
    <div className={styles.settings}>
      <ModelProvidersPage />
    </div>
  );
}
