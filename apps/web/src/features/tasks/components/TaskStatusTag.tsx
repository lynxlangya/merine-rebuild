import { DICTIONARY_CODES, dictLabel, useDictionary } from '../../dictionaries/public';
import { taskStatusTag } from '../model';
import { TaskTag } from './TaskTag';

/** 关联列表与任务列表共享整单状态的标签和色调。 */
export function TaskStatusTag({ status }: { status?: string }) {
  const dictionary = useDictionary(DICTIONARY_CODES.taskOrderStatus);
  return (
    <TaskTag
      {...taskStatusTag(status, (code) => (code ? dictLabel(dictionary.data, code) : '—'))}
    />
  );
}
