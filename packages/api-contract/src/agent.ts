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

/** 数据源管理：配置、检查结论与启停动作。 */
export type DataSourceView = components['schemas']['DataSourceView'];
export type DataSourcePage = components['schemas']['PageResultDataSourceView'];
export type SaveDataSource = components['schemas']['SaveDataSource'];
export type CheckDataSourceDraft = components['schemas']['CheckDataSourceDraft'];
export type DataSourceCheckResult = components['schemas']['DataSourceCheckResult'];
export type DataSourceStatusChange = components['schemas']['DataSourceStatusChange'];
export type DataSourceViewColumn = components['schemas']['DataSourceViewColumn'];

/** 查询范围管理：范围与说明分开保存，保存范围会停用数据源等待复检。 */
export type QueryScopeDetail = components['schemas']['QueryScopeDetail'];
export type ScopeViewMeta = components['schemas']['ScopeViewMeta'];
export type ScopeColumnMeta = components['schemas']['ScopeColumnMeta'];
export type QueryScopeColumn = components['schemas']['QueryScopeColumn'];
export type ReplaceQueryScopeViews = components['schemas']['ReplaceQueryScopeViews'];
export type SaveQueryScopeMetadata = components['schemas']['SaveQueryScopeMetadata'];

/** 本地演示对话的契约类型：SSE data 载荷联合、请求、执行快照与消息部件联合。 */
export type ChatEvent = components['schemas']['ChatStreamEvent'];
export type ChatRequest = components['schemas']['ChatRequest'];
export type ChatRunView = components['schemas']['ChatRunView'];
export type ChatRuntime = components['schemas']['ChatRuntime'];
export type MessagePart = components['schemas']['MessagePart'];

/** 资料库的正式业务内容；只读定义，不包含查询结果或执行能力。 */
export type SemanticCatalog = components['schemas']['SemanticCatalog'];
export type SemanticTopic = components['schemas']['SemanticTopic'];
export type BusinessConcept = components['schemas']['BusinessConcept'];
export type MetricDefinition = components['schemas']['MetricDefinition'];
