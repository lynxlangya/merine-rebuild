import type { TaskTextPart } from '../model';
import { formatTaskTime } from '../taskTime';
import styles from './TaskTime.module.css';

/** 任务时间：统一到分钟，等宽数字，保留机器可读的原值。 */
export function TaskTime({ value }: { value?: string }) {
  return (
    <time className={styles.time} dateTime={value || undefined}>
      {formatTaskTime(value)}
    </time>
  );
}

/** 带时间的说明文字：句中的时间与单独显示的时间同一样式，不会被折成两行。 */
export function TaskText({ parts }: { parts: TaskTextPart[] }) {
  return (
    <>
      {parts.map((part, index) =>
        typeof part === 'string' ? part : <TaskTime key={index} value={part.time} />,
      )}
    </>
  );
}
