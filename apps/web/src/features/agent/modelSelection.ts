import type { ModelOption } from '@merine/api-contract';
import type { AgentModelSelection, ReasoningEffort } from './chatModel';

export interface ComposerChoice {
  modelOptionId: string;
  reasoningEffort?: ReasoningEffort;
}

/** 显式选择 > 历史会话 > 首个可用模型；只接受选中模型自己的档位。 */
export function resolveComposerSelection(
  options: ModelOption[],
  previous: AgentModelSelection | undefined,
  choice: ComposerChoice | undefined,
  fallbackEffort: ReasoningEffort,
) {
  const explicit = options.find((option) => option.id === choice?.modelOptionId);
  const history = options.find(
    (option) => option.providerId === previous?.providerId && option.modelId === previous?.modelId,
  );
  const model = explicit ?? history ?? options[0];
  const efforts = model?.reasoningEfforts ?? [];
  const requested = explicit
    ? choice?.reasoningEffort
    : history
      ? previous?.reasoningEffort
      : undefined;
  const effort =
    requested && efforts.includes(requested)
      ? requested
      : model?.defaultReasoningEffort && efforts.includes(model.defaultReasoningEffort)
        ? model.defaultReasoningEffort
        : efforts.includes(fallbackEffort)
          ? fallbackEffort
          : efforts[0];
  return { model, effort };
}
