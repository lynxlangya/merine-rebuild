import assert from 'node:assert/strict';
import test from 'node:test';
import { metadataChanged, metadataForm, scopeCandidates } from './model.ts';

test('切换视图使用各自已保存说明，不把上一个视图的空值当成草稿', () => {
  const first = {
    view: 'v_a',
    displayName: '任务统计',
    description: '每条任务一行',
    columns: [{ name: 'id', description: '任务编号' }],
  };
  const second = {
    view: 'v_b',
    displayName: '信息统计',
    description: '每条信息一行',
    columns: [{ name: 'id', description: '信息编号' }],
  };
  assert.equal(metadataForm(first).overrides.id, '任务编号');
  assert.equal(metadataForm(second).description, '每条信息一行');
  assert.equal(metadataForm(second).overrides.id, '信息编号');
  assert.equal(metadataChanged(metadataForm(second), second), false);
});

test('说明刷新采用新基线，输入草稿和清空覆盖值仍识别为修改', () => {
  const meta = { displayName: '视图', columns: [{ name: 'id', description: '编号' }] };
  assert.equal(metadataChanged(metadataForm(meta), meta), false);
  assert.equal(metadataChanged({ ...metadataForm(meta), overrides: { id: '' } }, meta), true);
  assert.equal(metadataChanged({ ...metadataForm(meta), description: '新口径' }, meta), true);
  assert.equal(
    metadataChanged({ ...metadataForm(meta), overrides: { id: '编号', unused: '' } }, meta),
    false,
  );
});

test('探测失败不会把已有视图误标为授权丢失，明确结果才标记', () => {
  const detail = { source: { views: ['v_a'] }, meta: [] };
  assert.equal(
    scopeCandidates(detail, { outcome: 'CONNECTION_FAILED', detectedViews: [] })[0].detected,
    null,
  );
  assert.equal(
    scopeCandidates(detail, { outcome: 'PASSED', detectedViews: [] })[0].detected,
    false,
  );
  assert.equal(
    scopeCandidates(detail, { outcome: 'PASSED', detectedViews: ['v_a'] })[0].detected,
    true,
  );
});
