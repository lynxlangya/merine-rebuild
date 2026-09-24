import type { IconOption } from '@merine/api-contract';
import { DownOutlined } from '@ant-design/icons';
import { Popover, Tooltip } from 'antd';
import { useState } from 'react';
import { hasRegisteredIcon, iconByName } from '../../../app/iconRegistry';
import styles from './IconPicker.module.css';

/**
 * 导航图标选择器：点开是一张图标小方格网格，点选即生效。
 *
 * 选项来自后端 /api/system/menus/icons（名称与中文名），组件映射来自 app/iconRegistry；
 * 后端有、前端还没注册的名称会被过滤并提示，不会画出点不动的空格子。
 * 「默认」表示回落：页面用路由注册表图标，目录用文件夹。
 */
export function IconPicker({
  id,
  value,
  onChange,
  options,
}: {
  /** Form.Item 注入，用于把 label 关联到触发按钮 */
  id?: string;
  value?: string | null;
  onChange?: (value: string | undefined) => void;
  options: IconOption[];
}) {
  const [open, setOpen] = useState(false);
  const selected = value ? options.find((option) => option.name === value) : undefined;
  const renderable = options.filter((option) => hasRegisteredIcon(option.name));
  const missingCount = options.length - renderable.length;

  const pick = (name: string | undefined) => {
    onChange?.(name);
    setOpen(false);
  };

  return (
    <Popover
      open={open}
      onOpenChange={setOpen}
      trigger="click"
      placement="bottomLeft"
      content={
        <div className={styles.panel}>
          <div className={styles.grid} role="radiogroup" aria-label="导航图标">
            <Tooltip title="默认：目录用文件夹，页面用路由注册表图标">
              <button
                type="button"
                role="radio"
                aria-checked={!value}
                aria-label="默认图标"
                className={`${styles.tile} ${!value ? styles.selected : ''}`}
                onClick={() => pick(undefined)}
              >
                <span className={styles.defaultText}>默认</span>
              </button>
            </Tooltip>
            {renderable.map((option) => (
              <Tooltip key={option.name} title={`${option.label}（${option.name}）`}>
                <button
                  type="button"
                  role="radio"
                  aria-checked={value === option.name}
                  aria-label={`${option.label}（${option.name}）`}
                  className={`${styles.tile} ${value === option.name ? styles.selected : ''}`}
                  onClick={() => pick(option.name)}
                >
                  {iconByName(option.name)}
                </button>
              </Tooltip>
            ))}
          </div>
          {missingCount > 0 && (
            <p className={styles.hint}>
              另有 {missingCount} 个图标后端已注册、前端还未实现，更新前端后可选。
            </p>
          )}
        </div>
      }
    >
      <button
        type="button"
        id={id}
        className={styles.trigger}
        aria-haspopup="dialog"
        aria-expanded={open}
        aria-label="选择导航图标"
      >
        <span className={styles.triggerIcon}>{iconByName(value)}</span>
        <span className={value ? styles.triggerLabel : styles.triggerPlaceholder}>
          {selected ? `${selected.label}（${selected.name}）` : (value ?? '默认图标')}
        </span>
        <DownOutlined className={styles.chevron} />
      </button>
    </Popover>
  );
}
