package com.merine.rebuild.intelligence.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

public final class AssessmentViews {
    private AssessmentViews() { }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(requiredProperties = {"id", "analysis", "recommendationCode", "unitName", "userName", "createdAt"})
    public record Assessment(String id, String receiptId, String analysis, String recommendationCode,
                             String unitName, String userName, Instant createdAt) { }

    @Schema(requiredProperties = {"id", "fromUnitName", "signedAt"})
    public record SignedReceipt(String id, String fromUnitName, Instant signedAt) { }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(requiredProperties = {"sourceMine", "canAssess", "signedReceipts"})
    public record AssessmentContext(boolean sourceMine, boolean canAssess, String reasonCode,
                                    String reason, List<SignedReceipt> signedReceipts) { }
}
