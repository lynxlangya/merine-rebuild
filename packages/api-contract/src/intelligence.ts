import type { components } from './generated/intelligence';

/** 信息流转与本单位研判：显式导出生成契约，字段定义仍来自后端。 */
export type IntelligenceDetail = components['schemas']['IntelligenceDetail'];
export type IntelligenceAction = components['schemas']['IntelligenceAction'];
export type IntelligenceReceipt = components['schemas']['IntelligenceReceipt'];
export type IntelligenceUnitOption = components['schemas']['IntelligenceUnitOption'];
export type IntelligenceListItem = components['schemas']['IntelligenceListItem'];
export type PageResultIntelligenceListItem =
  components['schemas']['PageResultIntelligenceListItem'];
export type IntelligenceDraftRequest = components['schemas']['IntelligenceDraftRequest'];
export type IntelligenceSendRequest = components['schemas']['IntelligenceSendRequest'];
export type IntelligenceFeedbackRequest = components['schemas']['IntelligenceFeedbackRequest'];
export type IntelligenceSupplementRequest = components['schemas']['IntelligenceSupplementRequest'];
export type Assessment = components['schemas']['Assessment'];
export type RecordAssessment = components['schemas']['RecordAssessment'];
export type AssessmentContext = components['schemas']['AssessmentContext'];
export type SignedReceipt = components['schemas']['SignedReceipt'];
export type PageResultAssessment = components['schemas']['PageResultAssessment'];
