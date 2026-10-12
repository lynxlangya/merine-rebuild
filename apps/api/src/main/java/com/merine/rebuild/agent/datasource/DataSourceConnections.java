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
 * 时间戳统一 UTC；非回环目标按 {@link DataSourceProperties.TlsMode} 强制加密与身份校验，
 * 只有回环地址允许关闭 TLS。
 *
 * <p>{@code VERIFY_IDENTITY} 校验的是连接使用的数字地址，因此目标证书需要包含该地址的
 * IP SAN；不能包含时由部署方显式选择 {@code VERIFY_CA}（只校验证书链）或 {@code REQUIRED}
 * （仅加密），不由代码静默降级。
 */
final class DataSourceConnections {
    private DataSourceConnections() {}

    static Connection open(DataSourceProperties properties, DataSourceTargetGuard.ResolvedTarget target,
                           String database, String username, String password) throws SQLException {
        String host = target.address().getHostAddress();
        int zone = host.indexOf('%');
        if (zone >= 0) host = host.substring(0, zone);
        if (target.address() instanceof Inet6Address) host = "[" + host + "]";
        String tls = target.loopback()
                ? "sslMode=DISABLED&allowPublicKeyRetrieval=true"
                : "sslMode=" + properties.tlsMode();
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
