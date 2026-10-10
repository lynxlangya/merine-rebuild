package com.merine.rebuild.system.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 每日定时清理过期审计流水，时刻由 `merine.audit.retention.cron` 决定（默认本地 03:30）。
 *
 * 默认开启，测试 profile 显式关闭（`merine.audit.retention.enabled: false`），
 * 免得定时器在测试或本地调试时删掉正在断言的记录。
 * 失败只记录日志：下一个调度点会自然重试，一次失败不影响其它维护动作。
 */
@Component
@ConditionalOnProperty(prefix = "merine.audit.retention", name = "enabled",
        havingValue = "true", matchIfMissing = true)
class AuditRetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(AuditRetentionScheduler.class);

    private final AuditRetentionService retention;

    AuditRetentionScheduler(AuditRetentionService retention) {
        this.retention = retention;
    }

    @Scheduled(cron = "${merine.audit.retention.cron:0 30 3 * * *}")
    void purgeExpired() {
        try {
            retention.purge(false);
        } catch (RuntimeException error) {
            log.error("审计保留期清理失败，等待下一个调度点重试", error);
        }
    }
}
