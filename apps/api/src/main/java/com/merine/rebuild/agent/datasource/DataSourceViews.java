package com.merine.rebuild.agent.datasource;

import com.merine.rebuild.agent.datasource.dto.DataSourceView;
import com.merine.rebuild.agent.datasource.persistence.DataSourceRow;
import com.merine.rebuild.agent.datasource.persistence.DataSourceViewRow;
import java.util.List;

/** 数据库行到对外视图的转换；密码字段不在行类型里，转换不会泄露凭据。 */
final class DataSourceViews {
    private DataSourceViews() {}

    static DataSourceView of(DataSourceRow row, List<String> views) {
        return new DataSourceView(row.id(), row.name(), row.remark(), row.dbType(), row.host(),
                row.port(), row.databaseName(), row.username(), row.status(), views,
                row.lastCheckAt(), row.lastCheckStatus(),
                row.lastCheckMessage() == null ? "" : row.lastCheckMessage(),
                row.updatedAt(), row.version());
    }

    static List<String> names(List<DataSourceViewRow> rows) {
        return rows.stream().map(DataSourceViewRow::viewName).toList();
    }
}
