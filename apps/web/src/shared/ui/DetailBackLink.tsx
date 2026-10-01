import { ArrowLeftOutlined } from '@ant-design/icons';
import { Link } from 'react-router';
import styles from './DetailBackLink.module.css';

export function DetailBackLink({ to, children }: { to: string; children: string }) {
  return (
    <Link className={styles.back} to={to}>
      <ArrowLeftOutlined />
      {children}
    </Link>
  );
}
