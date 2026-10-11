package com.merine.rebuild.agent.datasource;

import com.merine.rebuild.agent.datasource.dto.DataSourceRequests;
import com.merine.rebuild.common.ApiException;
import com.merine.rebuild.common.ApiResponse;
import java.net.InetAddress;
import java.net.Inet6Address;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;

/** 数据源输入的显式规范化与边界校验；不碰网络，只做格式与部署允许范围检查。 */
final class DataSourceInputs {
    private static final Pattern HOST_NAME = Pattern.compile(
            "[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)*");
    private static final Pattern IDENTIFIER =
            Pattern.compile("[\\p{L}\\p{N}_$]{1,64}");

    private DataSourceInputs() {}

    static DataSourceRequests.Save normalizeSave(DataSourceRequests.Save input,
                                                 DataSourceProperties properties) {
        return new DataSourceRequests.Save(
                text(input.name(), "name", 80),
                optionalText(input.remark(), "remark", 300),
                dbType(input.dbType()),
                host(input.host()),
                port(input.port(), properties),
                identifier(input.databaseName(), "databaseName", "数据库名"),
                username(input.username()),
                password(input.password()),
                // 兼容旧客户端：只校验格式，不写白名单（唯一写入口在查询范围用例）。
                normalizeViews(input.views(), properties),
                input.version());
    }

    static DataSourceRequests.CheckDraft normalizeCheck(DataSourceRequests.CheckDraft input,
                                                        DataSourceProperties properties) {
        String id = input.id() == null || input.id().isBlank() ? null : input.id().trim();
        if (id != null && id.length() > 80) throw invalid("id", "配置标识不正确");
        return new DataSourceRequests.CheckDraft(
                id,
                dbType(input.dbType()),
                host(input.host()),
                port(input.port(), properties),
                identifier(input.databaseName(), "databaseName", "数据库名"),
                username(input.username()),
                password(input.password()),
                normalizeViews(input.views(), properties));
    }

    static String requirePassword(String password) {
        if (password == null) throw invalid("password", "请填写数据库密码");
        return password;
    }

    /** 主机只接受域名、IPv4 或 IPv6 字面量；不含协议、路径、端口或账号信息。 */
    private static String host(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty() || value.length() > 253) throw invalid("host", "请填写数据库主机地址");
        if (value.contains(":")) {
            try {
                if (!(InetAddress.getByName(value) instanceof Inet6Address)) {
                    throw invalid("host", "IPv6 地址格式不正确");
                }
            } catch (UnknownHostException error) {
                throw invalid("host", "IPv6 地址格式不正确");
            }
            return value;
        }
        if (!HOST_NAME.matcher(value).matches()) {
            throw invalid("host", "主机地址只能是域名、IPv4 或 IPv6 字面量，不含协议、路径或账号");
        }
        return value;
    }

    private static DataSourceRequests.DbType dbType(DataSourceRequests.DbType dbType) {
        if (dbType != DataSourceRequests.DbType.MYSQL) throw invalid("dbType", "首版只支持 MySQL");
        return dbType;
    }

    private static int port(Integer port, DataSourceProperties properties) {
        if (port == null || port < 1 || port > 65535) throw invalid("port", "端口必须在 1 到 65535 之间");
        if (!properties.allowedPorts().contains(port)) throw invalid("port", "端口不在服务端允许范围内");
        return port;
    }

    private static String identifier(String raw, String field, String label) {
        String value = raw == null ? "" : raw.trim();
        if (!IDENTIFIER.matcher(value).matches()) {
            throw invalid(field, label + "只能是 1 到 64 位的字母、数字、下划线或 $");
        }
        return value;
    }

    private static String username(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty() || value.length() > 64) throw invalid("username", "请填写数据库账号");
        for (int index = 0; index < value.length(); index += 1) {
            char current = value.charAt(index);
            if (current < 32 || current == 127) throw invalid("username", "账号不能包含控制字符");
        }
        return value;
    }

    /** 空串按「留空」处理：新建会报必填，编辑表示保留原密码。 */
    private static String password(String raw) {
        if (raw == null || raw.isBlank()) return null;
        if (raw.length() > 128) throw invalid("password", "密码长度不能超过 128 个字符");
        for (int index = 0; index < raw.length(); index += 1) {
            char current = raw.charAt(index);
            if (current < 32 || current == 127) throw invalid("password", "密码不能包含控制字符");
        }
        return raw;
    }

    /** 视图白名单规范化：去重、逐项校验；范围变更的唯一入口在查询范围用例。 */
    static List<String> normalizeViews(List<String> raw, DataSourceProperties properties) {
        if (raw == null) return List.of();
        if (raw.size() > properties.maxViews()) {
            throw invalid("views", "勾选视图不能超过 " + properties.maxViews() + " 个");
        }
        Set<String> seen = new LinkedHashSet<>();
        List<String> views = new ArrayList<>();
        for (String item : raw) {
            String view = item == null ? "" : item.trim();
            if (!IDENTIFIER.matcher(view).matches()) throw invalid("views", "视图名不合法：" + view);
            if (!seen.add(view)) throw invalid("views", "同一视图不能重复勾选：" + view);
            views.add(view);
        }
        return List.copyOf(views);
    }

    private static String text(String raw, String field, int max) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty() || value.length() > max) throw invalid(field, "内容长度不正确");
        return value;
    }

    private static String optionalText(String raw, String field, int max) {
        String value = raw == null ? "" : raw.trim();
        if (value.length() > max) throw invalid(field, "内容长度不能超过 " + max + " 个字符");
        return value;
    }

    static ApiException invalid(String field, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message,
                List.of(new ApiResponse.FieldError(field, message)));
    }
}
