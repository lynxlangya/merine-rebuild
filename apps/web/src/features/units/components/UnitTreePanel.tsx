import { ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import type { UnitTreeNode } from '@merine/api-contract';
import { Alert, Button, Empty, Input, Skeleton, Tag, Tree } from 'antd';
import type { ReactNode } from 'react';
import { useEffect, useMemo, useState } from 'react';
import {
  collectUnitTreeKeys,
  defaultUnitTreeExpandedKeys,
  filterUnitTree,
  unitLevelLabel,
} from '../model';
import styles from './UnitTreePanel.module.css';

export function UnitTreePanel({
  nodes,
  selectedCode,
  onSelect,
  search,
  onSearchChange,
  loading,
  error,
  onRetry,
}: {
  nodes: UnitTreeNode[];
  selectedCode: string | null;
  onSelect: (code: string) => void;
  search: string;
  onSearchChange: (value: string) => void;
  loading: boolean;
  error: string | null;
  onRetry: () => void;
}) {
  const [expandedKeys, setExpandedKeys] = useState<string[]>([]);
  const filtered = useMemo(() => filterUnitTree(nodes, search), [nodes, search]);
  const defaultExpandedKeys = useMemo(() => defaultUnitTreeExpandedKeys(nodes), [nodes]);

  useEffect(() => {
    const keys = search.trim() ? collectUnitTreeKeys(filtered) : defaultExpandedKeys;
    setExpandedKeys(keys);
  }, [filtered, search, defaultExpandedKeys]);

  const treeData = useMemo(
    () =>
      filtered.map(function toNode(node): {
        key: string;
        title: ReactNode;
        children: ReturnType<typeof toNode>[];
      } {
        return {
          key: node.code,
          title: (
            <span className={styles.node}>
              <span className={styles.nodeName}>{node.name}</span>
              <Tag className={styles.levelTag} variant="filled">
                {unitLevelLabel(node.level)}
              </Tag>
            </span>
          ),
          children: node.children.map(toNode),
        };
      }),
    [filtered],
  );

  const total = nodes.length === 0 ? 0 : collectUnitTreeKeys(nodes).length;

  return (
    <section className={styles.card} aria-label="单位组织树">
      <div className={styles.head}>
        <b>组织树</b>
        <Tag className={styles.countTag}>{loading ? '—' : total}</Tag>
        <span className={styles.spacer} />
        <Button
          type="text"
          size="small"
          icon={<ReloadOutlined />}
          aria-label="刷新单位树"
          loading={loading}
          onClick={onRetry}
        />
      </div>
      <Input
        className={styles.search}
        value={search}
        allowClear
        prefix={<SearchOutlined />}
        placeholder="搜索单位名称或编码"
        onChange={(event) => onSearchChange(event.target.value)}
      />
      <div className={styles.body}>
        {error && nodes.length > 0 && (
          <Alert
            className={styles.refreshError}
            type="warning"
            showIcon
            title="刷新失败，下面仍是上一次结果"
            description={error}
          />
        )}
        {loading && nodes.length === 0 ? (
          <Skeleton active title={false} paragraph={{ rows: 7 }} />
        ) : error && nodes.length === 0 ? (
          <Alert
            type="warning"
            showIcon
            title="组织树加载失败"
            description={error}
            action={
              <Button size="small" onClick={onRetry}>
                重试
              </Button>
            }
          />
        ) : filtered.length === 0 ? (
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description={search.trim() ? '没有匹配的单位' : '还没有单位'}
          />
        ) : (
          <Tree
            blockNode
            showLine
            treeData={treeData}
            selectedKeys={selectedCode ? [selectedCode] : []}
            expandedKeys={expandedKeys}
            onExpand={(keys) => setExpandedKeys(keys.map(String))}
            onSelect={(keys) => {
              const code = keys[0];
              if (typeof code === 'string') onSelect(code);
            }}
          />
        )}
      </div>
    </section>
  );
}
