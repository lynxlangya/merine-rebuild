import assert from 'node:assert/strict';
import { test, type TestContext } from 'node:test';
import { QueryClient, QueryObserver } from '@tanstack/react-query';
import type {
  CreateIntelligenceTask,
  IntelligenceDetail,
  IntelligenceTaskContext,
} from '@merine/api-contract';
import { ApiError } from '../../shared/http.ts';
import { submissionIntent, submissionUncertain } from '../../shared/idempotentIntent.ts';
import {
  canConfirmTaskSource,
  canSubmitIntelligenceTask,
  confirmTaskSourceReview,
  createTaskSourceReview,
} from './taskSourceReview.ts';

type Source = Pick<IntelligenceDetail, 'supplements'>;
type Context = Pick<IntelligenceTaskContext, 'canCreate' | 'sourceSupplementCheckpoint'>;

function sourceWith(...ids: string[]): Source {
  return {
    supplements: ids.map((id) => ({
      id,
      kind: 'SUPPLEMENT',
      body: `说明 ${id}`,
      unitName: '合成源头单位',
      userName: '合成人员',
      createdAt: '2026-10-01T08:00:00Z',
    })),
  };
}

function queries(t: TestContext) {
  const cache = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: Infinity } },
  });
  const initialSource = sourceWith('10');
  const initialContext: Context = { canCreate: true, sourceSupplementCheckpoint: '10' };
  let loadSource = async () => initialSource;
  let loadContext = async () => initialContext;
  cache.setQueryData(['source'], initialSource);
  cache.setQueryData(['context'], initialContext);
  const source = new QueryObserver(cache, {
    queryKey: ['source'],
    queryFn: () => loadSource(),
    enabled: false,
  });
  const context = new QueryObserver(cache, {
    queryKey: ['context'],
    queryFn: () => loadContext(),
    enabled: false,
  });
  const stopSource = source.subscribe(() => {});
  const stopContext = context.subscribe(() => {});
  t.after(() => {
    stopSource();
    stopContext();
    cache.clear();
  });
  return {
    source,
    context,
    setSourceLoader: (load: () => Promise<Source>) => (loadSource = load),
    setContextLoader: (load: () => Promise<Context>) => (loadContext = load),
  };
}

test('初始检查点来自已展示说明，资格自动刷新不替换它', async (t) => {
  const q = queries(t);
  const review = createTaskSourceReview(
    sourceWith('9007199254740993', '9007199254740992').supplements,
  );
  assert.equal(review.checkpoint, '9007199254740993');
  assert.equal(createTaskSourceReview([]).checkpoint, undefined);

  q.setContextLoader(async () => ({
    canCreate: true,
    sourceSupplementCheckpoint: '9007199254740994',
  }));
  await q.context.refetch();
  assert.equal(canSubmitIntelligenceTask(review, q.context.getCurrentResult()), true);
  assert.equal(review.checkpoint, '9007199254740993');
  assert.equal(
    confirmTaskSourceReview(review, q.context.getCurrentResult(), q.source.getCurrentResult()),
    review,
  );
});

test('再次冲突后来源刷新失败保留旧缓存，但不能确认或提交', async (t) => {
  const q = queries(t);
  let review = { ...createTaskSourceReview(sourceWith('10').supplements), required: true };
  q.setSourceLoader(async () => sourceWith('10', '11'));
  q.setContextLoader(async () => ({ canCreate: true, sourceSupplementCheckpoint: '11' }));
  await Promise.all([q.source.refetch(), q.context.refetch()]);
  review = confirmTaskSourceReview(
    review,
    q.context.getCurrentResult(),
    q.source.getCurrentResult(),
  );
  assert.equal(review.checkpoint, '11');
  assert.equal(review.required, false);

  review = { ...review, required: true };
  q.setSourceLoader(async () => {
    throw new Error('说明刷新失败');
  });
  q.setContextLoader(async () => ({ canCreate: true, sourceSupplementCheckpoint: '12' }));
  await Promise.all([q.source.refetch(), q.context.refetch()]);
  const staleSource = q.source.getCurrentResult();
  const refreshedContext = q.context.getCurrentResult();
  assert.equal(staleSource.isError, true);
  assert.deepEqual(staleSource.data, sourceWith('10', '11'));
  assert.equal(refreshedContext.data?.sourceSupplementCheckpoint, '12');
  assert.equal(canConfirmTaskSource(refreshedContext, staleSource), false);
  assert.equal(confirmTaskSourceReview(review, refreshedContext, staleSource), review);
  assert.equal(canSubmitIntelligenceTask(review, refreshedContext), false);

  q.setSourceLoader(async () => sourceWith('10', '11', '12'));
  await q.source.refetch();
  q.setContextLoader(async () => ({ canCreate: true, sourceSupplementCheckpoint: '13' }));
  await q.context.refetch();
  const confirmed = confirmTaskSourceReview(
    review,
    q.context.getCurrentResult(),
    q.source.getCurrentResult(),
  );
  assert.equal(confirmed.checkpoint, '12');
  assert.equal(confirmed.required, false);
  assert.equal(canSubmitIntelligenceTask(confirmed, q.context.getCurrentResult()), true);
});

