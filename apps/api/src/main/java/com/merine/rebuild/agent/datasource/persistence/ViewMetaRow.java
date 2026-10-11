package com.merine.rebuild.agent.datasource.persistence;

/**
 * 视图语义覆盖值行（显示名与用途/指标说明）。空串表示未填写或沿用默认，不在这层解释。
 */
public record ViewMetaRow(
        String dataSourceId,
        String viewName,
        String displayName,
        String description) {
}
