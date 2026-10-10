package com.merine.rebuild.system.audit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 审计保留期配置（`merine.audit.retention`）。
 *
 * {@code days} 是唯一的判定依据：清理只删除「发生时刻早于 now - days 天」的行，
 * 窗口内的记录在任何情况下都不动。过期即永久删除，本项目不做归档文件；
 * 需要更长的证据链时应先做导出，而不是把表一直留着。
 */
@ConfigurationProperties(prefix = "merine.audit.retention")
public record AuditRetentionProperties(
        @DefaultValue("30") int days,
        @DefaultValue("true") boolean enabled) {

    public AuditRetentionProperties {
        if (days < 1) {
            throw new IllegalArgumentException("审计保留期至少 1 天，当前为 " + days);
        }
    }
}
