package com.merine.rebuild.intelligence.dto;

import jakarta.validation.constraints.*;
import java.util.List;

/** 调用方仅提供内容和目标；身份与流程事实由后端确定。 */
public final class IntelligenceRequests {
    private IntelligenceRequests() { }
    public record IntelligenceDraftRequest(
            @NotBlank @Size(max=160) String title,
            @NotBlank @Size(max=4000) String body,
            @NotEmpty @Size(max=2000) List<@NotBlank String> scopeUnitCodes,
            @NotEmpty @Size(max=100) List<@NotBlank String> targetUnitCodes,
            @Size(max=1000) String note, @NotNull Integer version) { }
    public record IntelligenceSendRequest(
            @NotEmpty @Size(max=100) List<@NotBlank String> targetUnitCodes,
            @Size(max=1000) String note, String assessmentId, @Size(max=4000) String assessmentSummary) {
        public IntelligenceSendRequest(List<String> targets,String note) { this(targets,note,null,null); }
    }
    public record IntelligenceFeedbackRequest(@NotBlank @Size(max=4000) String body) { }
    public record IntelligenceSupplementRequest(@NotBlank String kind,
                                                @NotBlank @Size(max=4000) String body) { }
}
