import type { components } from './generated/diagnostics';

/** 工程诊断：显式导出生成契约，字段定义仍来自后端。 */
export type BootstrapStatus = components['schemas']['BootstrapStatus'];
export type ProbeRecord = components['schemas']['ProbeRecord'];
export type CreateProbe = components['schemas']['CreateProbe'];
