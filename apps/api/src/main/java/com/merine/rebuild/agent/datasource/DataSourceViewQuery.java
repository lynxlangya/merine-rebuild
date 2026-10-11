package com.merine.rebuild.agent.datasource;

import com.merine.rebuild.agent.AgentSecretCipher;
import com.merine.rebuild.agent.datasource.DataSourceTargetGuard.TargetDeniedException;
import com.merine.rebuild.agent.datasource.dto.DataSourceRequests.State;
import com.merine.rebuild.agent.datasource.persistence.DataSourceMapper;
import com.merine.rebuild.agent.datasource.persistence.DataSourceSecretRow;
import com.merine.rebuild.agent.datasource.persistence.DataSourceSourceRow;
import com.merine.rebuild.common.ApiException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 受控查询对数据源管理的唯一入口：工具层用它在已启用、且白名单包含目标视图的数据源上执行
 * 服务端内置的参数化 SQL，不向调用方或模型暴露凭据、地址或任意 SQL 执行能力。
 *
 * <p>约束：
 * <ul>
 *   <li>只接受 <strong>编译期常量</strong> SQL；视图名必须是该数据源 {@code ai_data_source_view}
 *       白名单中的项，否则拒绝；</li>
 *   <li>每次查询重新校验目标网段/端口并解密密码，使用临时、只读、带超时的连接，不建连接池、不重试；</li>
 *   <li>同一视图被多个启用数据源白名单命中时 fail closed，不猜来源，也不回退到应用库直查。</li>
 * </ul>
 */
@Component
public class DataSourceViewQuery {
    private static final Logger log = LoggerFactory.getLogger(DataSourceViewQuery.class);

    private final DataSourceMapper mapper;
    private final AgentSecretCipher cipher;
    private final DataSourceTargetGuard guard;
    private final DataSourceProperties properties;

    public DataSourceViewQuery(DataSourceMapper mapper, AgentSecretCipher cipher,
                               DataSourceTargetGuard guard, DataSourceProperties properties) {
        this.mapper = mapper;
        this.cipher = cipher;
        this.guard = guard;
        this.properties = properties;
    }

    /** 已启用的候选来源；0 个或多于 1 个都返回空，由调用方把工具标为不可用并记日志。 */
    @Transactional(readOnly = true)
    public Optional<Source> enabledSourceFor(String view) {
        List<DataSourceSourceRow> rows = mapper.enabledSourcesForView(view);
        if (rows.size() > 1) {
            log.warn("DataSource view whitelisted by multiple enabled sources: view={} count={}",
                    view, rows.size());
            return Optional.empty();
        }
        return rows.stream().findFirst().map(row -> new Source(row.id(), row.name()));
    }

    public record Source(String id, String name) {}

    /** 在指定数据源的视图白名单内执行内置 SQL，返回按列标签命名的行。 */
    public List<Map<String, Object>> select(String dataSourceId, String view, String sql,
                                            List<Object> parameters) {
        DataSourceSecretRow source = mapper.findSecret(dataSourceId);
        if (source == null || source.status() != State.ENABLED) {
            throw DataSourceErrors.sourceUnavailable();
        }
        boolean allowed = mapper.viewsOf(dataSourceId).stream()
                .anyMatch(row -> row.viewName().equals(view));
        if (!allowed) throw DataSourceErrors.viewNotAllowed();
        DataSourceTargetGuard.ResolvedTarget target;
        try {
            target = guard.resolve(source.host(), source.port());
        } catch (TargetDeniedException denied) {
            throw DataSourceErrors.targetDenied(denied.getMessage());
        }
        String password = decrypt(source);
        try (Connection connection = DataSourceConnections.open(
                properties, target, source.databaseName(), source.username(), password)) {
            connection.setReadOnly(true);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setQueryTimeout(queryTimeoutSeconds());
                bind(statement, parameters);
                try (ResultSet result = statement.executeQuery()) {
                    return rows(result);
                }
            }
        } catch (SQLException error) {
            log.warn("DataSource query failed: id={} sqlState={} errorCode={}",
                    source.id(), error.getSQLState(), error.getErrorCode());
            throw DataSourceErrors.queryFailed();
        }
    }

    private int queryTimeoutSeconds() {
        return Math.max(1, properties.socketTimeoutMs() / 1000);
    }

    private String decrypt(DataSourceSecretRow source) {
        try {
            return cipher.decrypt(DataSourceStore.secretAad(source.id()), source.passwordCipher());
        } catch (ApiException error) {
            throw DataSourceErrors.secretUnavailable();
        }
    }

    private static void bind(PreparedStatement statement, List<Object> parameters) throws SQLException {
        int index = 1;
        for (Object parameter : parameters) {
            switch (parameter) {
                case String text -> statement.setString(index, text);
                case Instant instant -> statement.setTimestamp(index, Timestamp.from(instant));
                case Integer number -> statement.setInt(index, number);
                case Long number -> statement.setLong(index, number);
                case null -> statement.setObject(index, null);
                default -> throw new IllegalArgumentException("不支持的数据源查询参数类型");
            }
            index += 1;
        }
    }

    private static List<Map<String, Object>> rows(ResultSet result) throws SQLException {
        ResultSetMetaData meta = result.getMetaData();
        List<Map<String, Object>> rows = new ArrayList<>();
        while (result.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int column = 1; column <= meta.getColumnCount(); column += 1) {
                row.put(meta.getColumnLabel(column), result.getObject(column));
            }
            rows.add(row);
        }
        return rows;
    }
}
