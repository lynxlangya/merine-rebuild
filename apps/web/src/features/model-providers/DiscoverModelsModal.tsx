import type { DiscoveredProviderModel } from '@merine/api-contract';
import { Checkbox, Modal, Tag, Tooltip } from 'antd';
import { useState } from 'react';
import { effortsLabel, newModelsFrom, type ModelInput } from './discovery';
import styles from './DiscoverModelsModal.module.css';

/**
 * 拉取结果的选择弹窗：勾选要导入的模型。
 * 每个模型的推理强度由服务端按官方文档给出的取值目录带回来（上游 /models 不返回能力信息），
 * 导入即写入该模型的可用档位；文档没收录的模型显示「不设置」，之后也不发送档位参数。
 * 已在表单里的标识不可选，避免重复行。
 * 组件由抽屉在拿到结果后挂载，所以初始勾选直接由 props 推导，不需要同步用的副作用。
 */
export function DiscoverModelsModal({
  found,
  existing,
  limit,
  onCancel,
  onImport,
}: {
  found: DiscoveredProviderModel[];
  existing: ModelInput[];
  limit: number;
  onCancel: () => void;
  onImport: (models: ModelInput[]) => void;
}) {
  const known = new Set(existing.map((model) => model.modelId.trim()));
  const selectable = found.filter((model) => !known.has(model.modelId.trim()));
  const room = Math.max(limit, 0);
  const [chosen, setChosen] = useState<string[]>(
    selectable.slice(0, room).map((model) => model.modelId),
  );
  const picked = found.filter(
    (model) => chosen.includes(model.modelId) && !known.has(model.modelId.trim()),
  );
  const allChosen = selectable.length > 0 && chosen.length >= Math.min(selectable.length, room);

  return (
    <Modal
      open
      title="获取模型列表"
      width={560}
      okText={`导入 ${Math.min(picked.length, room)} 个模型`}
      okButtonProps={{ disabled: picked.length === 0 }}
      cancelText="取消"
      onCancel={onCancel}
      onOk={() => onImport(newModelsFrom(existing, picked, room))}
    >
      <p className={styles.discoverLead}>
        上游返回 {found.length} 个模型，其中 {found.length - selectable.length}{' '}
        个已在列表里。推理强度按每个模型的取值目录填写，导入后可逐个调整。
      </p>
      <div className={styles.discoverTools}>
        <Checkbox
          checked={allChosen}
          indeterminate={chosen.length > 0 && !allChosen}
          disabled={selectable.length === 0}
          onChange={(event) =>
            setChosen(
              event.target.checked ? selectable.slice(0, room).map((model) => model.modelId) : [],
            )
          }
        >
          全选
        </Checkbox>
      </div>
      <div className={styles.discoverList}>
        {found.map((model) => {
          const exists = known.has(model.modelId.trim());
          return (
            <label className={styles.discoverRow} key={model.modelId}>
              <Checkbox
                checked={chosen.includes(model.modelId)}
                disabled={exists}
                onChange={(event) =>
                  setChosen((current) =>
                    event.target.checked
                      ? [...current, model.modelId]
                      : current.filter((item) => item !== model.modelId),
                  )
                }
              />
              <span className={styles.discoverId}>{model.modelId}</span>
              <Tooltip title={`推理强度：${effortsLabel(model.reasoningEfforts)}`}>
                <span
                  className={styles.discoverEfforts}
                  data-empty={!model.reasoningEfforts.length || undefined}
                >
                  {effortsLabel(model.reasoningEfforts)}
                </span>
              </Tooltip>
              {model.ownedBy && <span className={styles.discoverOwner}>{model.ownedBy}</span>}
              {exists && (
                <Tag bordered={false} className={styles.modelTag}>
                  已存在
                </Tag>
              )}
            </label>
          );
        })}
      </div>
      {picked.length > room && (
        <p className={styles.discoverNote}>一个连接最多 20 个模型，本次只导入前 {room} 个。</p>
      )}
    </Modal>
  );
}
