import type { IconProps } from './icons/props.ts'

/** 海防研判工作台品牌图标；与主项目 `apps/web/public/brand-mark.svg` 保持一致。 */
export function HaiphongLogo({ size = 24, className }: IconProps) {
  return (
    <svg
      width={size}
      height={size}
      className={className}
      viewBox="0 0 32 32"
      fill="none"
      aria-hidden="true"
    >
      <rect width="32" height="32" rx="8" fill="#2563EB" />
      <path
        d="M16 5 25 8v8c0 5-4 8.5-9 11-5-2.5-9-6-9-11V8Z"
        stroke="#fff"
        strokeWidth="1.8"
        strokeLinejoin="round"
      />
      <path
        d="m11 15 5-5 5 5M16 10v8M9 20c2-2 4-2 7 0s5 2 7 0"
        stroke="#fff"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <circle cx="16" cy="10" r="2" fill="#fff" />
    </svg>
  )
}
