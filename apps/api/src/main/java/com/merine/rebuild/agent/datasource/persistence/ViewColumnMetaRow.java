package com.merine.rebuild.agent.datasource.persistence;

/** 字段说明覆盖值行；默认说明取目标库列注释，这里只保存管理员填写的覆盖值。 */
public record ViewColumnMetaRow(
        String dataSourceId,
        String viewName,
        String columnName,
        String description) {
}
