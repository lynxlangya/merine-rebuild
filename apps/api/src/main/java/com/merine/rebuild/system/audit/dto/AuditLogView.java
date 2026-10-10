package com.merine.rebuild.system.audit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * 一条审计流水：只读展示用。
 * 不含业务内容（密码、密钥、情报正文、对话内容都不会出现在这里）；对象名称是当时的快照。
 */
@Schema(name = "AuditLogEntry")
public record AuditLogView(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant occurredAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String actorLogin,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String actorName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String actorUnitName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String module,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String moduleName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String action,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String result,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String targetType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String targetId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String targetLabel,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String summary,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String requestId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String clientIp) {}
