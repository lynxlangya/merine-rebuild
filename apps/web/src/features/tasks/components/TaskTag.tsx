import { Tag } from 'antd';
import type { TaskLabel } from '../model';
import styles from './TaskTag.module.css';

/** 任务状态与结果标签：色调随文字一起出现，不单靠颜色表达含义。 */
export function TaskTag({ label, tone }: TaskLabel) {
  return <Tag className={`${styles.tag} ${styles[tone]}`}>{label}</Tag>;
}
