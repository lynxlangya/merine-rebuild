import { XMarkdown, type ComponentProps } from '@ant-design/x-markdown';
import { Link } from 'react-router';
import { classifyHref } from '../navigation';
import styles from './AgentParts.module.css';

/**
 * 文本部件：Markdown 渲染必须显式开启 escapeRawHtml（组件库默认 false）。
 * 站内链接走前端路由，外链新标签页并带 noopener，非法协议退化为纯文本。
 */
export function TextPart({ text, streaming }: { text: string; streaming: boolean }) {
  return (
    <XMarkdown
      className={styles.markdown}
      content={text}
      escapeRawHtml
      streaming={{ hasNextChunk: streaming, tail: streaming ? { content: '▍' } : false }}
      components={{ a: MarkdownLink }}
    />
  );
}

function MarkdownLink(props: ComponentProps) {
  const href = typeof props.href === 'string' ? props.href : undefined;
  const children = props.children as React.ReactNode;
  const target = classifyHref(href);
  if (target.kind === 'internal') {
    return <Link to={target.to}>{children}</Link>;
  }
  if (target.kind === 'external') {
    return (
      <a href={target.to} target="_blank" rel="noopener noreferrer">
        {children}
      </a>
    );
  }
  return <span className={styles.plainLink}>{children}</span>;
}
