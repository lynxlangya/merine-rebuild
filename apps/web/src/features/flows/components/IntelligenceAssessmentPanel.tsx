import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router';
import { Alert, App, Button, Collapse, Space, Spin, Table, Typography } from 'antd';
import type { IntelligenceDetail, LinkedIntelligenceTask } from '@merine/api-contract';
import { useAuth } from '../../auth/public';
import { PERMISSIONS, hasPermission, type PermissionCode } from '../../../shared/permissions';
import { TablePager } from '../../../shared/ui/TablePager';
import { RecordTitleLink } from '../../../shared/ui/RecordTitleLink';
import tableStyles from '../../../shared/ui/RecordTable.module.css';
import { DICTIONARY_CODES, dictLabel, useDictionary } from '../../dictionaries/public';
import { assessmentKeys, fetchAssessmentContext, fetchAssessments } from '../assessmentApi';
import {
  taskIntelKeys,
  fetchIntelligenceTaskContext,
  fetchLinkedTasks,
  TaskStatusTag,
  TaskTime,
} from '../../tasks/public';
import { flowKeys } from '../queries';
import { formatFlowTime } from '../model';
import { AssessmentDialog } from './AssessmentDialog';
import { IntelligenceTaskDialog } from './IntelligenceTaskDialog';
import styles from '../InformationFlowPage.module.css';

