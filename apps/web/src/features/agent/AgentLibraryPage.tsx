import { ReloadOutlined } from '@ant-design/icons';
import { Alert, Button, Empty, Result, Skeleton } from 'antd';
import { errorText, isForbiddenError } from '../../shared/api-error';
import { ConceptCard, MetricCard } from './components/SemanticCards';
import { useSemanticCatalogQuery } from './semanticsQueries';
import styles from './AgentLibraryPage.module.css';

export function AgentLibraryPage() {
  const { allowed, query } = useSemanticCatalogQuery();
  if (!allowed || isForbiddenError(query.error)) {
    return (
      <Result
        status="403"
        title="没有查看资料库的权限"
        subTitle="请联系管理员授予海防助手的使用权限。"
      />
    );
  }
  const catalog = query.data;
  return (
    <div className={styles.page}>
      <div className={styles.content}>
        <header className={styles.header}>
          <div>
            <span className={styles.eyebrow}>海防助手 · 业务定义</span>
            <h1 className={styles.title}>资料库</h1>
            <p className={styles.lead}>理解业务概念，核对指标口径。</p>
          </div>
          <div className={styles.headerActions}>
            {catalog && <span className={styles.revision}>目录版本 {catalog.revision}</span>}
            <Button
              icon={<ReloadOutlined />}
              loading={query.isFetching}
              onClick={() => void query.refetch()}
            >
              刷新
            </Button>
          </div>
        </header>
        <p className={styles.notice}>
          这里展示业务说明与指标定义，当前仅供查看和核对，不提供数据查询。
        </p>
        {query.isError && (
          <Alert
            type="error"
            showIcon
            title={catalog ? '刷新失败，以下为上一次读取的定义' : '业务定义加载失败'}
            description={errorText(query.error)}
          />
        )}
        {query.isPending && (
          <div role="status" aria-label="正在加载业务定义">
            <Skeleton active paragraph={{ rows: 8 }} />
          </div>
        )}
        {catalog?.topics.length === 0 && <Empty description="暂无业务定义" />}
        {catalog?.topics.map((topic) => (
          <section key={topic.id} className={styles.topic} aria-labelledby={`topic-${topic.id}`}>
            <header className={styles.topicHeader}>
              <h2 id={`topic-${topic.id}`}>{topic.title}</h2>
              <p>{topic.description}</p>
            </header>
            {topic.concepts.length > 0 && (
              <section aria-labelledby={`concepts-${topic.id}`}>
                <h3 id={`concepts-${topic.id}`} className={styles.sectionTitle}>
                  业务概念
                </h3>
                <div className={styles.conceptGrid}>
                  {topic.concepts.map((concept) => (
                    <ConceptCard key={concept.id} concept={concept} />
                  ))}
                </div>
              </section>
            )}
            {topic.metrics.length > 0 && (
              <section aria-labelledby={`metrics-${topic.id}`}>
                <h3 id={`metrics-${topic.id}`} className={styles.sectionTitle}>
                  指标定义
                </h3>
                <div className={styles.metricList}>
                  {topic.metrics.map((metric) => (
                    <MetricCard key={metric.id} metric={metric} />
                  ))}
                </div>
              </section>
            )}
          </section>
        ))}
      </div>
    </div>
  );
}
