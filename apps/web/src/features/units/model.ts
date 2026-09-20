import type { UnitSummary, UnitTreeNode } from '@merine/api-contract';

/** 页面统一使用业务层级名称，不在各组件里重复写 1/2/3 的文案映射。 */
export const UNIT_LEVEL_LABELS: Record<number, string> = {
  1: '总队',
  2: '支队',
  3: '大队',
};

export function unitLevelLabel(level: number): string {
  return UNIT_LEVEL_LABELS[level] ?? `第 ${level} 级`;
}

/** TreeSelect 的纯数据节点；title/searchText 分开，搜索不依赖 React 节点。 */
export interface UnitSelectNode {
  value: string;
  title: string;
  searchText: string;
  disabled?: boolean;
  selectable?: boolean;
  children?: UnitSelectNode[];
}

/** 用户管理使用：把扁平单位选项还原成组织树，停用单位默认不可选。 */
export function buildUnitSelectTree(
  units: readonly UnitSummary[],
  currentCode?: string,
): UnitSelectNode[] {
  const byCode = new Map(units.map((unit) => [unit.code, unit]));
  const children = new Map<string, UnitSummary[]>();
  const roots: UnitSummary[] = [];

  for (const unit of units) {
    if (unit.parentCode && byCode.has(unit.parentCode)) {
      const list = children.get(unit.parentCode) ?? [];
      list.push(unit);
      children.set(unit.parentCode, list);
    } else {
      roots.push(unit);
    }
  }

  const sortUnits = (list: UnitSummary[]) =>
    [...list].sort((left, right) => left.code.localeCompare(right.code));

  const build = (unit: UnitSummary): UnitSelectNode => {
    const disabled = unit.status !== 'ENABLED' && unit.code !== currentCode;
    return {
      value: unit.code,
      title: unit.name,
      searchText: `${unit.name} ${unit.code}`,
      disabled,
      selectable: !disabled,
      children: sortUnits(children.get(unit.code) ?? []).map(build),
    };
  };

  return sortUnits(roots).map(build);
}

/** 单位表单使用：排除自身及后代，只允许一、二级单位成为上级。 */
export function buildParentSelectTree(
  nodes: readonly UnitTreeNode[],
  excludeCode?: string,
): UnitSelectNode[] {
  const build = (node: UnitTreeNode): UnitSelectNode | null => {
    if (node.code === excludeCode) return null;
    const children = node.children
      .map(build)
      .filter((child): child is UnitSelectNode => child !== null);
    const selectable = node.level < 3;
    return {
      value: node.code,
      title: node.name,
      searchText: `${node.name} ${node.code}`,
      disabled: !selectable,
      selectable,
      children,
    };
  };

  return nodes.map(build).filter((node): node is UnitSelectNode => node !== null);
}

function matches(node: UnitTreeNode, keyword: string): boolean {
  const needle = keyword.trim().toLocaleLowerCase();
  if (!needle) return true;
  return (
    node.name.toLocaleLowerCase().includes(needle) || node.code.toLocaleLowerCase().includes(needle)
  );
}

/**
 * 搜索保留命中节点的完整子树；如果父节点不命中，则保留通向命中后代的路径。
 */
export function filterUnitTree(nodes: readonly UnitTreeNode[], keyword: string): UnitTreeNode[] {
  if (!keyword.trim()) return [...nodes];
  const result: UnitTreeNode[] = [];
  for (const node of nodes) {
    const selfMatches = matches(node, keyword);
    const filteredChildren = filterUnitTree(node.children, keyword);
    if (selfMatches) {
      result.push(node);
    } else if (filteredChildren.length > 0) {
      result.push({ ...node, children: filteredChildren });
    }
  }
  return result;
}

export function findUnitTreeNode(
  nodes: readonly UnitTreeNode[],
  code: string,
): UnitTreeNode | null {
  for (const node of nodes) {
    if (node.code === code) return node;
    const found = findUnitTreeNode(node.children, code);
    if (found) return found;
  }
  return null;
}

export function collectUnitTreeKeys(nodes: readonly UnitTreeNode[]): string[] {
  return nodes.flatMap((node) => [node.code, ...collectUnitTreeKeys(node.children)]);
}

export function flattenUnitTree(nodes: readonly UnitTreeNode[]): UnitTreeNode[] {
  return nodes.flatMap((node) => [node, ...flattenUnitTree(node.children)]);
}

/**
 * 默认只展开总队和最后一个支队。
 * 最后一个支队取树的中序展开顺序，不依赖单位名称或 area_code。
 */
export function defaultUnitTreeExpandedKeys(nodes: readonly UnitTreeNode[]): string[] {
  const rootKeys = nodes.filter((node) => node.level === 1).map((node) => node.code);
  const levelTwoNodes = flattenUnitTree(nodes).filter((node) => node.level === 2);
  const lastLevelTwo = levelTwoNodes[levelTwoNodes.length - 1];
  return lastLevelTwo ? [...rootKeys, lastLevelTwo.code] : rootKeys;
}

export function findUnitName(
  nodes: readonly UnitTreeNode[],
  code: string | null | undefined,
): string | null {
  if (!code) return null;
  return findUnitTreeNode(nodes, code)?.name ?? null;
}
