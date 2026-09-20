import { theme as antdTheme, type ThemeConfig } from 'antd';

/**
 * 把应用语义 token 映射到 Ant Design。
 *
 * 这里必须传**具体色值**而不是 `var(--x)`：Ant Design 会解析 colorPrimary 等
 * 种子色来派生 hover / active / soft 变体，CSS 变量不是它认识的颜色格式，
 * 传进去会得到错误的派生色（主按钮和选中行背景会变成近黑）。
 *
 * 色值仍然只维护在 `tokens.css` 一处：切换主题时 ThemeProvider 先改
 * `data-theme`，这里再按当前主题把变量读成具体值。
 */
export function buildTheme(dark: boolean): ThemeConfig {
  const styles = getComputedStyle(document.documentElement);
  const read = (name: string) => styles.getPropertyValue(name).trim();

  return {
    algorithm: dark ? antdTheme.darkAlgorithm : antdTheme.defaultAlgorithm,
    token: {
      colorBgLayout: read('--bg'),
      colorBgContainer: read('--surface'),
      colorBgElevated: read('--surface'),
      colorText: read('--fg'),
      colorTextSecondary: read('--muted'),
      colorTextTertiary: read('--fg-placeholder'),
      colorTextQuaternary: read('--disabled-fg'),
      colorTextDisabled: read('--disabled-fg'),
      colorBorder: read('--border-control'),
      colorBorderSecondary: read('--border'),
      colorPrimary: read('--accent'),
      colorPrimaryHover: read('--accent-hover'),
      colorPrimaryActive: read('--accent-active'),
      colorPrimaryBg: read('--accent-soft'),
      colorSuccess: read('--success'),
      colorWarning: read('--warning'),
      colorError: read('--danger'),
      colorFillQuaternary: read('--row-hover'),
      colorFillTertiary: read('--surface-active'),
      colorBgContainerDisabled: read('--disabled-bg'),
      colorLink: read('--accent'),
      borderRadius: 6,
      borderRadiusSM: 4,
      borderRadiusLG: 8,
      controlHeight: 32,
      controlHeightSM: 28,
      controlHeightLG: 40,
      fontFamily: 'var(--font-body)',
      fontSize: 13,
    },
    components: {
      Layout: {
        bodyBg: read('--bg'),
        headerBg: read('--surface'),
        siderBg: read('--nav-bg'),
      },
      Menu: {
        itemBg: 'transparent',
        itemSelectedBg: read('--nav-selected'),
        itemHoverBg: read('--nav-hover'),
        itemSelectedColor: read('--fg'),
        itemColor: read('--muted'),
        itemHeight: 36,
        itemBorderRadius: 6,
      },
      Table: {
        headerBg: read('--surface-2'),
        headerColor: read('--muted'),
        rowHoverBg: read('--row-hover'),
        rowSelectedBg: read('--surface-selected'),
        rowSelectedHoverBg: read('--surface-selected'),
        borderColor: read('--border'),
        headerSplitColor: read('--border'),
      },
      Card: {
        colorBorderSecondary: read('--border'),
      },
      Modal: {
        contentBg: read('--surface'),
        headerBg: read('--surface'),
      },
      Drawer: {
        colorBgElevated: read('--surface'),
      },
    },
  };
}
