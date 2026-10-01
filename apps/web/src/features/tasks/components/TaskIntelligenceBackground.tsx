import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import { Alert, Button, Collapse, Typography } from 'antd';
import { useAuth } from '../../auth/public';
import { fetchTaskIntelligenceSource, taskIntelKeys } from '../intelligenceApi';
import styles from '../TaskDetailView.module.css';
import { DICTIONARY_CODES, dictLabel, useDictionary } from '../../dictionaries/public';
import { formatTaskTime } from '../taskTime';

export function TaskIntelligenceBackground({ taskId }: { taskId: string }) {
  const { state } = useAuth();
  const userId = state.status === 'authenticated' ? state.user.id : undefined;
  const recommendations = useDictionary(DICTIONARY_CODES.assessmentRecommendation);
  const query = useQuery({
    queryKey: [...taskIntelKeys.root(userId), taskId, 'source'],
    queryFn: ({ signal }) => fetchTaskIntelligenceSource(taskId, signal),
  });
  if (query.error)
    return (
      <Alert
        type="error"
        title="任务背景加载失败"
        action={<Button onClick={() => void query.refetch()}>重试</Button>}
      />
    );
  if (!query.data?.linked) return null;
  const source = query.data;
  return (
    <section className={styles.card}>
      <h2>任务背景</h2>
      <Typography.Paragraph
        style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere', margin: 0 }}
        ellipsis={{ rows: 4, expandable: true, symbol: '展开' }}
      >
        {source.backgroundSummary}
      </Typography.Paragraph>
      {source.topicId ? (
        <Link to={`/collaboration/flows/${source.topicId}`}>查看来源情报 · {source.topicNo}</Link>
      ) : (
        <span className={styles.muted}>来源情报当前不可访问</span>
      )}
      {source.sourceChanged && <Alert type="warning" title="来源情报有新说明，请核对最新内容。" />}
      {source.adoptedAssessment && (
        <Collapse
          ghost
          items={[
            {
              key: 'adopted',
              label: '查看采用的本单位研判',
              children: (
                <>
                  <Typography.Text type="secondary">
                    {dictLabel(recommendations.data, source.adoptedAssessment.recommendationCode)} ·{' '}
                    {source.adoptedAssessment.userName} ·{' '}
                    {formatTaskTime(source.adoptedAssessment.createdAt)}
                  </Typography.Text>
                  <p style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>
                    {source.adoptedAssessment.analysis}
                  </p>
                </>
              ),
            },
          ]}
        />
      )}
    </section>
  );
}
