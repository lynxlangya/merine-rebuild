package com.merine.rebuild.task.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.merine.rebuild.intelligence.dto.AssessmentViews.Assessment;
import com.merine.rebuild.intelligence.dto.AssessmentViews.AssessmentContext;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

public final class TaskIntelligenceViews {
    private TaskIntelligenceViews() { }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(requiredProperties = {"assessmentContext", "assessments", "targets", "canCreate"})
    public record IntelligenceTaskContext(AssessmentContext assessmentContext, List<Assessment> assessments,
            List<TaskViews.UnitOption> targets, String sourceSupplementCheckpoint, boolean canCreate, String reason) { }

    @Schema(requiredProperties = {"id", "taskNo", "title", "status", "dueAt", "issuerUnitName"})
    public record LinkedIntelligenceTask(String id, String taskNo, String title, String status,
                                         Instant dueAt, String issuerUnitName) { }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(requiredProperties = {"linked", "sourceChanged"})
    public record TaskIntelligenceSource(boolean linked, String backgroundSummary, String topicId,
            String topicNo, String title, boolean sourceChanged, Assessment adoptedAssessment) { }

}
