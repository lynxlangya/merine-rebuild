import { InfoCircleOutlined, NumberOutlined, ReadOutlined } from '@ant-design/icons';
import type { BusinessConcept, MetricDefinition } from '@merine/api-contract';
import styles from '../AgentLibraryPage.module.css';

export function ConceptCard({ concept }: { concept: BusinessConcept }) {
  const headingId = `definition-${concept.id}`;
  return (
    <article className={styles.conceptCard} aria-labelledby={headingId}>
      <div className={styles.cardHead}>
        <span className={styles.conceptIcon} aria-hidden="true">
          <ReadOutlined />
        </span>
        <span className={styles.revision}>版本 {concept.revision}</span>
      </div>
      <h4 id={headingId} className={styles.cardTitle}>
        {concept.title}
      </h4>
      <p className={styles.definition}>{concept.definition}</p>
      <ul className={styles.rules}>
        {concept.rules.map((rule, index) => (
          <li key={index}>{rule}</li>
        ))}
      </ul>
      <div className={styles.example}>
        <span className={styles.exampleLabel}>理解示例</span>
        <p>{concept.example}</p>
      </div>
      <div className={styles.identifier}>定义编号：{concept.id}</div>
    </article>
  );
}

export function MetricCard({ metric }: { metric: MetricDefinition }) {
  const headingId = `definition-${metric.id}`;
  const fields = [
    { label: '统计对象', value: metric.countingObject },
    { label: '计入条件', value: metric.inclusionRule },
    { label: '时间口径', value: metric.timeBasis },
    { label: '统计范围', value: metric.scopeRule },
    { label: '去重规则', value: metric.deduplicationRule },
    { label: '计量单位', value: metric.unit },
  ];
  return (
    <article className={styles.metricCard} aria-labelledby={headingId}>
      <div className={styles.metricHead}>
        <div className={styles.metricHeading}>
          <span className={styles.metricIcon} aria-hidden="true">
            <NumberOutlined />
          </span>
          <div>
            <h4 id={headingId} className={styles.cardTitle}>
              {metric.title}
            </h4>
            <p className={styles.definition}>{metric.definition}</p>
          </div>
        </div>
        <div className={styles.badges}>
          <span
            className={`${styles.status} ${metric.status === 'DRAFT' ? styles.draft : styles.confirmed}`}
          >
            {metric.statusLabel}
          </span>
          <span className={styles.revision}>版本 {metric.revision}</span>
        </div>
      </div>
      {metric.aliases.length > 0 && (
        <p className={styles.aliases}>常见叫法：{metric.aliases.join('、')}</p>
      )}
      <dl className={styles.metricFields}>
        {fields.map((field) => (
          <div key={field.label} className={styles.metricField}>
            <dt>{field.label}</dt>
            <dd>{field.value}</dd>
          </div>
        ))}
      </dl>
      {metric.pendingQuestions.length > 0 && (
        <aside className={styles.pending} aria-label="待确认的业务问题">
          <div className={styles.pendingTitle}>
            <InfoCircleOutlined aria-hidden="true" /> 待确认的业务问题
          </div>
          <ul>
            {metric.pendingQuestions.map((question, index) => (
              <li key={index}>{question}</li>
            ))}
          </ul>
        </aside>
      )}
      <details className={styles.metricExamples}>
        <summary>查看理解示例</summary>
        <div className={styles.exampleGrid}>
          {metric.examples.map((example, index) => (
            <div key={index} className={styles.example}>
              <span className={styles.exampleLabel}>{example.question}</span>
              <p>{example.explanation}</p>
            </div>
          ))}
        </div>
      </details>
      <div className={styles.identifier}>定义编号：{metric.id}</div>
    </article>
  );
}
