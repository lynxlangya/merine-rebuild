package com.merine.rebuild.agent.datasource.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * 数据源管理视图。密码字段不存在；连接地址是管理员自己配置的内容，可回显用于编辑。
 * {@code lastCheckAt} 为空表示尚未检查或配置修改后旧结果已失效。
 */
@Schema(name = "DataSourceView")
public record DataSourceView(
        String id,
        String name,
        String remark,
        DataSourceRequests.DbType dbType,
        String host,
        int port,
        String databaseName,
        String username,
        DataSourceRequests.State status,
        List<String> views,
        Instant lastCheckAt,
        DataSourceCheckOutcome lastCheckStatus,
        String lastCheckMessage,
        Instant updatedAt,
        int version,
        int semanticVersion) {
}
