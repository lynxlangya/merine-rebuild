package com.merine.rebuild.system.audit.persistence;

import java.time.Instant;

/** 审计流水的持久化行；字段与表一一对应。 */
public record AuditLogRow(
        long id,
        Instant occurredAt,
        Long actorUserId,
        String actorLogin,
        String actorName,
        String actorUnitName,
        String module,
        String action,
        String result,
        String targetType,
        String targetId,
        String targetLabel,
        String summary,
        String requestId,
        String clientIp) {}
