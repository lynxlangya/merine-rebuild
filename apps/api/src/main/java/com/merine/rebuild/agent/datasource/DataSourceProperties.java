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
 *   <li>{@code tlsMode}：非回环目标的 TLS 验证强度。默认 {@code VERIFY_IDENTITY}
 *       （校验证书链并按连接使用的数字地址比对证书身份）；{@code VERIFY_CA} 只校验证书链；
 *       {@code REQUIRED} 仅加密不校验，仅供本地受限开发容器显式使用。</li>
 * </ul>
 *
 * <p>非回环目标固定启用 TLS；私有 CA 通过部署时给 JVM 配置信任库解决，不为连接静默降级。
 * 连接使用已解析的数字地址，因此 {@code VERIFY_IDENTITY} 要求目标证书包含该地址的 IP SAN；
 * 证书只有 DNS 名称时，需部署方显式改用 {@code VERIFY_CA} 或其他方案，不能静默跳过验证。
 */
@ConfigurationProperties(prefix = "merine.agent.datasource")
public record DataSourceProperties(
        @DefaultValue List<String> allowedNetworks,
        @DefaultValue("3306") List<Integer> allowedPorts,
        @DefaultValue("3000") int connectTimeoutMs,
        @DefaultValue("5000") int socketTimeoutMs,
        @DefaultValue("2") int maxConcurrentChecks,
        @DefaultValue("2000") long acquireTimeoutMs,
        @DefaultValue("50") int maxViews,
        @DefaultValue("VERIFY_IDENTITY") TlsMode tlsMode) {

    /** 非回环数据库连接的 TLS 验证强度；默认最严格的证书链 + 主机身份校验。 */
    public enum TlsMode {
        VERIFY_IDENTITY,
        VERIFY_CA,
        REQUIRED
    }

    public DataSourceProperties {
        allowedNetworks = allowedNetworks == null ? List.of() : List.copyOf(allowedNetworks);
        allowedPorts = allowedPorts == null || allowedPorts.isEmpty() ? List.of(3306) : List.copyOf(allowedPorts);
        tlsMode = tlsMode == null ? TlsMode.VERIFY_IDENTITY : tlsMode;
        if (connectTimeoutMs < 200 || socketTimeoutMs < 200 || maxConcurrentChecks < 1
                || acquireTimeoutMs < 0 || maxViews < 1) {
            throw new IllegalArgumentException("数据源检查边界配置不合法");
        }
    }
}
