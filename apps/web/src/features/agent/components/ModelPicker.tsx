import {
  CheckOutlined,
  CloseOutlined,
  DownOutlined,
  ReloadOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import { Button, Input, Popover, Tooltip, type InputRef } from 'antd';
import type { ModelOption } from '@merine/api-contract';
import { useEffect, useId, useRef, useState } from 'react';
import {
  ProviderGlyph,
  reasoningEffortOptions,
  type ReasoningEffort,
} from '../../model-providers/public';
import { EffortSlider } from '../EffortSlider';
import styles from './ModelPicker.module.css';

export function effortLabel(effort: ReasoningEffort | undefined): string {
  return reasoningEffortOptions.find((option) => option.value === effort)?.label ?? effort ?? '';
}

/** 同一个非模态弹框内切换模型与强度；选择即时生效，不自动发起请求。 */
export function ModelPicker({
  options,
  selected,
  effort,
  disabled,
  onModelChange,
  onEffortChange,
  onResetEffort,
}: {
  options: ModelOption[];
  selected: ModelOption;
  effort: ReasoningEffort | undefined;
  disabled: boolean;
  onModelChange: (modelOptionId: string) => void;
  onEffortChange: (effort: ReasoningEffort) => void;
  onResetEffort: () => void;
}) {
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState('');
  const trigger = useRef<HTMLButtonElement>(null);
  const searchInput = useRef<InputRef>(null);
  const panel = useRef<HTMLDivElement>(null);
  const id = useId();
  const efforts = reasoningEffortOptions
    .map((option) => option.value)
    .filter((value) => selected.reasoningEfforts.includes(value));
  const needle = search.trim().toLocaleLowerCase();
  const filtered = options.filter((option) =>
    `${option.displayName} ${option.modelId} ${option.providerName}`
      .toLocaleLowerCase()
      .includes(needle),
  );
  const groups = new Map<string, { name: string; models: ModelOption[] }>();
  for (const option of filtered) {
    const group = groups.get(option.providerId) ?? { name: option.providerName, models: [] };
    group.models.push(option);
    groups.set(option.providerId, group);
  }
  useEffect(() => {
    if (disabled) {
      setOpen(false);
      setSearch('');
    }
  }, [disabled]);
  const changeOpen = (next: boolean) => {
    setOpen(next);
    if (!next) setSearch('');
  };
  const close = () => {
    changeOpen(false);
    trigger.current?.focus();
  };

  return (
    <Popover
      open={open && !disabled}
      onOpenChange={changeOpen}
      afterOpenChange={(next) => {
        if (next) {
          searchInput.current?.focus();
          panel.current
            ?.querySelector('input[type="radio"]:checked')
            ?.closest('label')
            ?.scrollIntoView({ block: 'nearest' });
        }
      }}
      trigger="click"
      placement="topLeft"
      arrow={false}
      styles={{ container: { padding: 0, overflow: 'hidden', borderRadius: 16 } }}
      content={
        <div
          ref={panel}
          id={id}
          className={styles.panel}
          role="dialog"
          aria-label="选择模型与推理强度"
          onKeyDown={(event) => {
            if (event.key === 'Escape') {
              event.preventDefault();
              event.stopPropagation();
              close();
            }
          }}
          onBlur={(event) => {
            const next = event.relatedTarget;
            if (
              next instanceof Node &&
              !event.currentTarget.contains(next) &&
              next !== trigger.current
            )
              changeOpen(false);
          }}
        >
          <div className={styles.head}>
            <strong>模型与推理</strong>
            <Button
              type="text"
              size="small"
              icon={<CloseOutlined />}
              aria-label="关闭模型选择"
              onClick={close}
            />
          </div>
          <div className={styles.search}>
            <Input
              ref={searchInput}
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              prefix={<SearchOutlined aria-hidden="true" />}
              placeholder="搜索模型或连接"
              aria-label="搜索模型或连接"
              allowClear
              onKeyDown={(event) => {
                if (event.key === 'ArrowDown') {
                  event.preventDefault();
                  const radio =
                    panel.current?.querySelector<HTMLInputElement>('input[type="radio"]:checked') ??
                    panel.current?.querySelector<HTMLInputElement>('input[type="radio"]');
                  radio?.closest('label')?.scrollIntoView({ block: 'nearest' });
                  radio?.focus();
                }
              }}
            />
          </div>
          <div className={styles.models}>
            <fieldset className={styles.modelGroup}>
              <legend className={styles.srOnly}>可用模型</legend>
              {[...groups].map(([providerId, group]) => (
                <div key={providerId}>
                  <div className={styles.groupTitle}>{group.name}</div>
                  {group.models.map((option) => (
                    <label
                      key={option.id}
                      className={`${styles.option} ${option.id === selected.id ? styles.selected : ''}`}
                    >
                      <input
                        type="radio"
                        name={`${id}-model`}
                        value={option.id}
                        aria-label={`${option.displayName}，${option.providerName}，${option.modelId}`}
                        checked={option.id === selected.id}
                        onChange={() => onModelChange(option.id)}
                        className={styles.srOnly}
                      />
                      <span className={styles.glyph} aria-hidden="true">
                        <ProviderGlyph vendor={option.vendor} />
                      </span>
                      <span className={styles.optionText}>
                        <span className={styles.modelName}>{option.displayName}</span>
                        {option.displayName !== option.modelId && (
                          <span className={styles.modelId}>{option.modelId}</span>
                        )}
                      </span>
                      <CheckOutlined className={styles.check} aria-hidden="true" />
                    </label>
                  ))}
                </div>
              ))}
            </fieldset>
            {filtered.length === 0 && <p className={styles.empty}>没有找到匹配的模型</p>}
          </div>
          <div className={styles.reasoning}>
            <div className={styles.effortHead}>
              <span>推理强度</span>
              {efforts.length > 1 && (
                <Tooltip title="恢复当前模型的默认强度">
                  <Button
                    type="text"
                    size="small"
                    className={styles.resetEffort}
                    icon={<ReloadOutlined />}
                    aria-label="恢复当前模型的默认强度"
                    onClick={onResetEffort}
                  >
                    默认
                  </Button>
                </Tooltip>
              )}
            </div>
            {effort ? (
              <EffortSlider
                options={efforts}
                value={effort}
                labelOf={effortLabel}
                onChange={onEffortChange}
              />
            ) : (
              <p className={styles.noEffort}>此模型未配置可选推理档位</p>
            )}
          </div>
          <div className={styles.note}>选择即时生效，用于下一次发送</div>
        </div>
      }
    >
      <button
        ref={trigger}
        type="button"
        className={styles.trigger}
        disabled={disabled}
        aria-label={`模型与推理强度：${selected.providerName}，${selected.displayName}${effort ? `，${effortLabel(effort)}` : ''}`}
        aria-haspopup="dialog"
        aria-expanded={open && !disabled}
        aria-controls={open ? id : undefined}
      >
        <span className={styles.triggerGlyph} aria-hidden="true">
          <ProviderGlyph vendor={selected.vendor} />
        </span>
        <span className={styles.triggerName}>{selected.displayName}</span>
        {effort && <span className={styles.triggerEffort}>{effortLabel(effort)}</span>}
        <DownOutlined className={styles.caret} aria-hidden="true" />
      </button>
    </Popover>
  );
}
