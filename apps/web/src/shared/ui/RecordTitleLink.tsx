import type { ReactNode } from 'react';
import { Link } from 'react-router';
import styles from './RecordTitleLink.module.css';

/** 列表的主阅读入口：标题可导航，编号保持次要且可逐位核对。 */
export function RecordTitleLink({
  to,
  number,
  children,
}: {
  to: string;
  number?: string;
  children: ReactNode;
}) {
  return (
    <div className={styles.cell}>
      <Link className={styles.link} to={to}>
        {children}
      </Link>
      {number && <span className={styles.number}>{number}</span>}
    </div>
  );
}
