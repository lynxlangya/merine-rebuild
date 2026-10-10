package com.merine.rebuild.system.audit;

import com.merine.rebuild.system.audit.dto.AuditPurgeResult;
import com.merine.rebuild.system.audit.persistence.AuditLogMapper;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 审计保留期清理：把超过保留期的流水**硬删除**（默认 30 天）。
 *
 * 三条不变量：
 * <ol>
 *   <li>只有 {@code occurred_at < now - retentionDays} 的行会被删；窗口内与正好在边界的记录保留。</li>
 *   <li>分批删除（每批 {@value #BATCH_SIZE} 条，单次最多 {@value #MAX_BATCHES} 批）：
 *       限制单条语句与单次持锁范围；整个清理仍是一个事务，与留痕同生共死，
 *       超出上限的剩余记录留给下一个调度点。</li>
 *   <li>真删到东西时，清理本身留一条 {@code audit:purge} 流水，写清条数与覆盖时间段；
 *       审计写失败则整个清理回滚（与其它写入同一条规则）。</li>
 * </ol>
 *
 * 触发方式：每日定时（{@link AuditRetentionScheduler}）与手动接口（`POST /api/system/audit-logs/purge`）
 * 走同一个方法，区别只在留痕里的触发来源。
 */
@Service
@EnableConfigurationProperties(AuditRetentionProperties.class)
public class AuditRetentionService {

    private static final Logger log = LoggerFactory.getLogger(AuditRetentionService.class);
    private static final int BATCH_SIZE = 500;
    private static final int MAX_BATCHES = 400;

    private final AuditLogMapper mapper;
    private final AuditTrail audit;
    private final AuditRetentionProperties properties;

    AuditRetentionService(AuditLogMapper mapper, AuditTrail audit, AuditRetentionProperties properties) {
        this.mapper = mapper;
        this.audit = audit;
        this.properties = properties;
    }

    /**
     * 按保留期清理过期流水。
     *
     * @param manual true 表示人工触发（留痕记当前操作者）；false 表示定时触发（系统动作，无操作者）
     */
    @Transactional
    public AuditPurgeResult purge(boolean manual) {
        int days = properties.days();
        Instant boundary = Instant.now().minus(Duration.ofDays(days));
        Map<String, Object> overview = mapper.expiredSummary(boundary);
        long candidates = number(overview.get("total"));
        if (candidates == 0) {
            return new AuditPurgeResult(days, 0, null, null);
        }
        long deleted = 0;
        for (int batch = 0; batch < MAX_BATCHES; batch += 1) {
            int removed = mapper.deleteExpired(boundary, BATCH_SIZE);
            deleted += removed;
            if (removed < BATCH_SIZE) {
                break;
            }
        }
        if (deleted == 0) {
            // 并发下别人已经清掉了：此时不写留痕，守住「真删到东西才有记录」这条规则
            log.info("审计保留期清理跳过留痕：overview 说有 {} 条过期，但一条也没删到", candidates);
            return new AuditPurgeResult(days, 0, null, null);
        }
        Instant earliest = instant(overview.get("earliest"));
        Instant latest = instant(overview.get("latest"));
        String summary = "清理 %d 天前的审计记录（%s）：删除 %d 条（覆盖 %s 至 %s）".formatted(
                days, manual ? "手动触发" : "定时触发", deleted,
                earliest == null ? "未知" : earliest.toString(),
                latest == null ? "未知" : latest.toString());
        AuditEvent event = AuditEvent.succeeded("system", "audit:purge", "AUDIT_LOG", "",
                "审计流水", summary);
        if (manual) {
            audit.recordCurrent(event);
        } else {
            audit.record(null, event, null);
        }
        log.info("审计保留期清理完成: boundary={} candidates={} deleted={}", boundary, candidates, deleted);
        return new AuditPurgeResult(days, deleted, earliest, latest);
    }

    private static long number(Object value) {
        return value instanceof Number count ? count.longValue() : 0L;
    }

    /**
     * `MIN/MAX(DATETIME)` 的返回类型随驱动映射而变：连接按 UTC 建立时常见的是
     * {@link LocalDateTime}（值本身已是 UTC），也可能是 {@link Timestamp} 或 {@link Instant}。
     * 三种都归一到 Instant；认不出来时返回 null，摘要里就写「未知」而不是编一个时间。
     */
    private static Instant instant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            return instant.truncatedTo(ChronoUnit.MICROS);
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant().truncatedTo(ChronoUnit.MICROS);
        }
        if (value instanceof java.time.LocalDateTime local) {
            return local.toInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        }
        if (value instanceof java.time.OffsetDateTime offset) {
            return offset.toInstant().truncatedTo(ChronoUnit.MICROS);
        }
        return null;
    }
}
