package com.merine.rebuild.task.dto;

import com.merine.rebuild.intelligence.dto.AssessmentRequests.RecordAssessment;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class TaskIntelligenceRequests {
    private TaskIntelligenceRequests() { }
    public record CreateIntelligenceTask(@NotBlank @Size(max=160) String title,
        @NotBlank @Size(max=4000) String instruction,@NotBlank @Size(max=1000) String expectedResult,
        @NotNull Instant dueAt,@NotEmpty @Size(max=20) List<@NotBlank String> targetUnitCodes,
        @NotBlank @Size(max=4000) String backgroundSummary,String assessmentId,
        @Valid RecordAssessment newAssessment,String sourceSupplementCheckpoint) { }
}
