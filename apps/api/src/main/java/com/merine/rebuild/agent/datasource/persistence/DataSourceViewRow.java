package com.merine.rebuild.agent.datasource.persistence;

/** 数据源允许查询的视图白名单行。 */
public record DataSourceViewRow(Long id, String dataSourceId, String viewName) {
}
