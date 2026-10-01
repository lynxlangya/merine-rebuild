import assert from 'node:assert/strict';
import { test } from 'node:test';
import { ApiError } from './http.ts';
import { submissionIntent, submissionUncertain } from './idempotentIntent.ts';

test('研判提交意图保留原输入及请求键，明确修改才产生新意图', () => {
  const input = { analysis: '已核对的依据', recommendationCode: 'VERIFY' };
  const first = submissionIntent(null, input);
  assert.equal(submissionIntent(first, { ...input }), first);
  assert.notEqual(submissionIntent(first, { ...input, analysis: '新依据' }).key, first.key);
  first.uncertain = true;
  assert.equal(submissionIntent(first, { ...input }).input, input);
  assert.throws(() => submissionIntent(first, { ...input, analysis: '新依据' }), /重试/);
});

test('业务拒绝允许修正，响应丢失、服务错误和处理中保留原请求', () => {
  assert.equal(submissionUncertain(new ApiError('来源更新', 409, 'SOURCE_CHANGED')), false);
  assert.equal(submissionUncertain(new ApiError('缺少权限', 403, 'FORBIDDEN')), false);
  for (const error of [
    new ApiError('网络', 0),
    new ApiError('故障', 503),
    new ApiError('非确定的服务响应', 500, 'SOURCE_CHANGED'),
    new ApiError('处理中', 409, 'IDEMPOTENCY_PENDING'),
    new SyntaxError('响应无效'),
  ]) {
    assert.equal(submissionUncertain(error), true);
  }
});

test('未知结果重放确认未落单后允许核对来源，权限拒绝仍保留原意图', () => {
  const input = {
    backgroundSummary: '明确共享摘要',
    sourceSupplementCheckpoint: undefined as string | undefined,
  };
  const intent = submissionIntent(null, input);
  intent.uncertain = submissionUncertain(new ApiError('响应丢失', 0));
  assert.equal(
    submissionUncertain(new ApiError('权限变化', 403, 'FORBIDDEN'), intent.uncertain),
    true,
  );
  intent.uncertain = submissionUncertain(
    new ApiError('来源更新', 409, 'SOURCE_CHANGED'),
    intent.uncertain,
  );
  assert.equal(intent.uncertain, false);
  assert.equal(submissionIntent(intent, { ...input }).key, intent.key);
  const reviewed = submissionIntent(intent, { ...input, sourceSupplementCheckpoint: '6' });
  assert.notEqual(reviewed.key, intent.key);
  assert.equal(reviewed.input.backgroundSummary, input.backgroundSummary);
});
