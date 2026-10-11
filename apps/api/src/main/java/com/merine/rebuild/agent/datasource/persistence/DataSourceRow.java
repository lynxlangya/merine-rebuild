package com.merine.rebuild.agent.datasource.persistence;

import com.merine.rebuild.agent.datasource.dto.DataSourceCheckOutcome;
import com.merine.rebuild.agent.datasource.dto.DataSourceRequests.DbType;
import com.merine.rebuild.agent.datasource.dto.DataSourceRequests.State;
import java.time.Instant;

/**
 * 数据源数据库投射（不含密码密文）。列表与编辑表单只需要这些字段；
 * 需要解密的检查路径另行读取 {@link DataSourceSecretRow}。
 */
public record DataSourceRow(
        String id,
        String name,
        String remark,
        DbType dbType,
        String host,
        int port,
        String databaseName,
        String username,
        State status,
        Instant lastCheckAt,
        DataSourceCheckOutcome lastCheckStatus,
        String lastCheckMessage,
        Integer checkedVersion,
        Instant updatedAt,
        int version) {
}
