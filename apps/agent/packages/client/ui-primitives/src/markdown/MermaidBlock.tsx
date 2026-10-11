/**
 * ```mermaid 围栏的渲染器：本地懒加载 mermaid，把图源码画成内联 SVG。
 *
 * - 动态 import，只有真的出现 mermaid 图时才加载对应 chunk；不依赖任何 CDN，内网离线可用。
 * - securityLevel: 'strict'：禁用 HTML 标签与点击处理器，图源码只作为文本内容。
 * - 图语法错误时回退为普通代码块（由调用方传入 fallback），不吞掉用户内容。
 * - 跟随 `body[data-ds-dark-theme]` 切换亮暗主题重新渲染。
 */
import { useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import css from './MermaidBlock.module.css'

export interface MermaidBlockProps {
  /** Diagram source, without the fence markers. */
  code: string
  /** Rendered when the diagram cannot be parsed or drawn (typically the plain code block). */
  fallback: ReactNode
}

let sequence = 0

function isDarkTheme(): boolean {
  return document.body.hasAttribute('data-ds-dark-theme')
}

/**
 * Render one mermaid diagram, or the caller's fallback when rendering fails.
 * @param props - diagram source and fallback element.
 * @returns the diagram wrapper.
 */
export function MermaidBlock({ code, fallback }: MermaidBlockProps) {
  const [dark, setDark] = useState(isDarkTheme)
  const [svg, setSvg] = useState<string>()
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    const observer = new MutationObserver(() => { setDark(isDarkTheme()) })
    observer.observe(document.body, { attributes: true, attributeFilter: ['data-ds-dark-theme'] })
    return () => { observer.disconnect() }
  }, [])

  useEffect(() => {
    let cancelled = false
    setSvg(undefined)
    setFailed(false)
    void (async () => {
      try {
        const { default: mermaid } = await import('mermaid')
        mermaid.initialize({
          startOnLoad: false,
          securityLevel: 'strict',
          theme: dark ? 'dark' : 'default',
        })
        const { svg: rendered } = await mermaid.render(`merine-mermaid-${String(++sequence)}`, code)
        if (!cancelled) setSvg(rendered)
      } catch {
        if (!cancelled) setFailed(true)
      }
    })()
    return () => { cancelled = true }
  }, [code, dark])

  if (failed) return <>{fallback}</>
  return (
    <div className={css.root} data-mermaid>
      {svg === undefined
        ? <pre className={css.pending}>{code}</pre>
        : <div className={css.diagram} dangerouslySetInnerHTML={{ __html: svg }} />}
    </div>
  )
}
