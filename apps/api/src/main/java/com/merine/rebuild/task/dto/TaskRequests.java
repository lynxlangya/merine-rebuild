package com.merine.rebuild.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** 只列调用方能编辑的字段；身份、状态、来源单位和服务端时间均由用例确定。 */
public final class TaskRequests {
    private TaskRequests() { }

    public record Create(
            @NotBlank @Size(max = 160) String title,
            @NotBlank @Size(max = 4000) String instruction,
            @NotBlank @Size(max = 1000) String expectedResult,
            @NotNull Instant dueAt,
            @NotEmpty @Size(max = 20) List<@NotBlank String> targetUnitCodes,
            @Schema(hidden = true) @Null(message = "基于结果发后续任务已移除，请使用新建任务入口")
            String sourceResultId) { }

    public record Dispatch(
            @NotBlank @Size(max = 4000) String instruction,
            @NotBlank @Size(max = 1000) String expectedResult,
            @NotNull Instant dueAt,
            @NotEmpty @Size(max = 20) List<@NotBlank String> targetUnitCodes) { }

    public record Progress(@NotBlank @Size(max = 4000) String note) { }
    public record ReturnTask(@NotBlank String reasonCode, @NotBlank @Size(max = 1000) String reason) { }
    public record Recall(@NotBlank @Size(max = 1000) String reason) { }
    public record Close(@NotBlank @Size(max = 4000) String conclusion) { }

    public record SubmitResult(
            @NotBlank String outcomeCode,
            @NotBlank @Size(max = 4000) String handlingDetail,
            @NotBlank @Size(max = 4000) String conclusion,
            @Schema(hidden = true) @Null(message = "建议后续单位选择已移除，请在结论中说明去向")
            String suggestedUnitCode) { }

    public record TransferRequest(
            @NotBlank String targetUnitCode,
            @NotBlank @Size(max = 1000) String reason,
            @NotBlank @Size(max = 4000) String workDone,
            @NotBlank @Size(max = 4000) String evidenceSummary,
            @NotBlank @Size(max = 4000) String remainingWork) { }

    public record TransferResponse(boolean accept, @Size(max = 1000) String reason,
                                   Integer requiredDurationMinutes) { }
    public record TransferDecision(boolean approve, @Size(max = 1000) String reason,
                                   Instant dueAt) { }
}
