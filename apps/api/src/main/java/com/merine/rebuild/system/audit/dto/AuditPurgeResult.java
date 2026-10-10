package com.merine.rebuild.system.audit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * 一次保留期清理的结果。删除不可恢复，所以把「保留几天、删了多少、覆盖哪段时间」原样返回，
 * 让操作者在页面上看到实际发生了什么，而不是只看到一个成功提示。
 */
@Schema(name = "AuditPurgeResult")
public record AuditPurgeResult(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "保留期（天），只删除早于这个窗口的记录")
        int retentionDays,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "本次删除的条数；0 表示没有过期记录")
        long deleted,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true,
                description = "被删记录中最早的发生时刻，UTC；没有删除时为 null")
        Instant earliest,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true,
                description = "被删记录中最晚的发生时刻，UTC；没有删除时为 null")
        Instant latest) {}
