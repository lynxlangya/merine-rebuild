package com.merine.rebuild.intelligence.dto;

import jakarta.validation.constraints.*;

public final class AssessmentRequests {
    private AssessmentRequests() { }
    public record RecordAssessment(String receiptId, @NotBlank @Size(max=4000) String analysis,
                                   @NotBlank @Size(max=24) String recommendationCode) { }
}
