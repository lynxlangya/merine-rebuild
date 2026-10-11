package com.merine.rebuild.agent.datasource.persistence;

import com.merine.rebuild.agent.datasource.dto.DataSourceRequests.DbType;
import com.merine.rebuild.agent.datasource.dto.DataSourceRequests.State;
import java.time.Instant;

/**
 * 检查路径专用投射：含密码密文与检查所需的连接参数。
 * 只在 Service 与检查器内部传递，不进入任何 HTTP 响应或日志。
 */
public record DataSourceSecretRow(
        String id,
        String name,
        DbType dbType,
        String host,
        int port,
        String databaseName,
        String username,
        String passwordCipher,
        State status,
        int version,
        Instant updatedAt) {
}
