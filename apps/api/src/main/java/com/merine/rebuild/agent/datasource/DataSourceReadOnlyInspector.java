package com.merine.rebuild.agent.datasource;

import com.merine.rebuild.agent.datasource.DataSourceAuthorizationEvaluator.Decision;
import com.merine.rebuild.agent.datasource.DataSourceAuthorizationEvaluator.Privilege;
import com.merine.rebuild.agent.datasource.dto.DataSourceCheckOutcome;
import com.merine.rebuild.agent.datasource.dto.DataSourceCheckResult;
import com.merine.rebuild.agent.datasource.dto.DataSourceViewColumn;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 只读授权检查：用一条临时、受限、及时关闭的连接读取 MySQL 授权元数据，不执行任何业务 SQL，
 * 更不尝试 INSERT/UPDATE/DELETE/DDL 来“验证”只读。JDBC 的 {@code setReadOnly} 只是辅助信号，
 * 真正的边界是数据库账号自身的授权，以及这里的白名单判定。
 *
 * <p>元数据来源：{@code USER_PRIVILEGES}（全局）、{@code SCHEMA_PRIVILEGES}（库级）、
 * {@code TABLE_PRIVILEGES}（表/视图级）、{@code COLUMN_PRIVILEGES}（列级）与
 * {@code APPLICABLE_ROLES}（账号可用的角色）。非特权账号只能看到自己的授权行，
 * 但仍按当前账号与角色过滤，避免高权限账号把别人的授权算到自己头上。
 * 角色自身的授权行读不到，因此持有角色的账号一律判「授权无法确认」。
 *
 * <p>连接串完全由服务端拼装：只允许固定参数，不接受任意 JDBC URL 或用户自定义参数；
 * 目标使用上一步校验过的数字地址，不在连接时重新解析主机名。
 */
@Component
class DataSourceReadOnlyInspector {
    private static final Logger log = LoggerFactory.getLogger(DataSourceReadOnlyInspector.class);

    private final DataSourceProperties properties;

    DataSourceReadOnlyInspector(DataSourceProperties properties) {
        this.properties = properties;
    }

    DataSourceCheckResult inspect(DataSourceTargetGuard.ResolvedTarget target, String database,
                                  String username, String password, List<String> selectedViews) {
        return inspect(target, database, username, password, selectedViews, null);
    }

    DataSourceCheckResult inspect(DataSourceTargetGuard.ResolvedTarget target, String database,
                                  String username, String password, List<String> selectedViews,
                                  String describeView) {
        try (Connection connection = DataSourceConnections.open(
                properties, target, database, username, password)) {
            // 辅助信号：即使驱动据此拒绝写语句，也不把它当安全边界。
            connection.setReadOnly(true);
            String version = singleString(connection, "SELECT VERSION()");
            if (version != null && version.toLowerCase(Locale.ROOT).contains("mariadb")) {
                return result(DataSourceCheckOutcome.AUTHORIZATION_UNCONFIRMED,
                        "检测到目标不是 MySQL 服务，无法确认授权状态", List.of());
            }
            String currentUser = singleString(connection, "SELECT CURRENT_USER()");
            if (currentUser == null || currentUser.isBlank()) {
                return result(DataSourceCheckOutcome.AUTHORIZATION_UNCONFIRMED,
                        "无法读取当前数据库账号，不能确认只读状态", List.of());
            }
            boolean metadataComplete = true;
            List<String> roles;
            try {
                roles = applicableRoles(connection);
            } catch (SQLException error) {
                metadataComplete = false;
                roles = List.of();
                log.debug("DataSource role metadata unavailable: sqlState={} errorCode={}",
                        error.getSQLState(), error.getErrorCode());
            }
            Set<String> principals = new HashSet<>();
            principals.add(currentUser);
            principals.addAll(roles);
            List<Privilege> privileges = new ArrayList<>();
            metadataComplete &= collectPrivileges(connection, principals, privileges);
            List<String> detectedViews;
            try {
                detectedViews = detectedViews(connection, database, privileges);
            } catch (SQLException error) {
                metadataComplete = false;
                detectedViews = List.of();
                log.debug("DataSource view metadata unavailable: sqlState={} errorCode={}",
                        error.getSQLState(), error.getErrorCode());
            }
            Decision decision = DataSourceAuthorizationEvaluator.evaluate(
                    metadataComplete, roles, privileges, database, detectedViews, selectedViews);
            if (describeView != null) {
                if (!detectedViews.contains(describeView)) {
                    return new DataSourceCheckResult(DataSourceCheckOutcome.VIEW_DENIED,
                            "视图「" + describeView + "」不存在或当前账号没有 SELECT 授权；勾选不会修改数据库授权",
                            Instant.now(), detectedViews, List.of());
                }
                if (decision.outcome() == DataSourceCheckOutcome.PASSED) {
                    try {
                        return new DataSourceCheckResult(decision.outcome(), decision.message(),
                                Instant.now(), detectedViews, describeColumns(connection, database, describeView));
                    } catch (SQLException error) {
                        log.debug("DataSource column metadata unavailable: sqlState={} errorCode={}",
                                error.getSQLState(), error.getErrorCode());
                        return result(DataSourceCheckOutcome.AUTHORIZATION_UNCONFIRMED,
                                "无法读取视图字段元数据，请稍后重试", detectedViews);
                    }
                }
            }
            return new DataSourceCheckResult(decision.outcome(), decision.message(), Instant.now(),
                    detectedViews, List.of());
        } catch (SQLException error) {
            log.debug("DataSource check failed: sqlState={} errorCode={}",
                    error.getSQLState(), error.getErrorCode());
            return result(DataSourceCheckOutcome.CONNECTION_FAILED, connectionMessage(error), List.of());
        }
    }