export function IntelligenceAssessmentPanel({
  detail,
  busy,
}: {
  detail: IntelligenceDetail;
  busy: boolean;
}) {
  const { state } = useAuth();
  const user = state.status === 'authenticated' ? state.user : undefined;
  const can = (code: PermissionCode) => hasPermission(user?.permissionCodes, code);
  const navigate = useNavigate(),
    cache = useQueryClient();
  const { message } = App.useApp();
  const [dialog, setDialog] = useState<'assess' | 'task'>(),
    [historyPage, setHistoryPage] = useState(1),
    [taskPage, setTaskPage] = useState(1);
  const dictionary = useDictionary(DICTIONARY_CODES.assessmentRecommendation);
  const context = useQuery({
    queryKey: assessmentKeys.context(user?.id, detail.id),
    queryFn: ({ signal }) => fetchAssessmentContext(detail.id, signal),
  });
  const latest = useQuery({
    queryKey: assessmentKeys.list(user?.id, detail.id, 1),
    queryFn: ({ signal }) => fetchAssessments(detail.id, 1, signal),
  });
  const history = useQuery({
    queryKey: assessmentKeys.list(user?.id, detail.id, historyPage),
    queryFn: ({ signal }) => fetchAssessments(detail.id, historyPage, signal),
  });
  const taskContext = useQuery({
    queryKey: [...taskIntelKeys.root(user?.id), detail.id, 'context'],
    queryFn: ({ signal }) => fetchIntelligenceTaskContext(detail.id, signal),
    enabled:
      can(PERMISSIONS.intelCreateTask) && can(PERMISSIONS.taskCreate) && can(PERMISSIONS.taskRead),
  });
  const tasks = useQuery({
    queryKey: [...taskIntelKeys.root(user?.id), detail.id, 'list', taskPage],
    queryFn: ({ signal }) => fetchLinkedTasks(detail.id, taskPage, signal),
    enabled: can(PERMISSIONS.taskRead),
  });
  const saved = () => {
    setDialog(undefined);
    message.success('研判已保存');
    void cache.invalidateQueries({ queryKey: assessmentKeys.root(user?.id) });
    void cache.invalidateQueries({ queryKey: taskIntelKeys.root(user?.id) });
  };
  const top = latest.data?.items[0];
  return (
    <>
      <section className={styles.panel}>
        <div className={styles.sectionHead}>
          <h2>本单位研判</h2>
          <Space wrap>
            {can(PERMISSIONS.intelAssess) && (
              <Button
                disabled={busy || !context.data?.canAssess}
                title={context.data?.reason ?? undefined}
                onClick={() => setDialog('assess')}
              >
                记录研判
              </Button>
            )}
            {!!taskContext.data?.targets.length && (
              <Button
                type="primary"
                disabled={busy || !taskContext.data.canCreate}
                title={taskContext.data.reason ?? undefined}
                onClick={() => setDialog('task')}
              >
                发起任务
              </Button>
            )}
          </Space>
        </div>
        {context.error && (
          <Alert
            type="error"
            title="研判资格加载失败"
            action={<Button onClick={() => void context.refetch()}>重试</Button>}
          />
        )}
        {taskContext.error && (
          <Alert
            type="error"
            title="任务创建资格加载失败"
            action={<Button onClick={() => void taskContext.refetch()}>重试</Button>}
          />
        )}
        {can(PERMISSIONS.intelAssess) && context.data?.reason && (
          <p className={styles.meta}>记录研判：{context.data.reason}</p>
        )}
        {taskContext.data?.targets.length && taskContext.data.reason ? (
          <p className={styles.meta}>发起任务：{taskContext.data.reason}</p>
        ) : null}
        {latest.isPending ? (
          <Spin />
        ) : latest.error ? (
          <Alert
            type="error"
            title="研判加载失败"
            action={<Button onClick={() => void latest.refetch()}>重试</Button>}
          />
        ) : top ? (
          <>
            <div className={styles.meta}>
              {dictLabel(dictionary.data, top.recommendationCode)} · {top.userName} ·{' '}
              {formatFlowTime(top.createdAt)}
            </div>
            <Typography.Paragraph
              className={styles.body}
              ellipsis={{ rows: 4, expandable: true, symbol: '展开' }}
            >
              {top.analysis}
            </Typography.Paragraph>
          </>
        ) : (
          <p className={styles.meta}>尚无本单位研判。</p>
        )}
        {(latest.data?.total ?? 0) > 1 && (
          <Collapse
            ghost
            items={[
              {
                key: 'history',
                label: '查看研判历史',
                children: (
                  <>
                    {history.error ? (
                      <Alert
                        type="error"
                        title="历史加载失败"
                        action={<Button onClick={() => void history.refetch()}>重试</Button>}
                      />
                    ) : (
                      history.data?.items.map((a) => (
                        <article className={styles.feedback} key={a.id}>
                          <div className={styles.meta}>
                            {dictLabel(dictionary.data, a.recommendationCode)} · {a.userName} ·{' '}
                            {formatFlowTime(a.createdAt)}
                          </div>
                          <p className={styles.body}>{a.analysis}</p>
                        </article>
                      ))
                    )}
                    {!history.error && history.data && (
                      <TablePager
                        total={history.data.total}
                        page={historyPage}
                        pageSize={20}
                        itemCount={history.data?.items.length ?? 0}
                        onPageChange={setHistoryPage}
                      />
                    )}
                  </>
                ),
              },
            ]}
          />
        )}
      </section>
      {can(PERMISSIONS.taskRead) && (
        <section className={tableStyles.frame}>
          <div className={tableStyles.toolbar}>
            <h2>关联任务</h2>
            <Button loading={tasks.isFetching} onClick={() => void tasks.refetch()}>
              刷新
            </Button>
          </div>
          {tasks.error ? (
            <Alert
              type="error"
              title="关联任务加载失败"
              action={<Button onClick={() => void tasks.refetch()}>重试</Button>}
            />
          ) : tasks.isPending ? (
            <Spin />
          ) : tasks.data?.total === 0 ? (
            <p className={styles.meta} style={{ padding: '0 16px 12px' }}>
              暂无可查看的关联任务。
            </p>
          ) : (
            <>
              <Table<LinkedIntelligenceTask>
                rowKey="id"
                size="small"
                tableLayout="fixed"
                scroll={{ x: 850 }}
                loading={tasks.isFetching}
                dataSource={tasks.data?.items ?? []}
                pagination={false}
                columns={[
                  {
                    title: '任务',
                    key: 'task',
                    width: 350,
                    render: (_, t) => (
                      <RecordTitleLink to={`/collaboration/tasks/${t.id}`} number={t.taskNo}>
                        {t.title}
                      </RecordTitleLink>
                    ),
                  },
                  {
                    title: '状态',
                    key: 'status',
                    width: 90,
                    render: (_, t) => <TaskStatusTag status={t.status} />,
                  },
                  { title: '发起单位', dataIndex: 'issuerUnitName', width: 230 },
                  {
                    title: '办理期限',
                    key: 'due',
                    width: 180,
                    render: (_, t) => <TaskTime value={t.dueAt} />,
                  },
                ]}
              />
              <TablePager
                total={tasks.data?.total ?? 0}
                page={taskPage}
                pageSize={20}
                itemCount={tasks.data?.items.length ?? 0}
                onPageChange={setTaskPage}
              />
            </>
          )}
        </section>
      )}
      {dialog === 'assess' && context.data && (
        <AssessmentDialog
          topicId={detail.id}
          context={context.data}
          onClose={() => setDialog(undefined)}
          onSaved={saved}
        />
      )}
      {dialog === 'task' && (
        <IntelligenceTaskDialog
          topicId={detail.id}
          source={detail}
          onClose={() => setDialog(undefined)}
          onCreated={(id) => {
            setDialog(undefined);
            void cache.invalidateQueries({ queryKey: flowKeys.detail(user?.id, detail.id) });
            void cache.invalidateQueries({ queryKey: assessmentKeys.root(user?.id) });
            void cache.invalidateQueries({ queryKey: taskIntelKeys.root(user?.id) });
            message.success('任务已创建并下发');
            navigate(`/collaboration/tasks/${id}`);
          }}
        />
      )}
    </>
  );
}
