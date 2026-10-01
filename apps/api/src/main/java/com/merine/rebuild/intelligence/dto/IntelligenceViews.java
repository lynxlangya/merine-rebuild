package com.merine.rebuild.intelligence.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

public final class IntelligenceViews {
    private IntelligenceViews() { }
    public record IntelligenceAction(
            @Schema(requiredMode=Schema.RequiredMode.REQUIRED) String code,
            @Schema(requiredMode=Schema.RequiredMode.REQUIRED) boolean enabled,
            @Schema(requiredMode=Schema.RequiredMode.REQUIRED, types={"string","null"}) String reasonCode,
            @Schema(requiredMode=Schema.RequiredMode.REQUIRED, types={"string","null"}) String reason) { }
    public record IntelligenceUnitOption(
            @Schema(requiredMode=Schema.RequiredMode.REQUIRED) String code,
            @Schema(requiredMode=Schema.RequiredMode.REQUIRED) String name,
            @Schema(requiredMode=Schema.RequiredMode.REQUIRED, types={"string","null"}) String parentCode,
            @Schema(requiredMode=Schema.RequiredMode.REQUIRED) int level,
            @Schema(requiredMode=Schema.RequiredMode.REQUIRED) boolean enabled,
            @Schema(requiredMode=Schema.RequiredMode.REQUIRED) boolean targetEligible) { }
    public record IntelligenceListItem(
            @Schema(requiredMode=Schema.RequiredMode.REQUIRED) String id,
            @Schema(requiredMode=Schema.RequiredMode.REQUIRED) String topicNo,
            @Schema(requiredMode=Schema.RequiredMode.REQUIRED) String title,
            @Schema(requiredMode=Schema.RequiredMode.REQUIRED) String status,
            @Schema(requiredMode=Schema.RequiredMode.REQUIRED) String sourceUnitName,
            @Schema(requiredMode=Schema.RequiredMode.REQUIRED) Instant createdAt,
            @Schema(requiredMode=Schema.RequiredMode.REQUIRED, types={"string","null"}) Instant publishedAt,
            @Schema(requiredMode=Schema.RequiredMode.REQUIRED) long pendingReceiptCount,
            @Schema(requiredMode=Schema.RequiredMode.REQUIRED) long myReceiptCount) { }
    public record IntelligenceFeedback(
            @Schema(requiredMode=Schema.RequiredMode.REQUIRED) String id,
            @Schema(requiredMode=Schema.RequiredMode.REQUIRED) String body,
            @Schema(requiredMode=Schema.RequiredMode.REQUIRED) String unitName,
            @Schema(requiredMode=Schema.RequiredMode.REQUIRED) String userName,
            @Schema(requiredMode=Schema.RequiredMode.REQUIRED) Instant createdAt) { }
    public record IntelligenceReceipt(
            @Schema(requiredMode=Schema.RequiredMode.REQUIRED) String id,
            @Schema(requiredMode=Schema.RequiredMode.REQUIRED) String sendId,
            @Schema(requiredMode=Schema.RequiredMode.REQUIRED, types={"string","null"}) String parentReceiptId,
            @Schema(requiredMode=Schema.RequiredMode.REQUIRED) String fromUnitName,
            @Schema(requiredMode=Schema.RequiredMode.REQUIRED) String toUnitName,
            @Schema(requiredMode=Schema.RequiredMode.REQUIRED) String senderName,
            @Schema(requiredMode=Schema.RequiredMode.REQUIRED) String note,
            @Schema(types={"string","null"}) String assessmentSummary,
            @Schema(requiredMode=Schema.RequiredMode.REQUIRED) Instant sentAt,
            @Schema(requiredMode=Schema.RequiredMode.REQUIRED) boolean mine,
            @Schema(requiredMode=Schema.RequiredMode.REQUIRED, types={"string","null"}) String signedByName,
            @Schema(requiredMode=Schema.RequiredMode.REQUIRED, types={"string","null"}) Instant signedAt,
            @Schema(requiredMode=Schema.RequiredMode.REQUIRED) List<IntelligenceFeedback> feedbacks,
            @Schema(requiredMode=Schema.RequiredMode.REQUIRED) List<IntelligenceAction> allowedActions) { }
    public record IntelligenceSupplement(
            @Schema(requiredMode=Schema.RequiredMode.REQUIRED) String id,
            @Schema(requiredMode=Schema.RequiredMode.REQUIRED) String kind,
            @Schema(requiredMode=Schema.RequiredMode.REQUIRED) String body,
            @Schema(requiredMode=Schema.RequiredMode.REQUIRED) String unitName,
            @Schema(requiredMode=Schema.RequiredMode.REQUIRED) String userName,
            @Schema(requiredMode=Schema.RequiredMode.REQUIRED) Instant createdAt) { }
    public record IntelligenceDetail(
            @Schema(requiredMode=Schema.RequiredMode.REQUIRED) String id,
            @Schema(requiredMode=Schema.RequiredMode.REQUIRED) String topicNo,
            @Schema(requiredMode=Schema.RequiredMode.REQUIRED) String title,
            @Schema(requiredMode=Schema.RequiredMode.REQUIRED) String body,
            @Schema(requiredMode=Schema.RequiredMode.REQUIRED) String status,
            @Schema(requiredMode=Schema.RequiredMode.REQUIRED) String sourceUnitName,
            @Schema(requiredMode=Schema.RequiredMode.REQUIRED) String sourceUserName,
            @Schema(requiredMode=Schema.RequiredMode.REQUIRED) boolean sourceMine,
            @Schema(requiredMode=Schema.RequiredMode.REQUIRED) int version,
            @Schema(requiredMode=Schema.RequiredMode.REQUIRED) Instant createdAt,
            @Schema(requiredMode=Schema.RequiredMode.REQUIRED, types={"string","null"}) Instant publishedAt,
            @Schema(requiredMode=Schema.RequiredMode.REQUIRED) List<String> scopeUnitCodes,
            @Schema(requiredMode=Schema.RequiredMode.REQUIRED) List<String> draftTargetUnitCodes,
            @Schema(requiredMode=Schema.RequiredMode.REQUIRED, types={"string","null"}) String draftNote,
            @Schema(requiredMode=Schema.RequiredMode.REQUIRED) List<IntelligenceReceipt> receipts,
            @Schema(requiredMode=Schema.RequiredMode.REQUIRED) List<IntelligenceSupplement> supplements,
            @Schema(requiredMode=Schema.RequiredMode.REQUIRED) List<IntelligenceAction> allowedActions) { }
}
