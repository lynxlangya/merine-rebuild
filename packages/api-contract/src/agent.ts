import type { components } from './generated/agent';

export type ModelProvider = components['schemas']['ModelProvider'];
export type ModelProviderPage = components['schemas']['PageResultModelProvider'];
export type ProviderModel = components['schemas']['ProviderModel'];
export type ModelOption = components['schemas']['ModelOption'];
export type SaveModelProvider = components['schemas']['SaveModelProvider'];
export type DiscoverProviderModels = components['schemas']['DiscoverProviderModels'];
export type ProviderModelCatalog = components['schemas']['ProviderModelCatalog'];
export type DiscoveredProviderModel = components['schemas']['DiscoveredProviderModel'];
export type ProviderEffortCatalog = components['schemas']['ProviderEffortCatalog'];
export type ChatConversation = components['schemas']['ChatConversation'];
export type ChatConversationMessage = components['schemas']['ChatConversationMessage'];
export type ChatRunRecord = components['schemas']['ChatRunRecord'];
export type ChatRunStats = components['schemas']['ChatRunStats'];
export type ChatConversationPage = components['schemas']['PageResultChatConversation'];
export type ChatRunRecordPage = components['schemas']['PageResultChatRunRecord'];
export type ProviderModelEfforts = components['schemas']['ProviderModelEfforts'];

/** 本地演示对话的契约类型：SSE data 载荷联合、请求、执行快照与消息部件联合。 */
export type ChatEvent = components['schemas']['ChatStreamEvent'];
export type ChatRequest = components['schemas']['ChatRequest'];
export type ChatRunView = components['schemas']['ChatRunView'];
export type ChatRuntime = components['schemas']['ChatRuntime'];
export type MessagePart = components['schemas']['MessagePart'];
