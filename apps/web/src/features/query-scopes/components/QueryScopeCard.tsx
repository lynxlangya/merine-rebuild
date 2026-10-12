import { ArrowRightOutlined, DatabaseOutlined, TableOutlined } from '@ant-design/icons';
import type { DataSourceView } from '@merine/api-contract';
import { Tag, Tooltip } from 'antd';
import { Link } from 'react-router';
import collection from '../../../shared/ui/ResourceCollection.module.css';
import { DICTIONARY_CODES, DictTag, useDictionary } from '../../dictionaries/public';
import { formatCheckedAt, outcomeMeta } from '../model';
import styles from './QueryScopeCard.module.css';

/** 将已保存白名单直接呈现为视图目录；打开详情后才按需探测目标库。 */
export function QueryScopeCard({ item, canUpdate }: { item: DataSourceView; canUpdate: boolean }) {
  const dictionary = useDictionary(DICTIONARY_CODES.status).data;
  const views = item.views ?? [];
  const href = `/agent/query-scopes/${encodeURIComponent(item.id ?? '')}`;
  const outcome = item.lastCheckAt ? outcomeMeta(item.lastCheckStatus) : undefined;

  return (
    <article className={collection.card} aria-label={`${item.name}的查询范围`}>
      <header className={collection.cardHeader}>
        <span className={collection.icon} aria-hidden="true">
          <TableOutlined />
        </span>
        <div className={collection.identity}>
          <h3>
            <Link to={href}>{item.name || '未命名数据源'}</Link>
          </h3>
          <div className={collection.badges}>
            <DictTag dictionary={dictionary} value={item.status ?? ''} />
            <span className={collection.secondary}>{views.length} 个允许视图</span>
          </div>
        </div>
      </header>
      <div className={styles.body}>
        {item.remark && (
          <p className={collection.remark} title={item.remark}>
            {item.remark}
          </p>
        )}
        <div className={styles.catalog}>
          <div className={styles.root}>
            <DatabaseOutlined aria-hidden="true" />
            <span className={collection.mono}>{item.databaseName || '数据库'}</span>
          </div>
          {views.length > 0 ? (
            <ul className={styles.views} aria-label="允许查询的视图">
              {views.slice(0, 4).map((view) => (
                <li key={view}>
                  <TableOutlined aria-hidden="true" />
                  <code>{view}</code>
                </li>
              ))}
              {views.length > 4 && (
                <li>
                  <Link to={href}>查看其余 {views.length - 4} 个视图</Link>
                </li>
              )}
            </ul>
          ) : (
            <div className={styles.noViews}>
              <TableOutlined aria-hidden="true" />
              <span>尚未设置查询范围</span>
              <Link to={href}>
                {canUpdate ? '选择允许查询的视图' : '查看范围详情'} <ArrowRightOutlined />
              </Link>
            </div>
          )}
        </div>
        <div className={styles.checkRow}>
          <span className={collection.secondary}>最近检查</span>
          {outcome ? (
            <Tooltip
              title={[
                formatCheckedAt(item.lastCheckAt),
                item.lastCheckMessage || outcome.hint,
              ].join(' · ')}
            >
              <Tag color={outcome.color} bordered={false}>
                {item.lastCheckStatus === 'PASSED' ? '只读检查通过' : outcome.label}
              </Tag>
            </Tooltip>
          ) : (
            <span className={collection.secondary}>尚未检查</span>
          )}
        </div>
      </div>
      <footer className={collection.footer}>
        <Tooltip title={`配置 v${item.version ?? 0} · 语义 v${item.semanticVersion ?? 0}`}>
          <span className={collection.secondary}>说明 v{item.semanticVersion ?? 0}</span>
        </Tooltip>
        <Link to={href} className={styles.open}>
          {canUpdate ? '维护范围与说明' : '查看范围与说明'}{' '}
          <ArrowRightOutlined aria-hidden="true" />
        </Link>
      </footer>
    </article>
  );
}
