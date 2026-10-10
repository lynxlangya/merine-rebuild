import assert from 'node:assert/strict';
import test from 'node:test';
import type { DiscoveredProviderModel } from '@merine/api-contract';
import {
  effortsLabel,
  existingModelIds,
  newModelsFrom,
  normalizeEfforts,
  type ModelInput,
} from './discovery.ts';

const existing: ModelInput[] = [
  {
    modelId: 'deepseek-flash',
    displayName: 'Flash',
    remark: '保留原配置',
    reasoningEfforts: ['LOW'],
    status: 'ENABLED',
  },
];

function found(
  modelId: string,
  reasoningEfforts: DiscoveredProviderModel['reasoningEfforts'] = [],
) {
  return { modelId, reasoningEfforts };
}

test('already-imported ids are recognised and skipped', () => {
  assert.deepEqual([...existingModelIds(existing)], ['deepseek-flash']);
  const added = newModelsFrom(
    existing,
    [
      found('deepseek-flash'),
      {
        modelId: 'deepseek-v4-pro',
        ownedBy: 'deepseek',
        reasoningEfforts: ['NONE', 'LOW', 'HIGH', 'MAX'],
      },
    ],
    20,
  );
  assert.deepEqual(
    added.map((model) => model.modelId),
    ['deepseek-v4-pro'],
  );
  assert.equal(added[0].displayName, '');
  assert.equal(added[0].remark, '');
  assert.equal(added[0].status, 'ENABLED');
});

test('efforts come from the server directory per model, in interface order', () => {
  const added = newModelsFrom(
    [],
    [
      // 千问 Qwen3.8 系列官方取值，服务端可能按其它顺序返回
      { modelId: 'qwen3.8-max', reasoningEfforts: ['XHIGH', 'LOW', 'MEDIUM'] },
      // 目录没收录的模型：不设置档位
      found('qwen3.7-flash'),
    ],
    20,
  );
  assert.deepEqual(added[0].reasoningEfforts, ['LOW', 'MEDIUM', 'XHIGH']);
  assert.deepEqual(added[1].reasoningEfforts, []);
  assert.equal(added[0].modelId, 'qwen3.8-max');
});

test('effort normalisation drops unknown values and labels stay readable', () => {
  assert.deepEqual(normalizeEfforts(['MAX', 'HIGH', 'NONE', 'LOW', 'LOW', 'TURBO' as never]), [
    'NONE',
    'LOW',
    'HIGH',
    'MAX',
  ]);
  assert.equal(normalizeEfforts(undefined).length, 0);
  assert.equal(effortsLabel(['XHIGH', 'LOW', 'MEDIUM']), '低 / 中 / 极高');
  assert.equal(effortsLabel([]), '不设置');
});

test('import stops at the remaining room of the model list', () => {
  const discovered = Array.from({ length: 5 }, (_, index) => found(`model-${index}`, ['HIGH']));
  const added = newModelsFrom([], discovered, 2);
  assert.deepEqual(
    added.map((model) => model.modelId),
    ['model-0', 'model-1'],
  );
  assert.deepEqual(newModelsFrom([], discovered, 0), []);
});
