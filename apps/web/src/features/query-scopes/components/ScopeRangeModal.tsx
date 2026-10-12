import { CloudSyncOutlined } from '@ant-design/icons';
import type { ScopeViewMeta } from '@merine/api-contract';
import { Button, Checkbox, Empty, Input, Modal, Tag } from 'antd';
import { useState } from 'react';
import type { ScopeCandidate } from '../model';
import styles from '../QueryScopeDetailPage.module.css';

/** 范围选择与说明编辑分开，保存范围的停用后果在提交位置明确展示。 */
export function ScopeRangeModal({
  candidates,
  metadata,
  chosen,
  saving,
  reading,
  canTest,
  dirty,
  onChange,
  onInspect,
  onSave,
  onClose,
}: {
  candidates: ScopeCandidate[];
  metadata: Map<string, ScopeViewMeta>;
  chosen: string[];
  saving: boolean;
  reading: boolean;
  canTest: boolean;
  dirty: boolean;
  onChange: (views: string[]) => void;
  onInspect: () => void;
  onSave: () => void;
  onClose: () => void;
}) {
  const [search, setSearch] = useState('');
  const filtered = candidates.filter((item) =>
    `${item.name} ${metadata.get(item.name)?.displayName ?? ''}`
      .toLowerCase()
      .includes(search.trim().toLowerCase()),
  );
  return (
    <Modal
      open
      title="调整查询范围"
      width={680}
      onCancel={onClose}
      maskClosable={!saving}
      keyboard={!saving}
      closable={!saving}
      footer={
        <div className={styles.rangeFooter}>
          <span>{chosen.length} 个已选</span>
          <Button disabled={saving} onClick={onClose}>
            取消
          </Button>
          <Button
            type="primary"
            loading={saving}
            disabled={!dirty || reading || saving}
            onClick={onSave}
          >
            保存范围并停用
          </Button>
        </div>
      }
    >
      <p className={styles.rangeWarning}>保存后数据源会停用，重新检查并启用后生效。</p>
      <div className={styles.rangeTools}>
        <Input.Search
          aria-label="搜索候选视图"
          placeholder="搜索视图名称"
          allowClear
          value={search}
          onChange={(event) => setSearch(event.target.value)}
        />
        {canTest && (
          <Button
            icon={<CloudSyncOutlined />}
            loading={reading}
            disabled={saving || reading}
            onClick={onInspect}
          >
            发现视图
          </Button>
        )}
      </div>
      <div className={styles.rangeBulk}>
        <Button
          type="text"
          size="small"
          disabled={saving || reading || filtered.length === 0}
          onClick={() =>
            onChange(
              [
                ...new Set([
                  ...chosen,
                  ...filtered.filter((item) => item.detected !== false).map((item) => item.name),
                ]),
              ].sort(),
            )
          }
        >
          全选当前结果
        </Button>
        <Button
          type="text"
          size="small"
          disabled={saving || reading || chosen.length === 0}
          onClick={() =>
            onChange(chosen.filter((view) => !filtered.some((item) => item.name === view)))
          }
        >
          清空当前结果
        </Button>
      </div>
      <ul className={styles.rangeList}>
        {filtered.map((candidate) => (
          <li key={candidate.name}>
            <Checkbox
              checked={chosen.includes(candidate.name)}
              disabled={
                saving ||
                reading ||
                (candidate.detected === false && !chosen.includes(candidate.name))
              }
              onChange={(event) =>
                onChange(
                  event.target.checked
                    ? [...chosen, candidate.name].sort()
                    : chosen.filter((view) => view !== candidate.name),
                )
              }
            >
              <span className={styles.rangeIdentity}>
                <b>{metadata.get(candidate.name)?.displayName?.trim() || candidate.name}</b>
                <code>{candidate.name}</code>
              </span>
            </Checkbox>
            {candidate.detected === false && <Tag color="warning">未检测到授权</Tag>}
            {!candidate.saved && metadata.has(candidate.name) && <Tag>保留说明</Tag>}
          </li>
        ))}
      </ul>
      {filtered.length === 0 && (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description={search ? '没有匹配视图' : '点击“发现视图”读取候选项'}
        />
      )}
    </Modal>
  );
}