test('来源或资格正在刷新及资格刷新失败时不能绕过核对', async (t) => {
  const q = queries(t);
  const accepted = createTaskSourceReview(sourceWith('10').supplements);
  const review = { ...accepted, required: true };
  let resolveSource!: (value: Source) => void;
  q.setSourceLoader(() => new Promise<Source>((resolve) => (resolveSource = resolve)));
  const refreshing = q.source.refetch();
  assert.equal(q.source.getCurrentResult().isFetching, true);
  assert.equal(q.source.getCurrentResult().isSuccess, true);
  assert.equal(
    canConfirmTaskSource(q.context.getCurrentResult(), q.source.getCurrentResult()),
    false,
  );
  assert.equal(
    confirmTaskSourceReview(review, q.context.getCurrentResult(), q.source.getCurrentResult()),
    review,
  );
  resolveSource(sourceWith('10', '11'));
  await refreshing;

  let rejectContext!: (reason: Error) => void;
  q.setContextLoader(() => new Promise<Context>((_, reject) => (rejectContext = reject)));
  const contextRefresh = q.context.refetch();
  assert.equal(
    canConfirmTaskSource(q.context.getCurrentResult(), q.source.getCurrentResult()),
    false,
  );
  assert.equal(canSubmitIntelligenceTask(accepted, q.context.getCurrentResult()), false);
  rejectContext(new Error('资格刷新失败'));
  await contextRefresh;
  assert.equal(q.context.getCurrentResult().data?.canCreate, true);
  assert.equal(
    canConfirmTaskSource(q.context.getCurrentResult(), q.source.getCurrentResult()),
    false,
  );
  assert.equal(canSubmitIntelligenceTask(accepted, q.context.getCurrentResult()), false);
});

test('核对新版本不会改写结果不明时保留的原请求与幂等键', async (t) => {
  const q = queries(t);
  const review = createTaskSourceReview(sourceWith('10').supplements);
  const input: CreateIntelligenceTask = {
    title: '合成核查任务',
    instruction: '核查登记时间',
    expectedResult: '核查结论',
    backgroundSummary: '保留的办理背景',
    dueAt: '2026-10-02T08:00:00Z',
    targetUnitCodes: ['330300'],
    assessmentId: '9',
    sourceSupplementCheckpoint: review.checkpoint,
  };
  const pending = submissionIntent(null, input);
  pending.uncertain = submissionUncertain(new ApiError('响应丢失', 0));
  q.setContextLoader(async () => ({ canCreate: true, sourceSupplementCheckpoint: '11' }));
  await q.context.refetch();
  assert.equal(submissionIntent(pending, input), pending);
  assert.equal(pending.input.sourceSupplementCheckpoint, '10');

  pending.uncertain = submissionUncertain(new ApiError('来源更新', 409, 'SOURCE_CHANGED'), true);
  q.setSourceLoader(async () => sourceWith('10', '11'));
  await q.source.refetch();
  const confirmed = confirmTaskSourceReview(
    { ...review, required: true },
    q.context.getCurrentResult(),
    q.source.getCurrentResult(),
  );
  const next = submissionIntent(pending, {
    ...input,
    sourceSupplementCheckpoint: confirmed.checkpoint,
  });
  assert.notEqual(next.key, pending.key);
  assert.equal(next.input.backgroundSummary, input.backgroundSummary);
  assert.equal(next.input.sourceSupplementCheckpoint, '11');
  assert.equal(pending.input.sourceSupplementCheckpoint, '10');
});
