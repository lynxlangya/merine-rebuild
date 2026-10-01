import { Tag } from 'antd';
import styles from './StatusTag.module.css';

export function StatusTag({
  label,
  tone,
}: {
  label: string;
  tone: 'neutral' | 'accent' | 'success' | 'warning' | 'danger';
}) {
  return <Tag className={`${styles.tag} ${styles[tone]}`}>{label}</Tag>;
}
