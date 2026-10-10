import type { ModelOption } from '@merine/api-contract';

export type ReasoningEffort = ModelOption['reasoningEfforts'][number];

/**
 * 推理强度的取值、顺序与中文标签，按强度升序。
 * 取值是两家官方的并集：DeepSeek 用 关闭思考/低/高/最高（none/low/high/max）。
 * 千问 AI 平台按系列不同：Qwen3.8 是 低/中/极高（low/medium/xhigh），
 * glm-5.3 与 kimi-k3 是 低/高/最高。逐模型可用的子集由供应商配置决定。
 */
export const reasoningEffortOptions: { value: ReasoningEffort; label: string }[] = [
  { value: 'NONE', label: '关闭思考' },
  { value: 'LOW', label: '低' },
  { value: 'MEDIUM', label: '中' },
  { value: 'HIGH', label: '高' },
  { value: 'XHIGH', label: '极高' },
  { value: 'MAX', label: '最高' },
];

/** 兜底默认档位：没有官方默认值时优先「高」，再退回第一档。 */
export const defaultReasoningEffort: ReasoningEffort = 'HIGH';
