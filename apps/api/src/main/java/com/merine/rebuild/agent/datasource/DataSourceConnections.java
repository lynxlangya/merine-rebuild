package com.merine.rebuild.agent.datasource;

import java.net.Inet6Address;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * 数据源出站连接的唯一拼装点：检查与受控查询共用。
 *
 * <p>只允许固定参数，不接受任意 JDBC URL 或用户自定义参数；目标使用调用方已解析并校验过的
 * 数字地址，不在连接时重新解析主机名，避免校验与连接之间的 DNS 重绑定。多语句关闭，
 * 时间戳统一 UTC；非回环目标强制 TLS，只有回环地址允许关闭。
 */
final class DataSourceConnections {
    private DataSourceConnections() {}

    static Connection open(DataSourceProperties properties, DataSourceTargetGuard.ResolvedTarget target,
                           String database, String username, String password) throws SQLException {
        String host = target.address().getHostAddress();
        int zone = host.indexOf('%');
        if (zone >= 0) host = host.substring(0, zone);
        if (target.address() instanceof Inet6Address) host = "[" + host + "]";
        String tls = target.loopback() ? "sslMode=DISABLED&allowPublicKeyRetrieval=true" : "sslMode=REQUIRED";
        String url = "jdbc:mysql://" + host + ":" + target.port() + "/" + database
                + "?connectTimeout=" + properties.connectTimeoutMs()
                + "&socketTimeout=" + properties.socketTimeoutMs()
                + "&" + tls
                + "&allowMultiQueries=false&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true";
        Properties parameters = new Properties();
        parameters.setProperty("user", username);
        parameters.setProperty("password", password);
        return DriverManager.getConnection(url, parameters);
    }
}
