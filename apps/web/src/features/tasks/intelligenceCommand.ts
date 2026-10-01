import type { CreateIntelligenceTask } from '@merine/api-contract';

/** 指纹只包含实际提交内容；隐藏的表单分支不会混入命令。 */
export function normalizeIntelligenceTask(input: CreateIntelligenceTask): CreateIntelligenceTask {
  return {
    title: input.title.trim(),
    instruction: input.instruction.trim(),
    expectedResult: input.expectedResult.trim(),
    dueAt: input.dueAt,
    targetUnitCodes: [...new Set(input.targetUnitCodes)].sort(),
    backgroundSummary: input.backgroundSummary.trim(),
    sourceSupplementCheckpoint: input.sourceSupplementCheckpoint ?? undefined,
    ...(input.assessmentId
      ? { assessmentId: input.assessmentId }
      : {
          newAssessment: {
            ...input.newAssessment!,
            analysis: input.newAssessment!.analysis.trim(),
          },
        }),
  };
}
