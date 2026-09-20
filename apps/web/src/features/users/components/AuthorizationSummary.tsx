import styles from './AuthorizationSummary.module.css';

/**
 * 授权摘要：保存前写清这次改动会得到什么角色、会不会踢掉现有会话。
 * 「会话失效」不是设计稿的修辞，而是后端行为：角色、所属单位或密码变化后
 * 授权版本递增，旧会话随即失效。
 */
export function AuthorizationSummary({
  roleNames,
  mode,
}: {
  roleNames: string[];
  mode: 'create' | 'edit';
}) {
  return (
    <dl className={styles.box}>
      <dt className={styles.term}>角色</dt>
      <dd className={styles.detail}>
        {roleNames.length > 0 ? roleNames.join('、') : '尚未选择角色'}
      </dd>
      <dt className={styles.term}>变更影响</dt>
      <dd className={styles.detail}>
        {mode === 'edit'
          ? '角色、所属单位或密码发生变化后，该账号的现有登录会话将失效，须重新登录并按新权限访问。'
          : '保存后账号立即可用，使用初始密码首次登录。'}
      </dd>
    </dl>
  );
}