    private List<String> applicableRoles(Connection connection) throws SQLException {
        return query(connection,
                "SELECT ROLE_NAME, ROLE_HOST FROM information_schema.APPLICABLE_ROLES",
                row -> row.getString(1) + "@" + row.getString(2));
    }

    /** 四张授权表的读取；任一张失败或出现无法归属的行，整体标记为不可确认。 */
    private boolean collectPrivileges(Connection connection, Set<String> principals,
                                      List<Privilege> privileges) {
        boolean complete = true;
        try {
            List<Object[]> rows = query(connection, """
                    SELECT GRANTEE, PRIVILEGE_TYPE, IS_GRANTABLE
                      FROM information_schema.USER_PRIVILEGES
                    """, row -> new Object[] {row.getString(1), row.getString(2), row.getString(3)});
            complete &= appendPrivileges(rows, principals, privileges, 1, null, null, null, 2);
        } catch (SQLException error) {
            complete = false;
            log.debug("DataSource USER_PRIVILEGES unavailable: sqlState={}", error.getSQLState());
        }
        try {
            List<Object[]> rows = query(connection, """
                    SELECT GRANTEE, TABLE_SCHEMA, PRIVILEGE_TYPE, IS_GRANTABLE
                      FROM information_schema.SCHEMA_PRIVILEGES
                    """, row -> new Object[] {row.getString(1), row.getString(2), row.getString(3), row.getString(4)});
            complete &= appendPrivileges(rows, principals, privileges, 2, 1, null, null, 3);
        } catch (SQLException error) {
            complete = false;
            log.debug("DataSource SCHEMA_PRIVILEGES unavailable: sqlState={}", error.getSQLState());
        }
        try {
            List<Object[]> rows = query(connection, """
                    SELECT GRANTEE, TABLE_SCHEMA, TABLE_NAME, PRIVILEGE_TYPE, IS_GRANTABLE
                      FROM information_schema.TABLE_PRIVILEGES
                    """, row -> new Object[] {row.getString(1), row.getString(2), row.getString(3),
                            row.getString(4), row.getString(5)});
            complete &= appendPrivileges(rows, principals, privileges, 3, 1, 2, null, 4);
        } catch (SQLException error) {
            complete = false;
            log.debug("DataSource TABLE_PRIVILEGES unavailable: sqlState={}", error.getSQLState());
        }
        try {
            List<Object[]> rows = query(connection, """
                    SELECT GRANTEE, TABLE_SCHEMA, TABLE_NAME, COLUMN_NAME, PRIVILEGE_TYPE, IS_GRANTABLE
                      FROM information_schema.COLUMN_PRIVILEGES
                    """, row -> new Object[] {row.getString(1), row.getString(2), row.getString(3),
                            row.getString(4), row.getString(5), row.getString(6)});
            complete &= appendPrivileges(rows, principals, privileges, 4, 1, 2, 3, 5);
        } catch (SQLException error) {
            complete = false;
            log.debug("DataSource COLUMN_PRIVILEGES unavailable: sqlState={}", error.getSQLState());
        }
        return complete;
    }

    /**
     * 按 GRANTEE 过滤授权行：高权限账号能读到其它账号甚至角色的授权行，不能把它们算成本账号的。
     * 行格式固定为 {@code 'user'@'host'}，无法解析说明元数据不完整，整体判不可确认。
     */
    private static boolean appendPrivileges(List<Object[]> rows, Set<String> principals,
                                            List<Privilege> privileges, int typeIndex,
                                            Integer schemaIndex, Integer tableIndex,
                                            Integer columnIndex, int grantableIndex) {
        boolean complete = true;
        for (Object[] row : rows) {
            String grantee = parseGrantee((String) row[0]);
            if (grantee == null) {
                complete = false;
                continue;
            }
            if (!principals.contains(grantee)) continue;
            String type = cast(row[typeIndex]);
            boolean grantable = "YES".equalsIgnoreCase(cast(row[grantableIndex]));
            privileges.add(new Privilege(
                    value(schemaIndex, row), value(tableIndex, row), value(columnIndex, row),
                    type == null ? "" : type, grantable));
        }
        return complete;
    }

    private static String value(Integer index, Object[] row) {
        if (index == null) return null;
        String value = cast(row[index]);
        return value == null || value.isBlank() ? null : value;
    }

