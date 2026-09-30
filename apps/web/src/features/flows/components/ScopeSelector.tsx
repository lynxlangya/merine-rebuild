import { Button, Tree } from 'antd';
import type { TreeDataNode } from 'antd';
import type { IntelligenceUnitOption } from '@merine/api-contract';
import { canToggleScopeUnit, subtreeCodes } from '../model';
import styles from '../InformationFlowPage.module.css';

export function ScopeSelector({
  options,
  value = [],
  onChange,
}: {
  options: IntelligenceUnitOption[];
  value?: string[];
  onChange?: (codes: string[]) => void;
}) {
  const node = (unit: IntelligenceUnitOption): TreeDataNode => {
    const children = options.filter((u) => u.parentCode === unit.code);
    return {
      key: unit.code,
      disabled: !canToggleScopeUnit(unit, value),
      title: (
        <span className={styles.treeTitle}>
          <span>
            {unit.name}
            {!unit.enabled ? '（停用）' : ''}
          </span>
          {children.length > 0 && (
            <Button
              type="link"
              size="small"
              aria-label={`选择${unit.name}整棵子树`}
              onClick={(event) => {
                event.stopPropagation();
                onChange?.([...new Set([...value, ...subtreeCodes(options, unit.code)])]);
              }}
            >
              选择子树
            </Button>
          )}
        </span>
      ),
      children: children.map(node),
    };
  };
  return (
    <div className={styles.scopeTree}>
      {options.length > 0 && (
        <Tree
          checkable
          checkStrictly
          selectable={false}
          defaultExpandAll
          checkedKeys={value}
          treeData={options.filter((u) => !u.parentCode).map(node)}
          onCheck={(keys) => onChange?.((Array.isArray(keys) ? keys : keys.checked).map(String))}
        />
      )}
      <div className={styles.hint}>已选 {value.length} 个单位，需包含中转单位。</div>
    </div>
  );
}
