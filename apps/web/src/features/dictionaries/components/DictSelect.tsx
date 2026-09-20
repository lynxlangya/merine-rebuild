import type { DictionaryView } from '@merine/api-contract';
import { Select } from 'antd';
import { useMemo } from 'react';
import { toDictOptions } from '../model';

/**
 * 字典下拉：选项与顺序来自字典（只列启用项）。
 * includeAllLabel 用于查询表单的"全部"选项；没有它就是一个普通必选下拉。
 */
export function DictSelect({
  dictionary,
  value,
  onChange,
  includeAllLabel,
  loading,
  disabled,
  id,
  className,
  placeholder,
}: {
  dictionary: DictionaryView | undefined;
  value: string;
  onChange: (value: string) => void;
  /** 传入文案时在最前面加一个空值选项，例如"全部状态" */
  includeAllLabel?: string;
  loading?: boolean;
  disabled?: boolean;
  id?: string;
  className?: string;
  placeholder?: string;
}) {
  const options = useMemo(() => {
    const items = toDictOptions(dictionary);
    return includeAllLabel ? [{ value: '', label: includeAllLabel }, ...items] : items;
  }, [dictionary, includeAllLabel]);

  return (
    <Select
      id={id}
      className={className}
      value={value}
      loading={loading}
      disabled={disabled}
      options={options}
      placeholder={placeholder}
      onChange={(next: string) => onChange(next)}
    />
  );
}
