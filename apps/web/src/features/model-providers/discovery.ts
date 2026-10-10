import type { DiscoveredProviderModel, SaveModelProvider } from '@merine/api-contract';
import { reasoningEffortOptions, type ReasoningEffort } from './reasoningEfforts.ts';

export type ModelInput = NonNullable<SaveModelProvider['models']>[number];

/** 表单里已有的模型标识（去空格），用于把拉取结果里的重复项标出来。 */
export function existingModelIds(models: ModelInput[]): Set<string> {
  return new Set(models.map((model) => model.modelId.trim()));
}

/**
 * 把拉取结果变成待新增的模型卡片：跳过已存在的标识，显示名留空（服务端会用标识回填），
 * 推理强度用服务端按官方文档给出的该模型取值（归一化成界面顺序），目录没收录时留空。
 */
export function newModelsFrom(
  existing: ModelInput[],
  found: DiscoveredProviderModel[],
  limit: number,
): ModelInput[] {
  const known = existingModelIds(existing);
  return found
    .filter((model) => !known.has(model.modelId.trim()))
    .slice(0, Math.max(limit, 0))
    .map((model) => ({
      modelId: model.modelId,
      displayName: '',
      remark: '',
      reasoningEfforts: normalizeEfforts(model.reasoningEfforts),
      status: 'ENABLED' as const,
    }));
}

/** 按界面顺序整理档位，去掉不认识的取值。 */
export function normalizeEfforts(
  efforts: readonly ReasoningEffort[] | undefined,
): ReasoningEffort[] {
  return reasoningEffortOptions
    .map((option) => option.value)
    .filter((effort) => (efforts ?? []).includes(effort));
}

/** 档位的中文标签，按顺序用「/」拼起来；空集合显示为「不设置」。 */
export function effortsLabel(efforts: readonly ReasoningEffort[] | undefined): string {
  const labels = reasoningEffortOptions
    .filter((option) => (efforts ?? []).includes(option.value))
    .map((option) => option.label);
  return labels.length ? labels.join(' / ') : '不设置';
}
