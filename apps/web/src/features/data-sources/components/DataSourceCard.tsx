import { DatabaseOutlined, InfoCircleOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import type { DataSourceView } from '@merine/api-contract';
import { Button, Popover, Tag, Tooltip, Typography } from 'antd';
import type { ReactNode } from 'react';
import { Link } from 'react-router';
import collection from '../../../shared/ui/ResourceCollection.module.css';
import { DICTIONARY_CODES, DictTag, useDictionary } from '../../dictionaries/public';
import { formatCheckedAt, outcomeMeta } from '../model';
import styles from './DataSourceCard.module.css';

/** 连接卡片仅展示保存的配置与最近检查结论，不主动测试目标库。 */
export function DataSourceCard({
  item,
  scopeHref,
  actions,
}: {
  item: DataSourceView;
  scopeHref?: string;
  actions: ReactNode;
}) {
  const dictionary = useDictionary(DICTIONARY_CODES.status).data;
  const outcome = item.lastCheckAt ? outcomeMeta(item.lastCheckStatus) : undefined;
  const views = item.views ?? [];

  return (
    <article className={collection.card} aria-label={item.name}>
      <header className={collection.cardHeader}>
        <span className={collection.icon} aria-hidden="true">
          <DatabaseOutlined />
        </span>
        <div className={collection.identity}>
          <h3>{item.name || '未命名数据源'}</h3>
          <div className={collection.badges}>
            <span className={collection.secondary}>MySQL</span>
            <DictTag dictionary={dictionary} value={item.status ?? ''} />
          </div>
        </div>
        <Popover
          trigger="click"
          title="配置标识"
          content={
            <div className={styles.configInfo}>
              <span className={collection.secondary}>数据源 ID</span>
              <Typography.Text copyable={{ text: item.id ?? '', tooltips: ['复制 ID', '已复制'] }}>
                {item.id || '—'}
              </Typography.Text>
              <span className={collection.secondary}>配置版本 v{item.version ?? 0}</span>
            </div>
          }
        >
          <Button
            type="text"
            size="small"
            icon={<InfoCircleOutlined />}
            aria-label={`${item.name}的配置标识`}
          />
        </Popover>
      </header>
      <div className={styles.body}>
        {item.remark && (
          <p className={collection.remark} title={item.remark}>
            {item.remark}
          </p>
        )}
        <div className={styles.connection}>
          <span className={collection.secondary}>数据库</span>
          <strong className={styles.database}>{item.databaseName || '—'}</strong>
          <span className={collection.mono}>
            {item.host}:{item.port}
          </span>
          <dl className={styles.account}>
            <dt>连接账号</dt>
            <dd className={collection.mono}>{item.username || '—'}</dd>
          </dl>
        </div>
        <div className={styles.scopeRow}>
          <span className={collection.secondary}>查询视图</span>
          {scopeHref ? (
            <Link to={scopeHref} className={styles.scopeLink}>
              {views.length} 个视图 →
            </Link>
          ) : (
            <span>{views.length} 个视图</span>
          )}
        </div>
        <div className={styles.check}>
          <SafetyCertificateOutlined aria-hidden="true" />
          <div className={styles.checkContent}>
            <span className={styles.checkLabel}>最近只读检查</span>
            <div className={collection.badges}>
              {outcome ? (
                <Tooltip title={item.lastCheckMessage || outcome.hint}>
                  <Tag color={outcome.color} bordered={false}>
                    {outcome.label}
                  </Tag>
                </Tooltip>
              ) : (
                <span className={collection.secondary}>尚未检查</span>
              )}
              {outcome && (
                <span className={collection.secondary}>{formatCheckedAt(item.lastCheckAt)}</span>
              )}
            </div>
          </div>
        </div>
      </div>
      {actions && <footer className={collection.footer}>{actions}</footer>}
    </article>
  );
}
