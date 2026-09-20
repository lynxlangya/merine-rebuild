import { TreeSelect } from 'antd';
import type { TreeSelectProps } from 'antd';
import { buildUnitSelectTree } from '../model';
import { useUnitOptionsQuery } from '../queries';

export interface UnitTreeSelectProps {
  id?: string;
  className?: string;
  value?: string;
  onChange?: (value: string | undefined) => void;
  placeholder?: string;
  allowClear?: boolean;
  loading?: boolean;
  /** 编辑用户时，当前已停用的单位仍应显示，避免下拉回落到编码。 */
  currentCode?: string;
}

/**
 * 用户管理共用的单位选择器。
 *
 * 单位编码是稳定的对外标识；选择器只展示名称、编码和层级树，
 * 不把技术主键暴露给表单。
 */
export function UnitTreeSelect({
  id,
  className,
  value,
  onChange,
  placeholder,
  allowClear = false,
  loading = false,
  currentCode,
}: UnitTreeSelectProps) {
  const units = useUnitOptionsQuery();
  const treeData = buildUnitSelectTree(units.data ?? [], currentCode);

  return (
    <TreeSelect<string>
      id={id}
      className={className}
      value={value || undefined}
      onChange={(next) => onChange?.(next ?? undefined)}
      treeData={treeData as TreeSelectProps<string>['treeData']}
      treeNodeFilterProp="searchText"
      showSearch
      allowClear={allowClear}
      treeDefaultExpandAll
      loading={units.isPending || loading}
      placeholder={placeholder}
      notFoundContent={
        units.isError ? '单位选项加载失败' : units.isPending ? '加载中' : '没有可选单位'
      }
    />
  );
}
