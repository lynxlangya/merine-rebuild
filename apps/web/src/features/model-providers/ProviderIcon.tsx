import type { ModelProvider } from '@merine/api-contract';
import styles from './ProviderIcon.module.css';

/**
 * 供应商官方标识：文件在 `public/vendor-icons/`，来源与格式见同目录 ICONS.md。
 * 标识只用于指认供应商（指示性使用），商标归各自公司；CUSTOM 没有官方标识，用自绘中性占位。
 */
const VENDOR_ICONS: Record<ModelProvider['vendor'], string> = {
  DEEPSEEK: '/vendor-icons/deepseek.svg',
  QWEN: '/vendor-icons/qwen.svg',
  ZHIPU: '/vendor-icons/zhipu.png',
  MOONSHOT: '/vendor-icons/moonshot.ico',
  DOUBAO: '/vendor-icons/doubao.png',
  BAIDU: '/vendor-icons/baidu.svg',
  TENCENT: '/vendor-icons/tencent.ico',
  MINIMAX: '/vendor-icons/minimax.png',
  CUSTOM: '/vendor-icons/custom.svg',
};

/** 只要图标本身、不带外框，供助手模型选择器等紧凑场景复用。 */
export function ProviderGlyph({ vendor }: { vendor: ModelProvider['vendor'] }) {
  return <img className={styles.glyph} src={VENDOR_ICONS[vendor]} alt="" aria-hidden="true" />;
}

export function ProviderIcon({ vendor, name }: { vendor: ModelProvider['vendor']; name?: string }) {
  return (
    <span className={styles.icon}>
      <img
        className={styles.glyphLarge}
        src={VENDOR_ICONS[vendor]}
        alt={name ? `${name} 的官方标识` : '供应商官方标识'}
      />
    </span>
  );
}
