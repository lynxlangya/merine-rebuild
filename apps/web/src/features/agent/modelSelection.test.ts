import assert from 'node:assert/strict';
import test from 'node:test';
import type { ModelOption } from '@merine/api-contract';
import { resolveComposerSelection } from './modelSelection.ts';

const options: ModelOption[] = [
  {
    id: 'a/flash',
    providerId: 'a',
    providerName: '连接甲',
    vendor: 'DEEPSEEK',
    modelId: 'flash',
    displayName: 'Flash',
    reasoningEfforts: ['NONE', 'LOW', 'HIGH', 'MAX'],
    defaultReasoningEffort: 'HIGH',
  },
  {
    id: 'b/flash',
    providerId: 'b',
    providerName: '连接乙',
    vendor: 'QWEN',
    modelId: 'flash',
    displayName: 'Flash',
    reasoningEfforts: ['LOW', 'MEDIUM', 'XHIGH'],
    defaultReasoningEffort: 'XHIGH',
  },
  {
    id: 'b/plain',
    providerId: 'b',
    providerName: '连接乙',
    vendor: 'QWEN',
    modelId: 'plain',
    displayName: 'Plain',
    reasoningEfforts: [],
  },
];

test('历史回填同时匹配连接和模型，保留关闭思考等合法档位', () => {
  const history = resolveComposerSelection(
    options,
    { providerId: 'b', modelId: 'flash', reasoningEffort: 'MEDIUM' },
    undefined,
    'HIGH',
  );
  assert.equal(history.model?.id, 'b/flash');
  assert.equal(history.effort, 'MEDIUM');
  assert.equal(
    resolveComposerSelection(
      options,
      { providerId: 'a', modelId: 'flash', reasoningEffort: 'NONE' },
      undefined,
      'HIGH',
    ).effort,
    'NONE',
  );
});

test('切换模型采用新模型默认强度，不带入历史档位或不支持的强度', () => {
  const previous = { providerId: 'a', modelId: 'flash', reasoningEffort: 'MAX' } as const;
  const switched = resolveComposerSelection(
    options,
    previous,
    { modelOptionId: 'b/flash' },
    'HIGH',
  );
  assert.equal(switched.model?.id, 'b/flash');
  assert.equal(switched.effort, 'XHIGH');
  assert.equal(
    resolveComposerSelection(
      options,
      previous,
      { modelOptionId: 'b/flash', reasoningEffort: 'MAX' },
      'HIGH',
    ).effort,
    'XHIGH',
  );
  assert.equal(
    resolveComposerSelection(
      options,
      previous,
      { modelOptionId: 'b/flash', reasoningEffort: 'LOW' },
      'HIGH',
    ).effort,
    'LOW',
  );
});

test('模型失效时丢弃其临时强度，恢复有效历史；无模型或无档位时不伪造参数', () => {
  const resolved = resolveComposerSelection(
    options,
    { providerId: 'b', modelId: 'flash', reasoningEffort: 'MEDIUM' },
    { modelOptionId: 'removed', reasoningEffort: 'MAX' },
    'HIGH',
  );
  assert.equal(resolved.model?.id, 'b/flash');
  assert.equal(resolved.effort, 'MEDIUM');
  assert.equal(
    resolveComposerSelection(
      options,
      undefined,
      { modelOptionId: 'b/plain', reasoningEffort: 'MAX' },
      'HIGH',
    ).effort,
    undefined,
  );
  assert.deepEqual(resolveComposerSelection([], undefined, undefined, 'HIGH'), {
    model: undefined,
    effort: undefined,
  });
});

test('默认值也须属于当前模型；未配置官方默认时使用已有兜底或第一档', () => {
  const invalidDefault = { ...options[1], defaultReasoningEffort: 'MAX' } as ModelOption;
  assert.equal(
    resolveComposerSelection([invalidDefault], undefined, undefined, 'HIGH').effort,
    'LOW',
  );
  assert.equal(
    resolveComposerSelection(
      [{ ...options[0], defaultReasoningEffort: undefined }],
      undefined,
      undefined,
      'HIGH',
    ).effort,
    'HIGH',
  );
});
