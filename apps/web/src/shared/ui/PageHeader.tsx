import { Tag } from 'antd';
import type { ReactNode } from 'react';
import styles from './PageHeader.module.css';

/**
 * 页面标题区：只负责标题、说明和右侧操作，不承载查询与数据逻辑。
 */
export function PageHeader({
  title,
  description,
  actions,
  demo = true,
}: {
  title: string;
  description?: string;
  actions?: ReactNode;
  /** 页面数据是否为合成演示数据；为真时在标题旁明确标识。 */
  demo?: boolean;
}) {
  return (
    <header className={styles.head}>
      <div className={styles.main}>
        <h1 className={styles.title}>
          {title}
          {demo && (
            <Tag className={styles.demo} color="default">
              合成演示数据
            </Tag>
          )}
        </h1>
        {description && <p className={styles.description}>{description}</p>}
      </div>
      {actions && <div className={styles.actions}>{actions}</div>}
    </header>
  );
}
