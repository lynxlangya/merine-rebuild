import { Link } from 'react-router';
import type { MessagePart } from '@merine/api-contract';
import { useAgentSourceResolver } from '../AgentChatContext';
import styles from './AgentParts.module.css';

/**
 * 来源部件：服务端只下发可见来源；站内跳转由 app 注入的解析器完成，
 * 未注册、缺少标识或非法外链都退化为不可点文本。
 */
export function SourcesPart({
  items,
}: {
  items: Extract<MessagePart, { type: 'SOURCES' }>['items'];
}) {
  const resolve = useAgentSourceResolver();
  const sources = (items ?? []).filter((item) => item && typeof item === 'object');
  if (sources.length === 0) return null;
  return (
    <div className={styles.sources}>
      <div className={styles.partTitle}>参考来源</div>
      <ul>
        {sources.map((item, index) => {
          const title = item.title ?? '未命名来源';
          const target = item.kind ? resolve(item) : undefined;
          return (
            <li
              key={`${item.kind ?? 'UNKNOWN'}-${item.refId ?? item.routeKey ?? item.url ?? index}`}
            >
              {!target ? (
                <span className={styles.plainLink}>
                  {title}
                  <span className={styles.sourceHint}>（不可跳转）</span>
                </span>
              ) : target.kind === 'internal' ? (
                <Link className={styles.sourceLink} to={target.to}>
                  {title}
                </Link>
              ) : (
                <a
                  className={styles.sourceLink}
                  href={target.to}
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  {title}
                </a>
              )}
              {item.snippet && <span className={styles.sourceSnippet}>{item.snippet}</span>}
            </li>
          );
        })}
      </ul>
    </div>
  );
}
