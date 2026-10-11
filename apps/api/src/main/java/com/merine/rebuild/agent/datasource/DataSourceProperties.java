package com.merine.rebuild.agent.datasource;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 数据源检查的服务端边界，全部由部署配置控制，普通表单用户改不了。
 *
 * <ul>
 *   <li>{@code allowedNetworks}：目标主机 DNS 解析出的每个地址都必须落在这些 IPv4/IPv6 网段内；
 *       默认空表示拒绝所有目标（fail closed）。</li>
 *   <li>{@code allowedPorts}：允许连接的目标端口；首版默认 3306。</li>
 *   <li>{@code connectTimeoutMs}/{@code socketTimeoutMs}：连接与读写超时，禁止自动重试。</li>
 *   <li>{@code maxConcurrentChecks}：同时执行的检查数上限，超出直接拒绝，不排队堆积。</li>
 *   <li>{@code acquireTimeoutMs}：等待检查名额的最长时间。</li>
 *   <li>{@code maxViews}：单条数据源允许勾选的最大视图数。</li>
 * </ul>
 *
 * <p>非本机目标固定使用 {@code sslMode=REQUIRED}（加密但不跳过证书校验）；仅回环地址允许关闭 TLS。
 * 内网私有 CA 通过部署时给 JVM 配置信任库解决，不为连接静默降级。
 */
@ConfigurationProperties(prefix = "merine.agent.datasource")
public record DataSourceProperties(
        @DefaultValue List<String> allowedNetworks,
        @DefaultValue("3306") List<Integer> allowedPorts,
        @DefaultValue("3000") int connectTimeoutMs,
        @DefaultValue("5000") int socketTimeoutMs,
        @DefaultValue("2") int maxConcurrentChecks,
        @DefaultValue("2000") long acquireTimeoutMs,
        @DefaultValue("50") int maxViews) {

    public DataSourceProperties {
        allowedNetworks = allowedNetworks == null ? List.of() : List.copyOf(allowedNetworks);
        allowedPorts = allowedPorts == null || allowedPorts.isEmpty() ? List.of(3306) : List.copyOf(allowedPorts);
        if (connectTimeoutMs < 200 || socketTimeoutMs < 200 || maxConcurrentChecks < 1
                || acquireTimeoutMs < 0 || maxViews < 1) {
            throw new IllegalArgumentException("数据源检查边界配置不合法");
        }
    }
}
