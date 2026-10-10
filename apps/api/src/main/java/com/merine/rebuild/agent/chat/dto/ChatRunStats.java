package com.merine.rebuild.agent.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 用量聚合：当前账号在指定时间范围内（可按连接 + 模型过滤）的执行汇总。
 *
 * <p>上游没有返回用量的记录不参与 token 合计，另用 {@code runsWithoutUsage} 计数，
 * 界面据此提示「N 条没有用量」，不做估算。
 */
@Schema(name = "ChatRunStats")
public record ChatRunStats(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Totals totals,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<DailyPoint> series,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ModelSlice> models,
        /** 分桶用的本地偏移（分钟），与请求一致，便于前端补零对齐。 */
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int offsetMinutes) {

    @Schema(name = "ChatRunStatsTotals")
    public record Totals(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int runs,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int succeeded,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int failed,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int aborted,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int runsWithoutUsage,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long promptTokens,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long completionTokens,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long inputChars,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long outputChars,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long durationMs) {}

    /** 本地日期（yyyy-MM-dd）+ 当天各口径合计；缺的天由前端补零。 */
    @Schema(name = "ChatRunDailyPoint")
    public record DailyPoint(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String date,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int runs,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int failed,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long promptTokens,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long completionTokens,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long inputChars,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long outputChars,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long durationMs) {}

    @Schema(name = "ChatRunModelSlice")
    public record ModelSlice(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String providerId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String providerName,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String modelId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int runs,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int failed,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long promptTokens,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long completionTokens,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long durationMs) {}
}