    private static String cast(Object value) {
        return value instanceof String text ? text : null;
    }

    /** 解析 {@code 'user'@'host'}；处理 MySQL 的双写单引号与反斜杠转义。 */
    static String parseGrantee(String grantee) {
        if (grantee == null || grantee.isEmpty()) return null;
        int[] cursor = {0};
        String user = quoted(grantee, cursor);
        if (user == null || cursor[0] >= grantee.length() || grantee.charAt(cursor[0]) != '@') return null;
        cursor[0] += 1;
        String host = quoted(grantee, cursor);
        if (host == null) return null;
        return user + "@" + host;
    }

    private static String quoted(String text, int[] cursor) {
        if (cursor[0] >= text.length() || text.charAt(cursor[0]) != '\'') return null;
        cursor[0] += 1;
        StringBuilder value = new StringBuilder();
        while (cursor[0] < text.length()) {
            char current = text.charAt(cursor[0]);
            if (current == '\'') {
                if (cursor[0] + 1 < text.length() && text.charAt(cursor[0] + 1) == '\'') {
                    value.append('\'');
                    cursor[0] += 2;
                    continue;
                }
                cursor[0] += 1;
                return value.toString();
            }
            if (current == '\\' && cursor[0] + 1 < text.length()) {
                value.append(text.charAt(cursor[0] + 1));
                cursor[0] += 2;
                continue;
            }
            value.append(current);
            cursor[0] += 1;
        }
        return null;
    }

    private List<String> detectedViews(Connection connection, String database, List<Privilege> privileges)
            throws SQLException {
        List<String> views = query(connection, """
                SELECT TABLE_NAME FROM information_schema.TABLES
                 WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'VIEW' ORDER BY TABLE_NAME
                """, database, row -> row.getString(1));
        return views.stream()
                .filter(view -> DataSourceAuthorizationEvaluator.hasSelect(privileges, database, view))
                .toList();
    }

    /** 字段元数据只读 information_schema；最多 100 列，与 Agent 的可查询列上限一致。 */
    private List<DataSourceViewColumn> describeColumns(Connection connection, String database, String view)
            throws SQLException {
        return query(connection, """
                SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE, COLUMN_COMMENT
                  FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?
                 ORDER BY ORDINAL_POSITION LIMIT 100
                """, database, view, row -> new DataSourceViewColumn(row.getString(1), row.getString(2),
                "YES".equalsIgnoreCase(row.getString(3)),
                row.getString(4) == null ? "" : row.getString(4)));
    }

    private String singleString(Connection connection, String sql) throws SQLException {
        List<String> values = query(connection, sql, row -> row.getString(1));
        return values.isEmpty() ? null : values.getFirst();
    }

    private <T> List<T> query(Connection connection, String sql, RowMapper<T> mapper) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(queryTimeoutSeconds());
            try (ResultSet result = statement.executeQuery(sql)) {
                List<T> rows = new ArrayList<>();
                while (result.next()) rows.add(mapper.map(result));
                return rows;
            }
        }
    }

    private <T> List<T> query(Connection connection, String sql, String parameter,
                              RowMapper<T> mapper) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(queryTimeoutSeconds());
            statement.setString(1, parameter);
            try (ResultSet result = statement.executeQuery()) {
                List<T> rows = new ArrayList<>();
                while (result.next()) rows.add(mapper.map(result));
                return rows;
            }
        }
    }

    private <T> List<T> query(Connection connection, String sql, String first, String second,
                              RowMapper<T> mapper) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(queryTimeoutSeconds());
            statement.setString(1, first);
            statement.setString(2, second);
            try (ResultSet result = statement.executeQuery()) {
                List<T> rows = new ArrayList<>();
                while (result.next()) rows.add(mapper.map(result));
                return rows;
            }
        }
    }

    private int queryTimeoutSeconds() {
        return Math.max(1, properties.socketTimeoutMs() / 1000);
    }

    private static DataSourceCheckResult result(DataSourceCheckOutcome outcome, String message,
                                                List<String> detectedViews) {
        return new DataSourceCheckResult(outcome, message, Instant.now(), detectedViews, List.of());
    }

    /** 只按错误类型给固定文案，不回显驱动异常原文：其中可能带连接串或输入片段。 */
    private static String connectionMessage(SQLException error) {
        int code = error.getErrorCode();
        String state = error.getSQLState() == null ? "" : error.getSQLState();
        if (code == 1045 || state.startsWith("28")) {
            return "数据库账号或密码不正确，或该账号不允许从本服务连接";
        }
        if (code == 1044) return "数据库账号无权访问目标数据库，或数据库不存在";
        if (code == 1049) return "目标数据库不存在";
        if (state.startsWith("08")) return "无法连接数据库：网络不可达、端口未开放或连接超时";
        return "连接检查失败：无法完成目标数据库的只读授权检查";
    }

    @FunctionalInterface
    private interface RowMapper<T> {
        T map(ResultSet row) throws SQLException;
    }
}
