import type { components } from './generated/agent';

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
