import type { ReasoningEffort } from '../model-providers/public';
import styles from './EffortSlider.module.css';

/** 自定义轨道和刻度；原生 range 保留拖动、触摸和键盘的标准交互。 */
export function EffortSlider({
  options,
  value,
  labelOf,
  onChange,
}: {
  options: ReasoningEffort[];
  value: ReasoningEffort;
  labelOf: (effort: ReasoningEffort) => string;
  onChange: (effort: ReasoningEffort) => void;
}) {
  if (options.length <= 1) return <p className={styles.single}>仅提供「{labelOf(value)}」档位</p>;
  const index = Math.max(0, options.indexOf(value));
  const max = options.length - 1;
  const ratio = index / max;
  const active = options[index];
  // range 的把手中心在左右各 16px 内缩处；填充和刻度使用同一坐标。
  const fillWidth = `calc(${ratio * 100}% + ${16 - 32 * ratio}px)`;
  return (
    <div className={styles.panel}>
      <div className={styles.control}>
        <div className={styles.track} aria-hidden="true">
          <span
            className={styles.fill}
            data-off={active === 'NONE' || undefined}
            style={{ width: fillWidth }}
          />
          <span className={styles.ticks}>
            {options.map((option, i) => (
              <span
                key={option}
                className={styles.tick}
                data-filled={(i <= index && active !== 'NONE') || undefined}
                style={{ left: `${(i / max) * 100}%` }}
              />
            ))}
          </span>
        </div>
        <input
          type="range"
          className={styles.range}
          min={0}
          max={max}
          step={1}
          value={index}
          aria-label="推理强度"
          aria-valuetext={labelOf(active)}
          onChange={(event) => {
            const next = options[Number(event.target.value)];
            if (next) onChange(next);
          }}
        />
      </div>
      <div className={styles.labels} role="group" aria-label="推理档位快捷选择">
        {options.map((option, i) => (
          <button
            key={option}
            type="button"
            className={styles.label}
            aria-pressed={i === index}
            style={{ left: `${(i / max) * 100}%` }}
            onClick={() => onChange(option)}
          >
            {labelOf(option)}
          </button>
        ))}
      </div>
    </div>
  );
}
