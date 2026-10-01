import assert from 'node:assert/strict';
import { test } from 'node:test';
import { normalizeIntelligenceTask } from './intelligenceCommand.ts';
import { submissionIntent } from '../../shared/idempotentIntent.ts';

test('已有研判与新研判切换时只提交实际采用的依据', () => {
  const input = {
    title: ' 核查 ',
    instruction: ' 办理 ',
    expectedResult: ' 结论 ',
    dueAt: '2026-10-01T08:00:00Z',
    targetUnitCodes: ['b', 'a', 'b'],
    backgroundSummary: ' 摘要 ',
    assessmentId: '9',
    newAssessment: { analysis: '未采用的填写', recommendationCode: 'VERIFY' },
  };
  const command = normalizeIntelligenceTask(input);
  assert.equal(command.title, '核查');
  assert.deepEqual(command.targetUnitCodes, ['a', 'b']);
  assert.equal(command.newAssessment, undefined);
  const first = submissionIntent(null, command);
  assert.equal(
    submissionIntent(
      first,
      normalizeIntelligenceTask({ ...input, title: '核查', targetUnitCodes: ['a', 'b'] }),
    ).key,
    first.key,
  );
  const fresh = normalizeIntelligenceTask({ ...input, assessmentId: undefined });
  assert.equal(fresh.assessmentId, undefined);
  assert.equal(fresh.newAssessment?.analysis, '未采用的填写');
});
