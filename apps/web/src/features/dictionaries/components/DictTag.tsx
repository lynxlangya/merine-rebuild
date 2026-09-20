import type { DictionaryView } from '@merine/api-contract';
import { Tag } from 'antd';
import { dictLabel, dictTone } from '../model';
import styles from './DictTag.module.css';

/**
 * 字典标签：文案来自字典（含停用项，历史数据也能显示业务标签），
 * 色调由取值决定；字典还没加载出来时回退显示原始值，不出现空白。
 */
export function DictTag({
  dictionary,
  value,
}: {
  dictionary: DictionaryView | undefined;
  value: string;
}) {
  const tone = dictTone(value);
  return (
    <Tag className={tone === 'warning' ? styles.warning : styles.neutral}>
      {dictLabel(dictionary, value)}
    </Tag>
  );
}
