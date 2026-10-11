package com.merine.rebuild.agent.datasource;

import com.merine.rebuild.agent.datasource.dto.DataSourceView;
import com.merine.rebuild.agent.datasource.dto.ScopeColumnMeta;
import com.merine.rebuild.agent.datasource.dto.ScopeViewMeta;
import com.merine.rebuild.agent.datasource.persistence.DataSourceRow;
import com.merine.rebuild.agent.datasource.persistence.DataSourceViewRow;
import com.merine.rebuild.agent.datasource.persistence.ViewColumnMetaRow;
import com.merine.rebuild.agent.datasource.persistence.ViewMetaRow;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 数据库行到对外视图的转换；密码字段不在行类型里，转换不会泄露凭据。 */
final class DataSourceViews {
    private DataSourceViews() {}

    static DataSourceView of(DataSourceRow row, List<String> views) {
        return new DataSourceView(row.id(), row.name(), row.remark(), row.dbType(), row.host(),
                row.port(), row.databaseName(), row.username(), row.status(), views,
                row.lastCheckAt(), row.lastCheckStatus(),
                row.lastCheckMessage() == null ? "" : row.lastCheckMessage(),
                row.updatedAt(), row.version(), row.semanticVersion());
    }

    static List<String> names(List<DataSourceViewRow> rows) {
        return rows.stream().map(DataSourceViewRow::viewName).toList();
    }

    /**
     * 说明覆盖值按视图分组；显示名、说明与字段说明都为空的行不出现在结果里。
     * 只做行到对外模型的转换，不代表目标库中视图/字段仍然存在。
     */
    static List<ScopeViewMeta> scopeMeta(List<ViewMetaRow> views, List<ViewColumnMetaRow> columns) {
        Map<String, List<ScopeColumnMeta>> byView = new LinkedHashMap<>();
        for (ViewColumnMetaRow row : columns) {
            if (row.description() == null || row.description().isEmpty()) continue;
            byView.computeIfAbsent(row.viewName(), key -> new ArrayList<>())
                    .add(new ScopeColumnMeta(row.columnName(), row.description()));
        }
        Map<String, ScopeViewMeta> result = new LinkedHashMap<>();
        for (ViewMetaRow row : views) {
            List<ScopeColumnMeta> viewColumns = byView.getOrDefault(row.viewName(), List.of());
            if (row.displayName().isEmpty() && row.description().isEmpty() && viewColumns.isEmpty()) continue;
            result.put(row.viewName(), new ScopeViewMeta(row.viewName(), row.displayName(),
                    row.description(), viewColumns));
        }
        for (Map.Entry<String, List<ScopeColumnMeta>> entry : byView.entrySet()) {
            result.computeIfAbsent(entry.getKey(),
                    view -> new ScopeViewMeta(view, "", "", entry.getValue()));
        }
        return List.copyOf(result.values());
    }
}
