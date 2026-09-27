import type { HistoryEntry } from '../model';
import { TaskText, TaskTime } from './TaskTime';
import styles from './TaskHistoryList.module.css';

/** 办理记录：一行一件事，时间在左，说明原文另起一行。 */
export function TaskHistoryList({ entries }: { entries: HistoryEntry[] }) {
  return (
    <ol className={styles.list}>
      {entries.map((entry) => (
        <li key={entry.key}>
          <span className={styles.at}>
            <TaskTime value={entry.at} />
          </span>
          <div>
            <span>
              <TaskText parts={entry.sentence} />
              <span className={styles.note}> · {entry.actorUserName ?? '—'}</span>
            </span>
            {entry.note ? <p className={styles.note}>{entry.note}</p> : null}
          </div>
        </li>
      ))}
    </ol>
  );
}
