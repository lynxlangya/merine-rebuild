import type { IntelligenceDetail, IntelligenceTaskContext } from '@merine/api-contract';

type SourceData = Pick<IntelligenceDetail, 'supplements'>;
type ContextData = Pick<IntelligenceTaskContext, 'canCreate'>;
type LoadedQuery<T> = { data?: T; isSuccess: boolean; isFetching: boolean };

export type TaskSourceReview = { checkpoint?: string; required: boolean };

function supplementCheckpoint(supplements: SourceData['supplements']) {
  return supplements.reduce<string | undefined>(
    (latest, supplement) =>
      latest === undefined || BigInt(supplement.id) > BigInt(latest) ? supplement.id : latest,
    undefined,
  );
}

export function createTaskSourceReview(supplements: SourceData['supplements']): TaskSourceReview {
  return { checkpoint: supplementCheckpoint(supplements), required: false };
}

export function canConfirmTaskSource(
  context: LoadedQuery<ContextData>,
  source: LoadedQuery<SourceData>,
) {
  return (
    context.isSuccess &&
    !!context.data &&
    !context.isFetching &&
    source.isSuccess &&
    !!source.data &&
    !source.isFetching
  );
}

export function confirmTaskSourceReview(
  review: TaskSourceReview,
  context: LoadedQuery<ContextData>,
  source: LoadedQuery<SourceData>,
): TaskSourceReview {
  if (!review.required || !canConfirmTaskSource(context, source)) return review;
  return createTaskSourceReview(source.data!.supplements);
}

export function canSubmitIntelligenceTask(
  review: TaskSourceReview,
  context: LoadedQuery<ContextData>,
) {
  return !review.required && context.isSuccess && !context.isFetching && !!context.data?.canCreate;
}
