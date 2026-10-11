package com.merine.rebuild.agent.datasource.persistence;

/** 启用中的数据源最小投射：供受控查询按视图解析来源。 */
public record DataSourceSourceRow(String id, String name) {
}
